-- Schema-only: new accounts without an explicit language default to English.
-- Existing accounts keep their stored preferredLanguage.
ALTER TABLE "User" ALTER COLUMN "preferredLanguage" SET DEFAULT 'EN';
