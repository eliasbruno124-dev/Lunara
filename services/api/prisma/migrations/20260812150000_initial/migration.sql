-- CreateSchema
CREATE SCHEMA IF NOT EXISTS "public";

-- CreateEnum
CREATE TYPE "UserRole" AS ENUM ('TRACKER', 'PARTNER');

-- CreateEnum
CREATE TYPE "PreferredLanguage" AS ENUM ('DE', 'EN');

-- CreateEnum
CREATE TYPE "SpecialMode" AS ENUM ('NONE', 'HORMONAL_CONTRACEPTION', 'PREGNANCY', 'POSTPARTUM', 'MENOPAUSE_TRANSITION', 'PAUSED');

-- CreateEnum
CREATE TYPE "PeriodSource" AS ENUM ('MANUAL', 'IMPORT', 'SYNC');

-- CreateEnum
CREATE TYPE "FlowLevel" AS ENUM ('SPOTTING', 'LIGHT', 'MEDIUM', 'HEAVY');

-- CreateEnum
CREATE TYPE "OvulationTestResult" AS ENUM ('NEGATIVE', 'POSITIVE', 'UNCLEAR');

-- CreateEnum
CREATE TYPE "PredictionConfidence" AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'UNAVAILABLE');

-- CreateEnum
CREATE TYPE "TipCategory" AS ENUM ('COMMUNICATION', 'PRACTICAL_HELP', 'COMFORT', 'SPACE', 'FOOD_AND_DRINK', 'EMOTIONAL_SUPPORT', 'PERIOD_DAYS', 'PREMENSTRUAL_ESTIMATE', 'GENERAL', 'USER_DEFINED');

-- CreateEnum
CREATE TYPE "CyclePhase" AS ENUM ('MENSTRUATION', 'FOLLICULAR_ESTIMATE', 'OVULATION_ESTIMATE', 'LUTEAL_ESTIMATE', 'PREMENSTRUAL_ESTIMATE', 'UNKNOWN');

