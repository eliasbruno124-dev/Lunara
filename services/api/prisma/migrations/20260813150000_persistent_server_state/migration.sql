-- Bootstrap is a one-time server lifecycle event, not a property inferred from
-- the current number of accounts. This singleton survives deletion of every
-- relationship and therefore cannot accidentally reopen first-run setup.
CREATE TABLE "ServerState" (
    "id" INTEGER NOT NULL DEFAULT 1,
    "bootstrappedAt" TIMESTAMP(3),
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ServerState_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "ServerState_singleton_check" CHECK ("id" = 1)
);

-- Existing installations with at least one account have already completed
-- bootstrap. On a genuinely fresh database the timestamp intentionally stays
-- NULL so the initial setup flow remains available.
INSERT INTO "ServerState" ("id", "bootstrappedAt", "updatedAt")
SELECT
    1,
    CASE
        WHEN EXISTS (SELECT 1 FROM "User") THEN CURRENT_TIMESTAMP
        ELSE NULL
    END,
    CURRENT_TIMESTAMP;
