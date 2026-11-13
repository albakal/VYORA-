import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { CallableRequest, HttpsError, onCall } from "firebase-functions/v2/https";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { defineSecret } from "firebase-functions/params";
import { createClient, SupabaseClient } from "@supabase/supabase-js";
import { RtcRole, RtcTokenBuilder } from "agora-token";

initializeApp();

const DATABASE_ID = "ai-studio-android-vyora-09b7e2be-39f1-49bc-910d-92eb9b4daa3f";
const db = getFirestore(DATABASE_ID);

// Stored securely in Google Cloud Secret Manager (never bundled into APK)
const agoraAppIdSecret = defineSecret("AGORA_APP_ID");
const agoraAppCertSecret = defineSecret("AGORA_APP_CERTIFICATE");
const supabaseUrlSecret = defineSecret("SUPABASE_URL");
const supabaseSecretKey = defineSecret("SUPABASE_SECRET_KEY");

function getSupabaseAdminClient(): SupabaseClient | null {
  let url = "";
  let key = "";
  try {
    url = supabaseUrlSecret.value().trim();
    key = supabaseSecretKey.value().trim();
  } catch {
    return null;
  }

  if (
    !url ||
    !key ||
    url.includes("YOUR_PROJECT_REF") ||
    key.includes("YOUR_SUPABASE") ||
    !url.startsWith("https://")
  ) {
    return null;
  }

  return createClient(url, key, {
    auth: {
      persistSession: false,
      autoRefreshToken: false,
    },
  });
}

async function verifyAdminCaller(auth: NonNullable<CallableRequest["auth"]>): Promise<void> {
  const email = typeof auth.token.email === "string" ? auth.token.email.toLowerCase() : "";
  const isVerifiedPrimary =
    auth.token.email_verified === true && email === "mahmoudomran324@gmail.com";
  if (isVerifiedPrimary) return;

  const adminSnap = await db.collection("admins").doc(auth.uid).get();
  if (!adminSnap.exists) {
    throw new HttpsError("permission-denied", "Admin privileges required.");
  }
}

async function ensureSupabaseUserAndWallet(
  supabase: SupabaseClient,
  uid: string
): Promise<boolean> {
  if (!uid) return false;
  const [userSnap, privateSnap, walletSnap] = await Promise.all([
    db.collection("users").doc(uid).get(),
    db.collection("users").doc(uid).collection("private").doc("account").get(),
    db.collection("wallets").doc(uid).get(),
  ]);

  const user = userSnap.data() ?? {};
  const priv = privateSnap.data() ?? {};
  const wallet = walletSnap.data() ?? {};

  const { error: userErr } = await supabase.from("vyora_users_sync").upsert(
    {
      firebase_uid: uid,
      username: String(user.username ?? `creator_${uid.slice(0, 6)}`),
      display_name: String(user.displayName ?? "VYORA Creator"),
      email: String(priv.email ?? ""),
      is_banned: user.isBanned === true,
      updated_at: new Date().toISOString(),
    },
    { onConflict: "firebase_uid" }
  );

  if (userErr) {
    console.warn("Supabase user upsert warning:", userErr.message);
    return false;
  }

  const { error: walletErr } = await supabase.from("vyora_wallets").upsert(
    {
      user_id: uid,
      points: Number(wallet.points ?? 0),
      balance_usd: Number(wallet.balanceUsd ?? 0),
      total_earned_usd: Number(wallet.totalEarnedUsd ?? 0),
      updated_at: new Date().toISOString(),
    },
    { onConflict: "user_id", ignoreDuplicates: true }
  );

  if (walletErr) {
    console.warn("Supabase wallet init warning:", walletErr.message);
    return false;
  }
  return true;
}