-- CreateEnum
CREATE TYPE "AuditEventType" AS ENUM ('LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'NEW_DEVICE', 'SESSION_REVOKED', 'INVITATION_CREATED', 'INVITATION_REVOKED', 'SHARING_CHANGED', 'DATA_EXPORTED', 'ACCOUNT_DELETED', 'ACCOUNT_DISABLED');

-- CreateEnum
CREATE TYPE "SyncOperation" AS ENUM ('UPSERT', 'DELETE');

-- CreateEnum
CREATE TYPE "CheckInStatus" AS ENUM ('PENDING', 'ANSWERED', 'EXPIRED', 'DISMISSED');

-- CreateTable
CREATE TABLE "User" (
    "id" UUID NOT NULL,
    "username" VARCHAR(64) NOT NULL,
    "displayName" VARCHAR(120) NOT NULL,
    "passwordHash" TEXT NOT NULL,
    "preferredLanguage" "PreferredLanguage" NOT NULL DEFAULT 'DE',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "disabledAt" TIMESTAMP(3),

    CONSTRAINT "User_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Household" (
    "id" UUID NOT NULL,
    "name" VARCHAR(120) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Household_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Membership" (
    "id" UUID NOT NULL,
    "householdId" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "role" "UserRole" NOT NULL,
    "customRoleLabel" VARCHAR(80),
    "permissions" JSONB NOT NULL DEFAULT '{}',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Membership_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DeviceSession" (
    "id" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "tokenFamilyId" UUID NOT NULL,
    "deviceName" VARCHAR(120) NOT NULL,
    "refreshTokenHash" CHAR(64) NOT NULL,
    "lastUsedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "revokedAt" TIMESTAMP(3),
    "rotatedFromId" UUID,

    CONSTRAINT "DeviceSession_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CycleProfile" (
    "id" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "typicalCycleLengthOptional" INTEGER,
    "typicalPeriodLengthOptional" INTEGER,
    "lutealLengthOptional" INTEGER,
    "fertilityPredictionEnabled" BOOLEAN NOT NULL DEFAULT false,
    "predictionPaused" BOOLEAN NOT NULL DEFAULT false,
    "specialMode" "SpecialMode" NOT NULL DEFAULT 'NONE',
    "timezone" VARCHAR(64) NOT NULL DEFAULT 'Europe/Berlin',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CycleProfile_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PeriodEvent" (
    "id" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "startDate" DATE NOT NULL,
    "endDate" DATE,
    "source" "PeriodSource" NOT NULL DEFAULT 'MANUAL',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),
    "version" INTEGER NOT NULL DEFAULT 1,

    CONSTRAINT "PeriodEvent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DailyLog" (
    "id" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "localDate" DATE NOT NULL,
    "flowLevel" "FlowLevel",
    "painLevel" INTEGER,
    "mood" VARCHAR(64),
    "energy" INTEGER,
    "sleepQuality" INTEGER,
    "bodyTemperature" DECIMAL(4,2),
    "ovulationTestResult" "OvulationTestResult",
    "customSymptoms" JSONB NOT NULL DEFAULT '[]',
    "encryptedNote" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),
    "version" INTEGER NOT NULL DEFAULT 1,

    CONSTRAINT "DailyLog_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SharingSettings" (
    "id" UUID NOT NULL,
    "ownerUserId" UUID NOT NULL,
    "householdId" UUID NOT NULL,
    "sharePrediction" BOOLEAN NOT NULL DEFAULT true,
    "sharePeriodDates" BOOLEAN NOT NULL DEFAULT false,
    "sharePhase" BOOLEAN NOT NULL DEFAULT false,
    "shareFlow" BOOLEAN NOT NULL DEFAULT false,
    "sharePain" BOOLEAN NOT NULL DEFAULT false,
    "shareMood" BOOLEAN NOT NULL DEFAULT false,
    "shareEnergy" BOOLEAN NOT NULL DEFAULT false,
    "shareSleep" BOOLEAN NOT NULL DEFAULT false,
    "shareSymptoms" BOOLEAN NOT NULL DEFAULT false,
    "shareNotes" BOOLEAN NOT NULL DEFAULT false,
    "shareFertileWindow" BOOLEAN NOT NULL DEFAULT false,
    "allowSymptomBasedTips" BOOLEAN NOT NULL DEFAULT false,
    "tipsEnabled" BOOLEAN NOT NULL DEFAULT true,
    "checkInsEnabled" BOOLEAN NOT NULL DEFAULT true,
    "sharingPaused" BOOLEAN NOT NULL DEFAULT false,
    "privateDate" DATE,
    "disabledTipCategories" "TipCategory"[] DEFAULT ARRAY[]::"TipCategory"[],
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "SharingSettings_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CarePreferences" (
    "id" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "likesWarmth" BOOLEAN NOT NULL DEFAULT false,
    "likesCuddling" BOOLEAN NOT NULL DEFAULT false,
    "likesSpace" BOOLEAN NOT NULL DEFAULT false,
    "likesSnacks" BOOLEAN NOT NULL DEFAULT false,
    "likesConversation" BOOLEAN NOT NULL DEFAULT false,
    "likesHouseholdHelp" BOOLEAN NOT NULL DEFAULT false,
    "likesReminders" BOOLEAN NOT NULL DEFAULT false,
    "dislikesUnsolicitedAdvice" BOOLEAN NOT NULL DEFAULT true,
    "customHelpfulActions" JSONB NOT NULL DEFAULT '[]',
    "customUnhelpfulActions" JSONB NOT NULL DEFAULT '[]',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CarePreferences_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Prediction" (
    "id" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "predictedStartFrom" DATE NOT NULL,
    "predictedStartTo" DATE NOT NULL,
    "predictedEndFrom" DATE NOT NULL,
    "predictedEndTo" DATE NOT NULL,
    "fertileWindowFrom" DATE,
    "fertileWindowTo" DATE,
    "confidence" "PredictionConfidence" NOT NULL,
    "basedOnCycleCount" INTEGER NOT NULL,
    "calculatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "algorithmVersion" VARCHAR(32) NOT NULL,
    "explanation" JSONB NOT NULL,
    "typicalCycleLength" INTEGER NOT NULL,
    "typicalPeriodLength" INTEGER NOT NULL,
    "variationDays" INTEGER NOT NULL,

    CONSTRAINT "Prediction_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PartnerTip" (
    "id" UUID NOT NULL,
    "category" "TipCategory" NOT NULL,
    "titleDe" VARCHAR(160) NOT NULL,
    "bodyDe" VARCHAR(600) NOT NULL,
    "callToActionDe" VARCHAR(240) NOT NULL,
    "titleEn" VARCHAR(160) NOT NULL,
    "bodyEn" VARCHAR(600) NOT NULL,
    "callToActionEn" VARCHAR(240) NOT NULL,
    "requiredSharedFields" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "applicablePhase" "CyclePhase",
    "applicableSymptoms" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "priority" INTEGER NOT NULL DEFAULT 0,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "createdByUserId" UUID,

    CONSTRAINT "PartnerTip_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "TipFeedback" (
    "id" UUID NOT NULL,
    "tipId" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "helpful" BOOLEAN,
    "dismissed" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "TipFeedback_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Invitation" (
    "id" UUID NOT NULL,
    "householdId" UUID NOT NULL,
    "tokenHash" CHAR(64) NOT NULL,
    "assignedRole" "UserRole" NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "usedAt" TIMESTAMP(3),
    "revokedAt" TIMESTAMP(3),
    "createdByUserId" UUID NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Invitation_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AuditEvent" (
    "id" UUID NOT NULL,
    "householdId" UUID,
    "actorUserId" UUID,
    "type" "AuditEventType" NOT NULL,
    "ipHash" CHAR(64),
    "metadata" JSONB NOT NULL DEFAULT '{}',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AuditEvent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "IdempotencyKey" (
    "id" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "householdId" UUID NOT NULL,
    "key" VARCHAR(128) NOT NULL,
    "method" VARCHAR(12) NOT NULL,
    "path" VARCHAR(240) NOT NULL,
    "requestHash" CHAR(64) NOT NULL,
    "responseStatus" INTEGER NOT NULL,
    "responseBody" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expiresAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "IdempotencyKey_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SyncChange" (
    "cursor" BIGSERIAL NOT NULL,
    "householdId" UUID NOT NULL,
    "ownerUserId" UUID NOT NULL,
    "entityType" VARCHAR(40) NOT NULL,
    "entityId" UUID NOT NULL,
    "operation" "SyncOperation" NOT NULL,
    "entityVersion" INTEGER NOT NULL,
    "occurredAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SyncChange_pkey" PRIMARY KEY ("cursor")
);

-- CreateTable
CREATE TABLE "CheckIn" (
    "id" UUID NOT NULL,
    "householdId" UUID NOT NULL,
    "senderUserId" UUID NOT NULL,
    "recipientUserId" UUID NOT NULL,
    "prompt" VARCHAR(240) NOT NULL,
    "response" VARCHAR(500),
    "status" "CheckInStatus" NOT NULL DEFAULT 'PENDING',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "answeredAt" TIMESTAMP(3),

    CONSTRAINT "CheckIn_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CustomSymptom" (
    "id" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "label" VARCHAR(80) NOT NULL,
    "archivedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "CustomSymptom_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ImportBatch" (
    "id" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "householdId" UUID NOT NULL,
    "sourceFormat" VARCHAR(32) NOT NULL,
    "recordsCreated" INTEGER NOT NULL DEFAULT 0,
    "recordsSkipped" INTEGER NOT NULL DEFAULT 0,
    "reversibleUntil" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ImportBatch_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "User_username_key" ON "User"("username");

-- CreateIndex
CREATE INDEX "User_disabledAt_idx" ON "User"("disabledAt");

-- CreateIndex
CREATE INDEX "Membership_userId_idx" ON "Membership"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "Membership_householdId_userId_key" ON "Membership"("householdId", "userId");

-- CreateIndex
CREATE UNIQUE INDEX "Membership_householdId_role_key" ON "Membership"("householdId", "role");

-- CreateIndex
CREATE UNIQUE INDEX "DeviceSession_refreshTokenHash_key" ON "DeviceSession"("refreshTokenHash");

-- CreateIndex
CREATE UNIQUE INDEX "DeviceSession_rotatedFromId_key" ON "DeviceSession"("rotatedFromId");

-- CreateIndex
CREATE INDEX "DeviceSession_userId_revokedAt_idx" ON "DeviceSession"("userId", "revokedAt");

-- CreateIndex
CREATE INDEX "DeviceSession_tokenFamilyId_revokedAt_idx" ON "DeviceSession"("tokenFamilyId", "revokedAt");

-- CreateIndex
CREATE UNIQUE INDEX "CycleProfile_userId_key" ON "CycleProfile"("userId");

-- CreateIndex
CREATE INDEX "PeriodEvent_userId_startDate_idx" ON "PeriodEvent"("userId", "startDate");

-- CreateIndex
CREATE INDEX "PeriodEvent_userId_updatedAt_idx" ON "PeriodEvent"("userId", "updatedAt");

-- CreateIndex
CREATE UNIQUE INDEX "PeriodEvent_userId_id_key" ON "PeriodEvent"("userId", "id");

-- CreateIndex
CREATE INDEX "DailyLog_userId_updatedAt_idx" ON "DailyLog"("userId", "updatedAt");

-- CreateIndex
CREATE UNIQUE INDEX "DailyLog_userId_localDate_key" ON "DailyLog"("userId", "localDate");

-- CreateIndex
CREATE INDEX "SharingSettings_householdId_idx" ON "SharingSettings"("householdId");

-- CreateIndex
CREATE UNIQUE INDEX "SharingSettings_ownerUserId_householdId_key" ON "SharingSettings"("ownerUserId", "householdId");

-- CreateIndex
CREATE UNIQUE INDEX "CarePreferences_userId_key" ON "CarePreferences"("userId");

-- CreateIndex
CREATE INDEX "Prediction_userId_calculatedAt_idx" ON "Prediction"("userId", "calculatedAt");

-- CreateIndex
CREATE INDEX "PartnerTip_category_enabled_priority_idx" ON "PartnerTip"("category", "enabled", "priority");

-- CreateIndex
CREATE UNIQUE INDEX "TipFeedback_tipId_userId_key" ON "TipFeedback"("tipId", "userId");

-- CreateIndex
CREATE UNIQUE INDEX "Invitation_tokenHash_key" ON "Invitation"("tokenHash");

-- CreateIndex
CREATE INDEX "Invitation_householdId_expiresAt_idx" ON "Invitation"("householdId", "expiresAt");

-- CreateIndex
CREATE INDEX "AuditEvent_householdId_createdAt_idx" ON "AuditEvent"("householdId", "createdAt");

-- CreateIndex
CREATE INDEX "AuditEvent_actorUserId_createdAt_idx" ON "AuditEvent"("actorUserId", "createdAt");

-- CreateIndex
CREATE INDEX "IdempotencyKey_expiresAt_idx" ON "IdempotencyKey"("expiresAt");

-- CreateIndex
CREATE UNIQUE INDEX "IdempotencyKey_userId_key_key" ON "IdempotencyKey"("userId", "key");

-- CreateIndex
CREATE INDEX "SyncChange_householdId_cursor_idx" ON "SyncChange"("householdId", "cursor");

-- CreateIndex
CREATE INDEX "SyncChange_ownerUserId_cursor_idx" ON "SyncChange"("ownerUserId", "cursor");

-- CreateIndex
CREATE INDEX "CheckIn_recipientUserId_status_expiresAt_idx" ON "CheckIn"("recipientUserId", "status", "expiresAt");

-- CreateIndex
CREATE UNIQUE INDEX "CustomSymptom_userId_label_key" ON "CustomSymptom"("userId", "label");

-- CreateIndex
CREATE INDEX "ImportBatch_userId_createdAt_idx" ON "ImportBatch"("userId", "createdAt");

-- AddForeignKey
ALTER TABLE "Membership" ADD CONSTRAINT "Membership_householdId_fkey" FOREIGN KEY ("householdId") REFERENCES "Household"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Membership" ADD CONSTRAINT "Membership_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DeviceSession" ADD CONSTRAINT "DeviceSession_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DeviceSession" ADD CONSTRAINT "DeviceSession_rotatedFromId_fkey" FOREIGN KEY ("rotatedFromId") REFERENCES "DeviceSession"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CycleProfile" ADD CONSTRAINT "CycleProfile_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PeriodEvent" ADD CONSTRAINT "PeriodEvent_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DailyLog" ADD CONSTRAINT "DailyLog_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SharingSettings" ADD CONSTRAINT "SharingSettings_ownerUserId_fkey" FOREIGN KEY ("ownerUserId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SharingSettings" ADD CONSTRAINT "SharingSettings_householdId_fkey" FOREIGN KEY ("householdId") REFERENCES "Household"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CarePreferences" ADD CONSTRAINT "CarePreferences_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Prediction" ADD CONSTRAINT "Prediction_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TipFeedback" ADD CONSTRAINT "TipFeedback_tipId_fkey" FOREIGN KEY ("tipId") REFERENCES "PartnerTip"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TipFeedback" ADD CONSTRAINT "TipFeedback_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Invitation" ADD CONSTRAINT "Invitation_householdId_fkey" FOREIGN KEY ("householdId") REFERENCES "Household"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Invitation" ADD CONSTRAINT "Invitation_createdByUserId_fkey" FOREIGN KEY ("createdByUserId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AuditEvent" ADD CONSTRAINT "AuditEvent_householdId_fkey" FOREIGN KEY ("householdId") REFERENCES "Household"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AuditEvent" ADD CONSTRAINT "AuditEvent_actorUserId_fkey" FOREIGN KEY ("actorUserId") REFERENCES "User"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "IdempotencyKey" ADD CONSTRAINT "IdempotencyKey_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "IdempotencyKey" ADD CONSTRAINT "IdempotencyKey_householdId_fkey" FOREIGN KEY ("householdId") REFERENCES "Household"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SyncChange" ADD CONSTRAINT "SyncChange_householdId_fkey" FOREIGN KEY ("householdId") REFERENCES "Household"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CheckIn" ADD CONSTRAINT "CheckIn_householdId_fkey" FOREIGN KEY ("householdId") REFERENCES "Household"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CheckIn" ADD CONSTRAINT "CheckIn_senderUserId_fkey" FOREIGN KEY ("senderUserId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CheckIn" ADD CONSTRAINT "CheckIn_recipientUserId_fkey" FOREIGN KEY ("recipientUserId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CustomSymptom" ADD CONSTRAINT "CustomSymptom_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ImportBatch" ADD CONSTRAINT "ImportBatch_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ImportBatch" ADD CONSTRAINT "ImportBatch_householdId_fkey" FOREIGN KEY ("householdId") REFERENCES "Household"("id") ON DELETE CASCADE ON UPDATE CASCADE;
