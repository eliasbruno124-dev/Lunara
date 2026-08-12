-- Bind user-defined partner tips to both their creator and household. Built-in
-- catalogue entries deliberately retain NULL ownership and remain global.
ALTER TABLE "PartnerTip" ADD COLUMN "householdId" UUID;
ALTER TABLE "PartnerTip" ADD COLUMN "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE "PartnerTip" ADD COLUMN "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE "CustomSymptom" ADD COLUMN "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX "PartnerTip_createdByUserId_householdId_enabled_idx"
  ON "PartnerTip"("createdByUserId", "householdId", "enabled");

CREATE INDEX "PartnerTip_householdId_category_enabled_idx"
  ON "PartnerTip"("householdId", "category", "enabled");

ALTER TABLE "PartnerTip"
  ADD CONSTRAINT "PartnerTip_createdByUserId_fkey"
  FOREIGN KEY ("createdByUserId") REFERENCES "User"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "PartnerTip"
  ADD CONSTRAINT "PartnerTip_householdId_fkey"
  FOREIGN KEY ("householdId") REFERENCES "Household"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "PartnerTip"
  ADD CONSTRAINT "PartnerTip_user_defined_ownership_check"
  CHECK (
    ("category" = 'USER_DEFINED' AND "createdByUserId" IS NOT NULL AND "householdId" IS NOT NULL)
    OR
    ("category" <> 'USER_DEFINED' AND "createdByUserId" IS NULL AND "householdId" IS NULL)
  );

CREATE UNIQUE INDEX "CustomSymptom_userId_lower_label_key"
  ON "CustomSymptom"("userId", lower("label"));
