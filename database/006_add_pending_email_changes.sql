BEGIN;

CREATE TABLE public.pending_email_changes (
    user_id BIGINT PRIMARY KEY
        REFERENCES public.users (id) ON DELETE CASCADE,

    proposed_email VARCHAR(254) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT pending_email_changes_email_not_blank
        CHECK (btrim(proposed_email) <> ''),

    CONSTRAINT pending_email_changes_code_hash_length
        CHECK (length(code_hash) = 64),

    CONSTRAINT pending_email_changes_valid_expiry
        CHECK (expires_at > issued_at),

    CONSTRAINT pending_email_changes_attempts_nonnegative
        CHECK (failed_attempts >= 0)
);

COMMIT;