async function syncVideoRecordToSupabase(
  supabase: SupabaseClient,
  videoId: string
): Promise<boolean> {
  const videoSnap = await db.collection("videos").doc(videoId).get();
  if (!videoSnap.exists) return false;
  const video = videoSnap.data()!;
  const creatorUid = String(video.userId ?? "");
  if (!creatorUid) return false;

  await ensureSupabaseUserAndWallet(supabase, creatorUid);

  const { error } = await supabase.from("vyora_videos_ledger").upsert(
    {
      video_id: videoId,
      creator_uid: creatorUid,
      creator_username: String(video.creatorUsername ?? "creator"),
      caption: String(video.caption ?? ""),
      video_url: String(video.videoUrl ?? ""),
      thumbnail_url: String(video.thumbnailUrl ?? ""),
      sound_title: String(video.soundTitle ?? "Original Sound"),
      duration_sec: Math.min(300, Math.max(1, Number(video.durationSec ?? 15))),
      views_count: Math.max(0, Number(video.viewsCount ?? 0)),
      likes_count: Math.max(0, Number(video.likesCount ?? 0)),
      status: video.status === "removed" ? "removed" : "active",
      updated_at: new Date().toISOString(),
    },
    { onConflict: "video_id" }
  );

  if (error) {
    console.warn("Supabase video upsert warning:", error.message);
    return false;
  }
  return true;
}

/**
 * Generates a short-lived Agora RTC token for an authenticated user joining or hosting a live stream.
 */
export const getAgoraLiveToken = onCall(
  {
    region: "us-central1",
    secrets: [agoraAppIdSecret, agoraAppCertSecret],
  },
  async (request) => {
    const auth = request.auth;
    if (!auth) {
      throw new HttpsError("unauthenticated", "Authentication required to join live streams.");
    }

    const streamId = String(request.data?.streamId ?? "").trim();
    if (!streamId || streamId.length > 128) {
      throw new HttpsError("invalid-argument", "Valid streamId is required.");
    }

    const appId = agoraAppIdSecret.value().trim();
    const appCertificate = agoraAppCertSecret.value().trim();
    if (!appId || !appCertificate) {
      throw new HttpsError(
        "failed-precondition",
        "Agora server secrets are not configured in Firebase Secret Manager."
      );
    }

    const userSnap = await db.collection("users").doc(auth.uid).get();
    if (userSnap.exists && userSnap.data()?.isBanned === true) {
      throw new HttpsError("permission-denied", "Banned accounts cannot access live streams.");
    }

    const streamSnap = await db.collection("liveStreams").doc(streamId).get();
    if (!streamSnap.exists) {
      throw new HttpsError("not-found", "Live stream does not exist.");
    }
    const stream = streamSnap.data()!;
    if (stream.isActive !== true) {
      throw new HttpsError("failed-precondition", "This live stream has already ended.");
    }

    const isHost = stream.hostId === auth.uid;
    const role = isHost ? RtcRole.PUBLISHER : RtcRole.SUBSCRIBER;
    const channelName = String(stream.channelName ?? streamId);
    const tokenExpirationInSeconds = 7200;
    const privilegeExpirationInSeconds = 7200;

    const token = RtcTokenBuilder.buildTokenWithUid(
      appId,
      appCertificate,
      channelName,
      0,
      role,
      tokenExpirationInSeconds,
      privilegeExpirationInSeconds
    );

    return {
      appId,
      token,
      channelName,
      rtcUid: 0,
      role: isHost ? "publisher" : "subscriber",
      expiresAt: Date.now() + tokenExpirationInSeconds * 1000,
    };
  }
);

/**
 * Syncs authenticated user profile/wallet and optionally an owned video record into Supabase.
 */
export const syncUserAndVideoToSupabase = onCall(
  {
    region: "us-central1",
    secrets: [supabaseUrlSecret, supabaseSecretKey],
  },
  async (request) => {
    const auth = request.auth;
    if (!auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }

    const supabase = getSupabaseAdminClient();
    if (!supabase) {
      return {
        supabaseConfigured: false,
        synced: false,
      };
    }

    const userSynced = await ensureSupabaseUserAndWallet(supabase, auth.uid);
    const videoId = String(request.data?.videoId ?? "").trim();
    let videoSynced = true;
    if (videoId) {
      const videoSnap = await db.collection("videos").doc(videoId).get();
      if (videoSnap.exists && videoSnap.data()?.userId === auth.uid) {
        videoSynced = await syncVideoRecordToSupabase(supabase, videoId);
      }
    }

    return {
      supabaseConfigured: true,
      synced: userSynced && videoSynced,
    };
  }
);

