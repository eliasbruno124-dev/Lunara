-- Schema-only: raw null ends, versions and timestamps remain untouched.
-- The old exclusion constraint already forbids every newly forbidden overlap;
-- replacing its unbounded null end with start + 4 only permits more ranges.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE "PeriodEvent"
DROP CONSTRAINT "PeriodEvent_userId_active_range_excl";

-- Transaction-held table lock prevents writes between removal and replacement.
-- Closed date bounds include day 5; a new period on day 6 is disjoint.
ALTER TABLE "PeriodEvent"
ADD CONSTRAINT "PeriodEvent_userId_active_range_excl"
EXCLUDE USING gist (
    "userId" WITH =,
    daterange("startDate", COALESCE("endDate", "startDate" + 4), '[]') WITH &&
)
WHERE ("deletedAt" IS NULL);
COMMIT;
