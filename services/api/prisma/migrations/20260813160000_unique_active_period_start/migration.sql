-- A tracker can have at most one active period start on a local calendar date.
-- Soft-deleted events deliberately remain outside this constraint so that a
-- corrected/new event can later reuse the same date with a new client UUID.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM "PeriodEvent"
        WHERE "deletedAt" IS NULL
        GROUP BY "userId", "startDate"
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce active period-date uniqueness: duplicate active events exist'
            USING ERRCODE = '23505';
    END IF;
END
$$;

CREATE UNIQUE INDEX "PeriodEvent_userId_startDate_active_key"
ON "PeriodEvent"("userId", "startDate")
WHERE "deletedAt" IS NULL;
