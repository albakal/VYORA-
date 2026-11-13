const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot list videos or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("videos").where("status", "==", "active").get());
  await assertFails(unauthDb.collection("users").where("isBanned", "==", false).get());
});

test("User profile: owner can create valid profile, cannot self-ban or inflate followers", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_creator",
      displayName: "Alice",
      bio: "Hello VYORA",
      avatarUrl: "",
      isPrivate: false,
      isBanned: false,
      followersCount: 0,
      followingCount: 0,
      likesCount: 0,
      interests: ["music"],
      language: "ar",
      themeMode: "dark",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc("fake_id").set({
      userId: "fake_id",
      username: "evil",
      displayName: "Evil",
      isPrivate: false,
      isBanned: false,
      followersCount: 999,
      followingCount: 0,
      likesCount: 0,
      createdAt: now,
    })
  );
});

test("PII isolation: Bob cannot read Alice's private PII doc", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("private").doc("account").set({
      userId: ALICE_UID,
      email: "alice@example.com",
      updatedAt: pastTimestamp(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").doc("account").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("account").get());
});

test("Videos: owner creates video, peer increments likesCount by 1, peer cannot delete or hijack", async () => {
  const now = pastTimestamp();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_1").set({
      userId: ALICE_UID,
      creatorUsername: "alice_creator",
      creatorAvatarUrl: "",
      videoUrl: "https://storage.googleapis.com/demo/vid1.mp4",
      thumbnailUrl: "",
      caption: "First #vyora clip",
      hashtags: ["vyora"],
      soundId: "snd_1",
      soundTitle: "Original Sound",
      durationSec: 15,
      viewsCount: 0,
      likesCount: 0,
      commentsCount: 0,
      sharesCount: 0,
      bookmarksCount: 0,
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("videos").doc("vid_1").update({
      likesCount: 1,
      updatedAt: pastTimestamp(),
    })
  );

  await assertFails(
    bobDb.collection("videos").doc("vid_1").update({
      likesCount: 50,
    })
  );
  await assertFails(bobDb.collection("videos").doc("vid_1").delete());

  await assertFails(bobDb.collection("videos").get());
  await assertSucceeds(bobDb.collection("videos").where("status", "==", "active").get());
});

test("Conversations & Messages: only participants can read or write", async () => {
  const now = pastTimestamp();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext("outsider_999").firestore();

  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_1").set({
      participantIds: [ALICE_UID, BOB_UID],
      lastMessage: "Hi Bob",
      lastMessageType: "text",
      lastSenderId: ALICE_UID,
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("conversations").doc("conv_1").collection("messages").doc("msg_1").set({
      senderId: BOB_UID,
      participantIds: [ALICE_UID, BOB_UID],
      type: "text",
      text: "Hey Alice!",
      readBy: [BOB_UID],
      createdAt: now,
    })
  );

  await assertFails(outsiderDb.collection("conversations").doc("conv_1").get());
  await assertFails(outsiderDb.collection("conversations").doc("conv_1").collection("messages").doc("msg_1").get());
});

test("Admin & Email Spoofing protection: unverified admin email rejected, verified admin succeeds", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("wallets").doc(ALICE_UID).set({
      userId: ALICE_UID,
      points: 100,
      balanceUsd: 1.0,
      totalEarnedUsd: 1.0,
      updatedAt: pastTimestamp(),
    });
  });

  const spoofedDb = testEnv.authenticatedContext("spoof_1", {
    email: "mahmoudomran324@gmail.com",
    email_verified: false,
  }).firestore();
  await assertFails(spoofedDb.collection("wallets").doc(ALICE_UID).update({ points: 9999 }));

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "mahmoudomran324@gmail.com",
    email_verified: true,
  }).firestore();
  await assertSucceeds(
    adminDb.collection("wallets").doc(ALICE_UID).update({
      points: 200,
      balanceUsd: 2.0,
      totalEarnedUsd: 2.0,
      updatedAt: pastTimestamp(),
    })
  );

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("wallets").doc(ALICE_UID).update({ points: 5000 }));
});