/**
 * Records a deduplicated video view on Firestore and mirrors creator earnings into Supabase.
 */
export const recordVideoViewReward = onCall(
  {
    region: "us-central1",
    secrets: [supabaseUrlSecret, supabaseSecretKey],
  },
  async (request) => {
    const auth = request.auth;
    if (!auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }

    const videoId = String(request.data?.videoId ?? "").trim();
    if (!videoId || videoId.length > 128) {
      throw new HttpsError("invalid-argument", "Valid videoId is required.");
    }

    const viewerUid = auth.uid;
    const videoRef = db.collection("videos").doc(videoId);
    const viewRef = videoRef.collection("views").doc(viewerUid);
    const settingsRef = db.collection("adminSettings").doc("global");

    let creatorUid = "";
    let pointsEarned = 0;
    let usdEarned = 0;
    let shouldReward = false;

    const counted = await db.runTransaction(async (tx) => {
      const [videoSnap, existingViewSnap, settingsSnap] = await Promise.all([
        tx.get(videoRef),
        tx.get(viewRef),
        tx.get(settingsRef),
      ]);

      if (!videoSnap.exists) {
        throw new HttpsError("not-found", "Video not found.");
      }
      if (existingViewSnap.exists) {
        return false;
      }

      const video = videoSnap.data()!;
      if (video.status !== "active") {
        return false;
      }

      tx.set(viewRef, {
        userId: viewerUid,
        targetId: videoId,
        createdAt: FieldValue.serverTimestamp(),
      });
      tx.update(videoRef, {
        viewsCount: FieldValue.increment(1),
        updatedAt: FieldValue.serverTimestamp(),
      });

      const settings = settingsSnap.exists ? settingsSnap.data()! : {};
      const rewardsEnabled = settings.rewardsEnabled !== false;
      const pointValueUsd = Number(settings.pointValueUsd ?? 0.01);
      creatorUid = String(video.userId ?? "");

      if (rewardsEnabled && creatorUid && creatorUid !== viewerUid && pointValueUsd > 0) {
        shouldReward = true;
        pointsEarned = 1;
        usdEarned = Number((pointsEarned * pointValueUsd).toFixed(4));

        const walletRef = db.collection("wallets").doc(creatorUid);
        const walletSnap = await tx.get(walletRef);

        if (walletSnap.exists) {
          tx.update(walletRef, {
            points: FieldValue.increment(pointsEarned),
            balanceUsd: FieldValue.increment(usdEarned),
            totalEarnedUsd: FieldValue.increment(usdEarned),
            updatedAt: FieldValue.serverTimestamp(),
          });
        } else {
          tx.set(walletRef, {
            userId: creatorUid,
            points: pointsEarned,
            balanceUsd: usdEarned,
            totalEarnedUsd: usdEarned,
            updatedAt: FieldValue.serverTimestamp(),
          });
        }

        const txRef = db.collection("transactions").doc();
        tx.set(txRef, {
          userId: creatorUid,
          type: "view_reward",
          pointsDelta: pointsEarned,
          amountDelta: usdEarned,
          description: `View reward for video ${videoId.slice(0, 8)}`,
          status: "completed",
          createdAt: FieldValue.serverTimestamp(),
        });
      }

      return true;
    });

    let supabaseSynced = false;
    if (counted) {
      const supabase = getSupabaseAdminClient();
      if (supabase) {
        await syncVideoRecordToSupabase(supabase, videoId);
        const { error: rpcErr } = await supabase.rpc("record_vyora_view_reward", {
          p_video_id: videoId,
          p_viewer_uid: viewerUid,
          p_creator_uid: creatorUid,
          p_points_earned: pointsEarned,
          p_usd_earned: usdEarned,
          p_should_reward: shouldReward,
        });
        if (rpcErr) {
          console.warn("Supabase record_vyora_view_reward warning:", rpcErr.message);
        } else {
          supabaseSynced = true;
        }
      }
    }

    return { counted, supabaseSynced };
  }
);

