-- Permanently valid invitations are represented by a NULL expiry. They remain
-- single-use and can still be revoked explicitly.
ALTER TABLE "Invitation" ALTER COLUMN "expiresAt" DROP NOT NULL;

-- Version 1 assigns every account to exactly one private relationship. This
-- removes ambiguous login/session tenant selection at the database boundary.
CREATE UNIQUE INDEX "Membership_userId_key" ON "Membership"("userId");

-- A household can only expose one active permanent PARTNER invitation. The
-- partial index also closes the concurrent create race across API processes.
CREATE UNIQUE INDEX "Invitation_one_open_permanent_role_key"
ON "Invitation"("householdId", "assignedRole")
WHERE "expiresAt" IS NULL AND "usedAt" IS NULL AND "revokedAt" IS NULL;
