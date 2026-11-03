-- Explicit operator rollback, not an automatically applied Prisma migration.
-- It may reject new rows that became valid under five-day assumptions. In that
-- case this entire transaction rolls back; never rewrite health data to force it.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE "PeriodEvent"
DROP CONSTRAINT "PeriodEvent_userId_active_range_excl";

ALTER TABLE "PeriodEvent"
ADD CONSTRAINT "PeriodEvent_userId_active_range_excl"
EXCLUDE USING gist (
    "userId" WITH =,
    daterange("startDate", "endDate", '[]') WITH &&
)
WHERE ("deletedAt" IS NULL);
COMMIT;
