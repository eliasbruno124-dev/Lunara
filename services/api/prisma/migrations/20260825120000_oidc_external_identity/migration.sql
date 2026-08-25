-- Authentik identities are linked explicitly to an existing Lunara account.
-- Local passwords remain supported, but may be disabled per account by setting
-- passwordHash to NULL after an identity has been linked.
CREATE TYPE "SessionAuthMethod" AS ENUM ('PASSWORD', 'OIDC');

ALTER TYPE "AuditEventType" ADD VALUE 'OIDC_IDENTITY_LINKED';

ALTER TABLE "User"
  ALTER COLUMN "passwordHash" DROP NOT NULL;

CREATE TABLE "ExternalIdentity" (
  "id" UUID NOT NULL,
  "userId" UUID NOT NULL,
  "issuer" VARCHAR(512) NOT NULL,
  "subject" VARCHAR(255) NOT NULL,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "lastUsedAt" TIMESTAMP(3),

  CONSTRAINT "ExternalIdentity_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "DeviceSession"
  ADD COLUMN "authMethod" "SessionAuthMethod" NOT NULL DEFAULT 'PASSWORD',
  ADD COLUMN "externalIdentityId" UUID;

CREATE UNIQUE INDEX "ExternalIdentity_issuer_subject_key"
  ON "ExternalIdentity"("issuer", "subject");
CREATE UNIQUE INDEX "ExternalIdentity_userId_issuer_key"
  ON "ExternalIdentity"("userId", "issuer");
CREATE INDEX "ExternalIdentity_userId_idx"
  ON "ExternalIdentity"("userId");
CREATE INDEX "DeviceSession_externalIdentityId_idx"
  ON "DeviceSession"("externalIdentityId");

ALTER TABLE "ExternalIdentity"
  ADD CONSTRAINT "ExternalIdentity_userId_fkey"
  FOREIGN KEY ("userId") REFERENCES "User"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "DeviceSession"
  ADD CONSTRAINT "DeviceSession_externalIdentityId_fkey"
  FOREIGN KEY ("externalIdentityId") REFERENCES "ExternalIdentity"("id")
  ON DELETE SET NULL ON UPDATE CASCADE;
