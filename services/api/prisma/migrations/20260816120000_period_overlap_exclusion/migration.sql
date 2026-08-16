-- PostgreSQL exclusion constraints need GiST equality support for UUIDs.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Do not guess how to repair existing health data. A deployment with invalid
-- or overlapping active rows stops here so an administrator can review it.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM "PeriodEvent"
        WHERE "deletedAt" IS NULL
          AND "endDate" IS NOT NULL
          AND "endDate" < "startDate"
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce period-range exclusion: an active period ends before it starts'
            USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM "PeriodEvent" AS left_period
        JOIN "PeriodEvent" AS right_period
          ON left_period."userId" = right_period."userId"
         AND left_period."id" < right_period."id"
         AND left_period."deletedAt" IS NULL
         AND right_period."deletedAt" IS NULL
         AND daterange(left_period."startDate", left_period."endDate", '[]')
             && daterange(right_period."startDate", right_period."endDate", '[]')
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce period-range exclusion: overlapping active periods exist'
            USING ERRCODE = '23P01';
    END IF;
END
$$;

-- Bounds are inclusive. PostgreSQL canonicalizes a closed date range to
-- [start, end + 1); a null end remains unbounded. Soft-deleted rows are
-- intentionally excluded so a corrected range can reuse the old dates.
ALTER TABLE "PeriodEvent"
ADD CONSTRAINT "PeriodEvent_userId_active_range_excl"
EXCLUDE USING gist (
    "userId" WITH =,
    daterange("startDate", "endDate", '[]') WITH &&
)
WHERE ("deletedAt" IS NULL);
