BEGIN;

ALTER TABLE public.email_verification_tokens
    ADD COLUMN email VARCHAR(254);

-- Invalidate old tokens whose destination email was not recorded.
UPDATE public.email_verification_tokens
SET used_at = CURRENT_TIMESTAMP
WHERE used_at IS NULL;

COMMIT;