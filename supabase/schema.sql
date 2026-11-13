-- ============================================================================
-- VYORA Accounting, Creator Wallet & Video Ledger Schema for Supabase
-- Run this script once in the Supabase SQL Editor (Dashboard -> SQL Editor).
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Synced Creator Profiles (keyed by Firebase Auth UID)
CREATE TABLE IF NOT EXISTS public.vyora_users_sync (
    firebase_uid TEXT PRIMARY KEY,
    username TEXT NOT NULL,
    display_name TEXT NOT NULL DEFAULT '',
    email TEXT NOT NULL DEFAULT '',
    is_banned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Synced Short Videos Catalog & Engagement Ledger
CREATE TABLE IF NOT EXISTS public.vyora_videos_ledger (
    video_id TEXT PRIMARY KEY,
    creator_uid TEXT NOT NULL REFERENCES public.vyora_users_sync(firebase_uid) ON DELETE CASCADE,
    creator_username TEXT NOT NULL,
    caption TEXT NOT NULL DEFAULT '',
    video_url TEXT NOT NULL,
    thumbnail_url TEXT NOT NULL DEFAULT '',
    sound_title TEXT NOT NULL DEFAULT '',
    duration_sec INTEGER NOT NULL DEFAULT 15 CHECK (duration_sec BETWEEN 1 AND 300),
    views_count BIGINT NOT NULL DEFAULT 0 CHECK (views_count >= 0),
    likes_count BIGINT NOT NULL DEFAULT 0 CHECK (likes_count >= 0),
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'removed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vyora_videos_creator ON public.vyora_videos_ledger(creator_uid);
CREATE INDEX IF NOT EXISTS idx_vyora_videos_status_created ON public.vyora_videos_ledger(status, created_at DESC);

-- 3. Authoritative Creator Wallets
CREATE TABLE IF NOT EXISTS public.vyora_wallets (
    user_id TEXT PRIMARY KEY REFERENCES public.vyora_users_sync(firebase_uid) ON DELETE CASCADE,
    points BIGINT NOT NULL DEFAULT 0 CHECK (points >= 0),
    balance_usd NUMERIC(12, 4) NOT NULL DEFAULT 0 CHECK (balance_usd >= 0),
    total_earned_usd NUMERIC(12, 4) NOT NULL DEFAULT 0 CHECK (total_earned_usd >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Immutable Financial Accounting Ledger (Transactions)
CREATE TABLE IF NOT EXISTS public.vyora_wallet_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_ref TEXT UNIQUE,
    user_id TEXT NOT NULL REFERENCES public.vyora_users_sync(firebase_uid) ON DELETE CASCADE,
    tx_type TEXT NOT NULL CHECK (tx_type IN ('view_reward', 'creator_bonus', 'withdrawal', 'adjustment')),
    points_delta BIGINT NOT NULL,
    amount_delta NUMERIC(12, 4) NOT NULL,
    description TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'completed' CHECK (status IN ('completed', 'pending', 'rejected')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vyora_wallet_tx_user_created ON public.vyora_wallet_transactions(user_id, created_at DESC);

-- 5. Creator Withdrawal Requests
CREATE TABLE IF NOT EXISTS public.vyora_withdrawal_requests (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES public.vyora_users_sync(firebase_uid) ON DELETE CASCADE,
    amount_usd NUMERIC(12, 4) NOT NULL CHECK (amount_usd > 0),
    points_spent BIGINT NOT NULL CHECK (points_spent > 0),
    method TEXT NOT NULL,
    account_details TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    reviewed_by TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vyora_withdrawals_user_status ON public.vyora_withdrawal_requests(user_id, status, created_at DESC);

-- ============================================================================
-- Atomic PL/pgSQL Accounting Functions (Invoked via service_role from Backend)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.record_vyora_view_reward(
    p_video_id TEXT,
    p_viewer_uid TEXT,
    p_creator_uid TEXT,
    p_points_earned BIGINT,
    p_usd_earned NUMERIC,
    p_should_reward BOOLEAN
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_ref TEXT := 'view:' || p_video_id || ':' || p_viewer_uid;
    v_rows_affected INTEGER := 0;
BEGIN
    UPDATE public.vyora_videos_ledger
    SET views_count = views_count + 1,
        updated_at = NOW()
    WHERE video_id = p_video_id;

    IF p_should_reward AND p_creator_uid <> '' AND p_creator_uid <> p_viewer_uid AND p_usd_earned > 0 THEN
        INSERT INTO public.vyora_wallets (user_id, points, balance_usd, total_earned_usd, updated_at)
        VALUES (p_creator_uid, 0, 0, 0, NOW())
        ON CONFLICT (user_id) DO NOTHING;

        INSERT INTO public.vyora_wallet_transactions (
            external_ref, user_id, tx_type, points_delta, amount_delta, description, status
        )
        VALUES (
            v_ref,
            p_creator_uid,
            'view_reward',
            p_points_earned,
            p_usd_earned,
            'View reward for video ' || LEFT(p_video_id, 8),
            'completed'
        )
        ON CONFLICT (external_ref) DO NOTHING;

        GET DIAGNOSTICS v_rows_affected = ROW_COUNT;

        IF v_rows_affected > 0 THEN
            UPDATE public.vyora_wallets
            SET points = points + p_points_earned,
                balance_usd = balance_usd + p_usd_earned,
                total_earned_usd = total_earned_usd + p_usd_earned,
                updated_at = NOW()
            WHERE user_id = p_creator_uid;
        END IF;
    END IF;

    RETURN TRUE;
END;
$$;

CREATE OR REPLACE FUNCTION public.grant_vyora_points_ledger(
    p_target_uid TEXT,
    p_points_delta BIGINT,
    p_amount_delta NUMERIC,
    p_reason TEXT,
    p_external_ref TEXT
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_rows_affected INTEGER := 0;
BEGIN
    INSERT INTO public.vyora_wallets (user_id, points, balance_usd, total_earned_usd, updated_at)
    VALUES (p_target_uid, 0, 0, 0, NOW())
    ON CONFLICT (user_id) DO NOTHING;

    INSERT INTO public.vyora_wallet_transactions (
        external_ref, user_id, tx_type, points_delta, amount_delta, description, status
    )
    VALUES (
        p_external_ref,
        p_target_uid,
        'adjustment',
        p_points_delta,
        p_amount_delta,
        COALESCE(NULLIF(p_reason, ''), 'Admin adjustment'),
        'completed'
    )
    ON CONFLICT (external_ref) DO NOTHING;

    GET DIAGNOSTICS v_rows_affected = ROW_COUNT;

    IF v_rows_affected > 0 THEN
        UPDATE public.vyora_wallets
        SET points = GREATEST(0, points + p_points_delta),
            balance_usd = GREATEST(0, balance_usd + p_amount_delta),
            total_earned_usd = total_earned_usd + GREATEST(0, p_amount_delta),
            updated_at = NOW()
        WHERE user_id = p_target_uid;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.review_vyora_withdrawal(
    p_request_id TEXT,
    p_reviewer_uid TEXT,
    p_approve BOOLEAN
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_req public.vyora_withdrawal_requests%ROWTYPE;
    v_new_status TEXT := CASE WHEN p_approve THEN 'approved' ELSE 'rejected' END;
    v_tx_ref TEXT := 'withdrawal:' || p_request_id;
    v_rows_affected INTEGER := 0;
BEGIN
    SELECT * INTO v_req
    FROM public.vyora_withdrawal_requests
    WHERE id = p_request_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    IF v_req.status <> 'pending' THEN
        RETURN;
    END IF;

    UPDATE public.vyora_withdrawal_requests
    SET status = v_new_status,
        reviewed_by = p_reviewer_uid,
        updated_at = NOW()
    WHERE id = p_request_id;

    IF p_approve THEN
        INSERT INTO public.vyora_wallet_transactions (
            external_ref, user_id, tx_type, points_delta, amount_delta, description, status
        )
        VALUES (
            v_tx_ref,
            v_req.user_id,
            'withdrawal',
            -v_req.points_spent,
            -v_req.amount_usd,
            'Withdrawal via ' || v_req.method,
            'completed'
        )
        ON CONFLICT (external_ref) DO NOTHING;

        GET DIAGNOSTICS v_rows_affected = ROW_COUNT;

        IF v_rows_affected > 0 THEN
            UPDATE public.vyora_wallets
            SET points = GREATEST(0, points - v_req.points_spent),
                balance_usd = GREATEST(0, balance_usd - v_req.amount_usd),
                updated_at = NOW()
            WHERE user_id = v_req.user_id;
        END IF;
    END IF;
END;
$$;

-- ============================================================================
-- Row-Level Security (RLS) — Zero-Trust Financial Isolation
-- ============================================================================

ALTER TABLE public.vyora_users_sync ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_videos_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_wallets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_wallet_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vyora_withdrawal_requests ENABLE ROW LEVEL SECURITY;

-- Public active video catalog readable with publishable key (no PII or financial fields exposed)
DROP POLICY IF EXISTS "Public active videos readable" ON public.vyora_videos_ledger;
CREATE POLICY "Public active videos readable"
    ON public.vyora_videos_ledger
    FOR SELECT
    TO anon, authenticated
    USING (status = 'active');

-- Authenticated JWT owners can read their own synced wallet, transactions, and withdrawals if JWT sub is mapped
DROP POLICY IF EXISTS "Own wallet readable by sub" ON public.vyora_wallets;
CREATE POLICY "Own wallet readable by sub"
    ON public.vyora_wallets
    FOR SELECT
    TO authenticated
    USING ((auth.jwt() ->> 'sub') = user_id);

DROP POLICY IF EXISTS "Own transactions readable by sub" ON public.vyora_wallet_transactions;
CREATE POLICY "Own transactions readable by sub"
    ON public.vyora_wallet_transactions
    FOR SELECT
    TO authenticated
    USING ((auth.jwt() ->> 'sub') = user_id);

DROP POLICY IF EXISTS "Own withdrawals readable by sub" ON public.vyora_withdrawal_requests;
CREATE POLICY "Own withdrawals readable by sub"
    ON public.vyora_withdrawal_requests
    FOR SELECT
    TO authenticated
    USING ((auth.jwt() ->> 'sub') = user_id);

-- Note: service_role (SUPABASE_SECRET_KEY in Firebase Cloud Functions) handles all financial writes.
