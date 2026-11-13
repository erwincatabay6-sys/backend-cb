BEGIN;

ALTER TABLE public.repair_status_history
    ADD COLUMN customer_update varchar(2000);

COMMENT ON COLUMN public.repair_status_history.customer_update
    IS 'Optional customer-visible update for tracking and customer email notifications.';

COMMIT;