-- Adds the primary problem category used for repeated-device-problem detection.
-- Existing repairs remain uncategorized (NULL).

BEGIN;

ALTER TABLE public.repair_jobs
ADD COLUMN problem_category character varying(30);

ALTER TABLE public.repair_jobs
ADD CONSTRAINT repair_jobs_problem_category_check
CHECK (
    problem_category IN (
        'CHARGING',
        'BATTERY',
        'DISPLAY',
        'POWER',
        'AUDIO',
        'CAMERA',
        'CONNECTIVITY',
        'SOFTWARE',
        'COOLING',
        'OTHER'
    )
);

COMMENT ON COLUMN public.repair_jobs.problem_category IS
'Staff-selected primary problem category. NULL means uncategorized.';

COMMIT;