/**
 * Validates wallet balance & global minimum withdrawal limit, creates the withdrawal request
 * in Firestore, and mirrors it into Supabase PostgreSQL accounting tables.
 */
export const requestCreatorWithdrawal = onCall(
  {
    region: "us-central1",
    secrets: [supabaseUrlSecret, supabaseSecretKey],
  },
  async (request) => {
    const auth = request.auth;
    if (!auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }

    const amountUsd = Number(request.data?.amountUsd ?? 0);
    const method = String(request.data?.method ?? "").trim().slice(0, 60);
    const accountDetails = String(request.data?.accountDetails ?? "").trim().slice(0, 200);

    if (!Number.isFinite(amountUsd) || amountUsd <= 0 || method.length < 2 || accountDetails.length < 3) {
      throw new HttpsError("invalid-argument", "Invalid withdrawal request details.");
    }

    const uid = auth.uid;
    const walletRef = db.collection("wallets").doc(uid);
    const settingsRef = db.collection("adminSettings").doc("global");
    const reqRef = db.collection("withdrawalRequests").doc();
    let pointsSpent = 1;

    await db.runTransaction(async (tx) => {
      const [walletSnap, settingsSnap] = await Promise.all([
        tx.get(walletRef),
        tx.get(settingsRef),
      ]);

      const settings = settingsSnap.exists ? settingsSnap.data()! : {};
      const minWithdrawalUsd = Number(settings.minWithdrawalUsd ?? 10.0);
      const pointValueUsd = Math.max(0.0001, Number(settings.pointValueUsd ?? 0.01));

      if (amountUsd < minWithdrawalUsd) {
        throw new HttpsError(
          "failed-precondition",
          `Minimum withdrawal amount is $${minWithdrawalUsd.toFixed(2)}.`
        );
      }

      const balanceUsd = Number(walletSnap.data()?.balanceUsd ?? 0);
      if (balanceUsd < amountUsd) {
        throw new HttpsError("failed-precondition", "Insufficient wallet balance.");
      }

      pointsSpent = Math.max(1, Math.round(amountUsd / pointValueUsd));
      tx.set(reqRef, {
        userId: uid,
        amountUsd,
        pointsSpent,
        method,
        accountDetails,
        status: "pending",
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });

    let supabaseSynced = false;
    const supabase = getSupabaseAdminClient();
    if (supabase) {
      await ensureSupabaseUserAndWallet(supabase, uid);
      const { error } = await supabase.from("vyora_withdrawal_requests").upsert(
        {
          id: reqRef.id,
          user_id: uid,
          amount_usd: amountUsd,
          points_spent: pointsSpent,
          method,
          account_details: accountDetails,
          status: "pending",
          updated_at: new Date().toISOString(),
        },
        { onConflict: "id" }
      );
      if (error) {
        console.warn("Supabase withdrawal mirror warning:", error.message);
      } else {
        supabaseSynced = true;
      }
    }

    return { requestId: reqRef.id, supabaseSynced };
  }
);

/**
 * Admin-only server function to grant/adjust creator points across both Firestore and Supabase.
 */
export const adminGrantCreatorPoints = onCall(
  {
    region: "us-central1",
    secrets: [supabaseUrlSecret, supabaseSecretKey],
  },
  async (request) => {
    const auth = request.auth;
    if (!auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }
    await verifyAdminCaller(auth);

    const targetUid = String(request.data?.targetUid ?? "").trim();
    const pointsDelta = Math.trunc(Number(request.data?.pointsDelta ?? 0));
    const reason = String(request.data?.reason ?? "Admin adjustment").trim().slice(0, 200);

    if (!targetUid || pointsDelta === 0) {
      throw new HttpsError("invalid-argument", "targetUid and non-zero pointsDelta required.");
    }

    const settingsSnap = await db.collection("adminSettings").doc("global").get();
    const pointValueUsd = Math.max(0.0001, Number(settingsSnap.data()?.pointValueUsd ?? 0.01));
    const amountDelta = Number((pointsDelta * pointValueUsd).toFixed(4));

    const walletRef = db.collection("wallets").doc(targetUid);
    const txRef = db.collection("transactions").doc();

    await db.runTransaction(async (tx) => {
      const snap = await tx.get(walletRef);
      const curPts = Number(snap.data()?.points ?? 0);
      const curBal = Number(snap.data()?.balanceUsd ?? 0);
      const curTot = Number(snap.data()?.totalEarnedUsd ?? 0);

      if (snap.exists) {
        tx.update(walletRef, {
          points: Math.max(0, curPts + pointsDelta),
          balanceUsd: Math.max(0, Number((curBal + amountDelta).toFixed(4))),
          totalEarnedUsd: Number((curTot + Math.max(0, amountDelta)).toFixed(4)),
          updatedAt: FieldValue.serverTimestamp(),
        });
      } else {
        tx.set(walletRef, {
          userId: targetUid,
          points: Math.max(0, pointsDelta),
          balanceUsd: Math.max(0, amountDelta),
          totalEarnedUsd: Math.max(0, amountDelta),
          updatedAt: FieldValue.serverTimestamp(),
        });
      }

      tx.set(txRef, {
        userId: targetUid,
        type: "adjustment",
        pointsDelta,
        amountDelta,
        description: reason || "Admin adjustment",
        status: "completed",
        createdAt: FieldValue.serverTimestamp(),
      });
    });

    let supabaseSynced = false;
    const supabase = getSupabaseAdminClient();
    if (supabase) {
      await ensureSupabaseUserAndWallet(supabase, targetUid);
      const { error } = await supabase.rpc("grant_vyora_points_ledger", {
        p_target_uid: targetUid,
        p_points_delta: pointsDelta,
        p_amount_delta: amountDelta,
        p_reason: reason || "Admin adjustment",
        p_external_ref: `adj:${txRef.id}`,
      });
      if (error) {
        console.warn("Supabase grant_vyora_points_ledger warning:", error.message);
      } else {
        supabaseSynced = true;
      }
    }

    return { ok: true, supabaseSynced };
  }
);

/**
 * Admin-only server function to approve or reject a withdrawal across Firestore and Supabase.
 */
export const adminReviewWithdrawal = onCall(
  {
    region: "us-central1",
    secrets: [supabaseUrlSecret, supabaseSecretKey],
  },
  async (request) => {
    const auth = request.auth;
    if (!auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }
    await verifyAdminCaller(auth);

    const requestId = String(request.data?.requestId ?? "").trim();
    const approve = Boolean(request.data?.approve);
    if (!requestId) {
      throw new HttpsError("invalid-argument", "requestId is required.");
    }

    const reqRef = db.collection("withdrawalRequests").doc(requestId);
    let userId = "";
    let amountUsd = 0;
    let pointsSpent = 0;
    let method = "";
    let accountDetails = "";

    await db.runTransaction(async (tx) => {
      const reqSnap = await tx.get(reqRef);
      if (!reqSnap.exists) {
        throw new HttpsError("not-found", "Withdrawal request not found.");
      }
      const reqData = reqSnap.data()!;
      if (reqData.status !== "pending") {
        throw new HttpsError("failed-precondition", "Withdrawal request already reviewed.");
      }

      userId = String(reqData.userId ?? "");
      amountUsd = Number(reqData.amountUsd ?? 0);
      pointsSpent = Number(reqData.pointsSpent ?? 0);
      method = String(reqData.method ?? "");
      accountDetails = String(reqData.accountDetails ?? "");

      tx.update(reqRef, {
        status: approve ? "approved" : "rejected",
        reviewedBy: auth.uid,
        updatedAt: FieldValue.serverTimestamp(),
      });

      if (approve && userId) {
        const walletRef = db.collection("wallets").doc(userId);
        const walletSnap = await tx.get(walletRef);
        const curPts = Number(walletSnap.data()?.points ?? 0);
        const curBal = Number(walletSnap.data()?.balanceUsd ?? 0);

        tx.update(walletRef, {
          points: Math.max(0, curPts - pointsSpent),
          balanceUsd: Math.max(0, Number((curBal - amountUsd).toFixed(4))),
          updatedAt: FieldValue.serverTimestamp(),
        });

        const txRef = db.collection("transactions").doc();
        tx.set(txRef, {
          userId,
          type: "withdrawal",
          pointsDelta: -pointsSpent,
          amountDelta: -amountUsd,
          description: `Withdrawal via ${method}`,
          status: "completed",
          createdAt: FieldValue.serverTimestamp(),
        });
      }
    });

    let supabaseSynced = false;
    const supabase = getSupabaseAdminClient();
    if (supabase && userId) {
      await ensureSupabaseUserAndWallet(supabase, userId);
      await supabase.from("vyora_withdrawal_requests").upsert(
        {
          id: requestId,
          user_id: userId,
          amount_usd: amountUsd,
          points_spent: pointsSpent,
          method,
          account_details: accountDetails,
          status: "pending",
        },
        { onConflict: "id", ignoreDuplicates: true }
      );
      const { error } = await supabase.rpc("review_vyora_withdrawal", {
        p_request_id: requestId,
        p_reviewer_uid: auth.uid,
        p_approve: approve,
      });
      if (error) {
        console.warn("Supabase review_vyora_withdrawal warning:", error.message);
      } else {
        supabaseSynced = true;
      }
    }

    return { ok: true, supabaseSynced };
  }
);

/**
 * Returns authoritative Supabase accounting wallet summary and synced video ledger counts
 * for the authenticated user.
 */
export const getWalletAccountingSummary = onCall(
  {
    region: "us-central1",
    secrets: [supabaseUrlSecret, supabaseSecretKey],
  },
  async (request) => {
    const auth = request.auth;
    if (!auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }

    const supabase = getSupabaseAdminClient();
    if (!supabase) {
      return {
        supabaseConfigured: false,
        synced: false,
        points: 0,
        balanceUsd: 0,
        totalEarnedUsd: 0,
        transactionsCount: 0,
        videosCount: 0,
      };
    }

    const ensured = await ensureSupabaseUserAndWallet(supabase, auth.uid);
    const [walletRes, txRes, videoRes] = await Promise.all([
      supabase
        .from("vyora_wallets")
        .select("points, balance_usd, total_earned_usd, updated_at")
        .eq("user_id", auth.uid)
        .maybeSingle(),
      supabase
        .from("vyora_wallet_transactions")
        .select("id", { count: "exact", head: true })
        .eq("user_id", auth.uid),
      supabase
        .from("vyora_videos_ledger")
        .select("video_id", { count: "exact", head: true })
        .eq("creator_uid", auth.uid)
        .eq("status", "active"),
    ]);

    return {
      supabaseConfigured: true,
      synced: ensured && !walletRes.error,
      points: Number(walletRes.data?.points ?? 0),
      balanceUsd: Number(walletRes.data?.balance_usd ?? 0),
      totalEarnedUsd: Number(walletRes.data?.total_earned_usd ?? 0),
      transactionsCount: Number(txRes.count ?? 0),
      videosCount: Number(videoRes.count ?? 0),
    };
  }
);

/**
 * Automatically dispatches an FCM push notification whenever a new notification document
 * is created in the named Firestore database, using the isolated private fcmToken.
 */
export const sendActivityPushNotification = onDocumentCreated(
  {
    region: "us-central1",
    database: DATABASE_ID,
    document: "notifications/{notifId}",
  },
  async (event) => {
    const data = event.data?.data();
    if (!data) return;

    const recipientId = String(data.recipientId ?? "");
    if (!recipientId) return;

    const privateSnap = await db
      .collection("users")
      .doc(recipientId)
      .collection("private")
      .doc("account")
      .get();

    const fcmToken = privateSnap.data()?.fcmToken;
    if (!fcmToken || typeof fcmToken !== "string") {
      return;
    }

    const actorUsername = String(data.actorUsername ?? "VYORA");
    const message = String(data.message ?? "New activity on VYORA");

    try {
      await getMessaging().send({
        token: fcmToken,
        notification: {
          title: `@${actorUsername}`,
          body: message,
        },
        data: {
          type: String(data.type ?? "activity"),
          targetId: String(data.targetId ?? ""),
        },
      });
    } catch (err) {
      console.warn("FCM dispatch skipped/failed:", err);
    }
  }
);
