import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { AuditEventType, PeriodSource, Prisma, SyncOperation, UserRole } from '@prisma/client';
import { createCipheriv, pbkdf2Sync, randomBytes, randomUUID } from 'node:crypto';
import * as argon2 from 'argon2';
import {
  assertCurrentTrackerMembership,
  lockRelationship,
  lockMembership,
  lockUserAuthentication,
  lockUserPeriods,
} from '../common/database/database-coordination.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { EncryptionService } from '../common/security/encryption.service.js';
import { DailyLogsService } from '../daily-logs/daily-logs.service.js';
import {
  isPeriodOverlapConstraintError,
  PERIOD_OVERLAP_MESSAGE,
} from '../periods/period-constraints.js';
import { PredictionService } from '../prediction/prediction.service.js';
import { effectivePeriodEndLocalDate } from '../periods/period-policy.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { DeleteAccountDto, ExportDto, ExportFormatDto, ImportDto } from './data-management.dto.js';
import { OidcTokenVerifier } from '../auth/oidc-token-verifier.js';

interface ImportPeriod {
  id?: string;
  startDate: string;
  endDate?: string | null;
}

interface ImportDailyLog {
  id?: string;
  localDate: string;
  flowLevel?: 'SPOTTING' | 'LIGHT' | 'MEDIUM' | 'HEAVY' | null;
  painLevel?: number | null;
  mood?: string | null;
  energy?: number | null;
  sleepQuality?: number | null;
  bodyTemperature?: number | null;
  customSymptoms?: string[];
  note?: string | null;
}

interface StoredPeriodRange {
  id: string;
  startDate: Date;
  endDate: Date | null;
  deletedAt: Date | null;
}

interface PeriodImportPlan {
  create: ImportPeriod[];
  duplicates: number;
  conflicts: number;
}

interface DailyLogImportPlan {
  create: ImportDailyLog[];
  duplicates: number;
}

@Injectable()
export class DataManagementService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly logs: DailyLogsService,
    private readonly encryption: EncryptionService,
    private readonly predictions: PredictionService,
    private readonly oidcVerifier?: OidcTokenVerifier,
  ) {}

  async export(user: AuthenticatedUser, dto: ExportDto) {
    const bundle = await this.buildBundle(user);
    const format = dto.format ?? ExportFormatDto.JSON;
    let result: { filename: string; mediaType: string; encoding: 'utf8' | 'base64'; data: unknown };
    if (format === ExportFormatDto.PERIODS_CSV) {
      result = {
        filename: `lunara-periods-${this.today()}.csv`,
        mediaType: 'text/csv; charset=utf-8',
        encoding: 'utf8',
        data: this.periodCsv(bundle.periods),
      };
    } else if (format === ExportFormatDto.DAILY_LOGS_CSV) {
      result = {
        filename: `lunara-daily-logs-${this.today()}.csv`,
        mediaType: 'text/csv; charset=utf-8',
        encoding: 'utf8',
        data: this.dailyLogCsv(bundle.dailyLogs),
      };
    } else if (format === ExportFormatDto.ENCRYPTED_JSON) {
      if (!dto.password) throw new BadRequestException('A password is required for encrypted export');
      result = {
        filename: `lunara-backup-${this.today()}.lunarabackup`,
        mediaType: 'application/vnd.lunara.encrypted+json',
        encoding: 'base64',
        data: this.encryptExport(JSON.stringify(bundle), dto.password),
      };
    } else {
      result = {
        filename: `lunara-backup-${this.today()}.json`,
        mediaType: 'application/json',
        encoding: 'utf8',
        data: bundle,
      };
    }
    await this.prisma.auditEvent.create({
      data: {
        householdId: user.householdId,
        actorUserId: user.userId,
        type: AuditEventType.DATA_EXPORTED,
        metadata: { format },
      },
    });
    return result;
  }

  async import(user: AuthenticatedUser, dto: ImportDto) {
    const periods = this.parsePeriods(dto.data.periods);
    const dailyLogs = this.parseDailyLogs(dto.data.dailyLogs);
    if (dto.preview !== false) {
      const [existingPeriods, existingLogs] = await Promise.all([
        this.prisma.periodEvent.findMany({
          where: { userId: user.userId },
          select: {
            id: true,
            startDate: true,
            endDate: true,
            deletedAt: true,
          },
        }),
        this.prisma.dailyLog.findMany({
          where: { userId: user.userId, deletedAt: null },
          select: { id: true, localDate: true },
        }),
      ]);
      const periodPlan = this.planPeriodImport(periods, existingPeriods);
      const logPlan = this.planDailyLogImport(dailyLogs, existingLogs);
      return {
        preview: true,
        ...this.importPreview(periods, dailyLogs, periodPlan, logPlan),
      };
    }

    try {
      const result = await this.prisma.$transaction(async (tx) => {
        // Preview is advisory. The authoritative plan is always rebuilt while
        // holding the same membership then per-user locks as normal mutations.
        // This prevents a stale tracker request from importing records after
        // invitation acceptance has moved that membership to PARTNER.
        await assertCurrentTrackerMembership(tx, user);
        await lockUserPeriods(tx, user.userId);
        const [existingPeriods, existingLogs] = await Promise.all([
          tx.periodEvent.findMany({
            where: { userId: user.userId },
            select: {
              id: true,
              startDate: true,
              endDate: true,
              deletedAt: true,
            },
          }),
          tx.dailyLog.findMany({
            where: { userId: user.userId, deletedAt: null },
            select: { id: true, localDate: true },
          }),
        ]);
        const periodPlan = this.planPeriodImport(periods, existingPeriods);
        const logPlan = this.planDailyLogImport(dailyLogs, existingLogs);
        const preview = this.importPreview(
          periods,
          dailyLogs,
          periodPlan,
          logPlan,
        );
        if (periodPlan.conflicts > 0) {
          throw new ConflictException({
            message:
              'Imported period ranges overlap existing or supplied active periods',
            conflicts: periodPlan.conflicts,
          });
        }

        const createdBatch = await tx.importBatch.create({
          data: {
            userId: user.userId,
            householdId: user.householdId,
            sourceFormat: 'LUNARA_JSON',
            recordsCreated: periodPlan.create.length + logPlan.create.length,
            recordsSkipped:
              periodPlan.duplicates + logPlan.duplicates,
            reversibleUntil: new Date(Date.now() + 24 * 60 * 60_000),
          },
        });
        for (const item of periodPlan.create) {
          const id = item.id ?? randomUUID();
          await tx.periodEvent.create({
            data: {
              id,
              userId: user.userId,
              startDate: this.parseDate(item.startDate),
              endDate: item.endDate ? this.parseDate(item.endDate) : null,
              source: PeriodSource.IMPORT,
            },
          });
          await this.importChange(tx, user, 'PeriodEvent', id);
        }
        for (const item of logPlan.create) {
          const id = item.id ?? randomUUID();
          await tx.dailyLog.create({
            data: {
              id,
              userId: user.userId,
              localDate: this.parseDate(item.localDate),
              flowLevel: item.flowLevel,
              painLevel: item.painLevel,
              mood: item.mood?.slice(0, 64),
              energy: item.energy,
              sleepQuality: item.sleepQuality,
              bodyTemperature: item.bodyTemperature,
              customSymptoms: item.customSymptoms ?? [],
              encryptedNote: item.note
                ? this.encryption.encrypt(item.note)
                : null,
            },
          });
          await this.importChange(tx, user, 'DailyLog', id);
        }
        if (periodPlan.create.length > 0) {
          await this.predictions.recalculateAndCache(user.userId, tx);
        }
        return { batch: createdBatch, preview };
      });
      return { preview: false, batchId: result.batch.id, ...result.preview };
    } catch (error) {
      if (isPeriodOverlapConstraintError(error)) {
        throw new ConflictException(
          `Import conflict: ${PERIOD_OVERLAP_MESSAGE}`,
        );
      }
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === 'P2002'
      ) {
        throw new ConflictException(
          'Import conflicts with data that already exists; refresh the preview and retry',
        );
      }
      throw error;
    }
  }

  async deleteAccount(user: AuthenticatedUser, dto: DeleteAccountDto): Promise<void> {
    const externalProof = dto.oidcAccessToken && this.oidcVerifier
      ? await this.oidcVerifier.verifyFreshAuthentication(dto.oidcAccessToken)
      : undefined;
    await this.prisma.$transaction(async (tx) => {
      await lockMembership(tx, user.membershipId, user.userId);
      const membership = await tx.membership.findUnique({ where: { id: user.membershipId } });
      if (!membership || membership.userId !== user.userId ||
          membership.householdId !== user.householdId || membership.role !== user.role) {
        throw new ForbiddenException('Account membership changed; sign in again');
      }
      await lockRelationship(tx, user.householdId);
      await lockUserAuthentication(tx, user.userId);
      const account = await tx.user.findUniqueOrThrow({ where: { id: user.userId } });
      if (account.disabledAt) throw new UnauthorizedException('Account is disabled');
      if (account.passwordHash) {
        // A linked OIDC identity never bypasses a configured local password.
        const valid = typeof dto.password === 'string' &&
          await argon2.verify(account.passwordHash, dto.password).catch(() => false);
        if (!valid) throw new UnauthorizedException('Password is incorrect');
      } else {
        if (!externalProof || dto.confirm !== `DELETE:${user.userId}`) {
          throw new UnauthorizedException('A fresh external sign-in and account confirmation are required');
        }
        const linked = await tx.externalIdentity.findUnique({
          where: { issuer_subject: { issuer: externalProof.issuer, subject: externalProof.subject } },
          select: { userId: true },
        });
        if (!linked || linked.userId !== user.userId) {
          throw new UnauthorizedException('External identity does not match this account');
        }
      }
      const session = await tx.deviceSession.findUnique({ where: { id: user.sessionId } });
      if (!session || session.userId !== user.userId || session.revokedAt || session.expiresAt <= new Date()) {
        throw new UnauthorizedException('Session is no longer active');
      }
      if (membership.role === UserRole.TRACKER && !dto.deleteHousehold) {
        throw new ConflictException('The tracker must delete the relationship or transfer the role first');
      }
      if (dto.deleteHousehold && membership.role !== UserRole.TRACKER) {
        throw new ForbiddenException('Only the tracker can delete the relationship');
      }
      if (dto.deleteHousehold) {
        // Invitation claims and relationship deletion share this lock. Member
        // IDs are re-read only after it, so a join that won the race is also
        // deleted and a waiting join observes the removed invitation.
        const members = await tx.membership.findMany({
          where: { householdId: user.householdId },
          select: { userId: true },
        });
        const memberUserIds = members.map((member) => member.userId);
        if (memberUserIds.length !== 1 || memberUserIds[0] !== user.userId) {
          throw new ConflictException('Shared relationships must be separated or the tracker role transferred before account deletion');
        }
        await tx.user.deleteMany({ where: { id: { in: memberUserIds } } });
        await tx.household.delete({ where: { id: user.householdId } });
        // The deleted household and users can no longer be referenced safely.
        // Keep only a deliberately FK-less, non-health-data deletion tombstone.
        await tx.auditEvent.create({
          data: {
            type: AuditEventType.ACCOUNT_DELETED,
            metadata: { householdDeleted: true, deletedUserCount: memberUserIds.length },
          },
        });
      } else {
        await tx.auditEvent.create({
          data: {
            householdId: user.householdId,
            actorUserId: user.userId,
            type: AuditEventType.ACCOUNT_DELETED,
            metadata: { householdDeleted: false },
          },
        });
        await tx.user.delete({ where: { id: user.userId } });
      }
    });
  }

  private async buildBundle(user: AuthenticatedUser) {
    const [account, profile, periods, logs, sharing, care] = await Promise.all([
      this.prisma.user.findUniqueOrThrow({
        where: { id: user.userId },
        select: { id: true, username: true, displayName: true, preferredLanguage: true, createdAt: true },
      }),
      this.prisma.cycleProfile.findUnique({ where: { userId: user.userId } }),
      this.prisma.periodEvent.findMany({ where: { userId: user.userId, deletedAt: null }, orderBy: { startDate: 'asc' } }),
      this.prisma.dailyLog.findMany({ where: { userId: user.userId, deletedAt: null }, orderBy: { localDate: 'asc' } }),
      this.prisma.sharingSettings.findUnique({
        where: { ownerUserId_householdId: { ownerUserId: user.userId, householdId: user.householdId } },
      }),
      this.prisma.carePreferences.findUnique({ where: { userId: user.userId } }),
    ]);
    return {
      format: 'lunara-backup',
      formatVersion: 1,
      exportedAt: new Date().toISOString(),
      account,
      cycleProfile: profile,
      sharingSettings: sharing,
      carePreferences: care,
      periods: periods.map((period) => ({
        id: period.id,
        startDate: this.date(period.startDate),
        endDate: period.endDate ? this.date(period.endDate) : null,
        source: period.source,
        createdAt: period.createdAt,
        updatedAt: period.updatedAt,
        version: period.version,
      })),
      dailyLogs: logs.map((log) => this.logs.serialize(log)),
    };
  }

  private importPreview(
    periods: ImportPeriod[],
    dailyLogs: ImportDailyLog[],
    periodPlan: PeriodImportPlan,
    logPlan: DailyLogImportPlan,
  ) {
    return {
      periods: {
        supplied: periods.length,
        create: periodPlan.create.length,
        duplicates: periodPlan.duplicates,
        conflicts: periodPlan.conflicts,
      },
      dailyLogs: {
        supplied: dailyLogs.length,
        create: logPlan.create.length,
        duplicates: logPlan.duplicates,
      },
    };
  }

  private planPeriodImport(
    periods: ImportPeriod[],
    stored: StoredPeriodRange[],
  ): PeriodImportPlan {
    const knownIds = new Set(stored.map((item) => item.id));
    const active = stored
      .filter((item) => item.deletedAt === null)
      .map((item) => ({
        startDate: this.date(item.startDate),
        endDate: item.endDate ? this.date(item.endDate) : null,
      }));
    const knownStarts = new Set(active.map((item) => item.startDate));
    const incomingIds = new Set<string>();
    const incomingStarts = new Set<string>();
    const create: ImportPeriod[] = [];
    let duplicates = 0;
    let conflicts = 0;

    for (const period of periods) {
      if (
        (period.id &&
          (knownIds.has(period.id) || incomingIds.has(period.id))) ||
        knownStarts.has(period.startDate) ||
        incomingStarts.has(period.startDate)
      ) {
        duplicates += 1;
        continue;
      }
      if (period.id) incomingIds.add(period.id);
      incomingStarts.add(period.startDate);
      if (active.some((existing) => this.periodRangesOverlap(existing, period))) {
        conflicts += 1;
        continue;
      }
      create.push(period);
      active.push({
        startDate: period.startDate,
        endDate: period.endDate ?? null,
      });
    }
    return { create, duplicates, conflicts };
  }

  private planDailyLogImport(
    logs: ImportDailyLog[],
    stored: Array<{ id: string; localDate: Date }>,
  ): DailyLogImportPlan {
    const knownIds = new Set(stored.map((item) => item.id));
    const knownDates = new Set(
      stored.map((item) => this.date(item.localDate)),
    );
    const create: ImportDailyLog[] = [];
    let duplicates = 0;
    for (const log of logs) {
      if (
        (log.id && knownIds.has(log.id)) ||
        knownDates.has(log.localDate)
      ) {
        duplicates += 1;
        continue;
      }
      if (log.id) knownIds.add(log.id);
      knownDates.add(log.localDate);
      create.push(log);
    }
    return { create, duplicates };
  }

  private periodRangesOverlap(
    left: { startDate: string; endDate?: string | null },
    right: { startDate: string; endDate?: string | null },
  ): boolean {
    return (
      right.startDate <= effectivePeriodEndLocalDate(left.startDate, left.endDate) &&
      left.startDate <= effectivePeriodEndLocalDate(right.startDate, right.endDate)
    );
  }

  private parsePeriods(value: unknown): ImportPeriod[] {
    if (value === undefined) return [];
    if (!Array.isArray(value) || value.length > 5_000) throw new BadRequestException('periods must be an array of at most 5000 items');
    return value.map((raw) => {
      if (!raw || typeof raw !== 'object') throw new BadRequestException('Invalid period item');
      const item = raw as Record<string, unknown>;
      const startDate = this.requiredDate(item.startDate, 'period.startDate');
      const endDate = item.endDate === null || item.endDate === undefined
        ? null
        : this.requiredDate(item.endDate, 'period.endDate');
      if (endDate && endDate < startDate) throw new BadRequestException('Period endDate precedes startDate');
      return { id: this.optionalUuid(item.id), startDate, endDate };
    });
  }

  private parseDailyLogs(value: unknown): ImportDailyLog[] {
    if (value === undefined) return [];
    if (!Array.isArray(value) || value.length > 20_000) throw new BadRequestException('dailyLogs must be an array of at most 20000 items');
    return value.map((raw) => {
      if (!raw || typeof raw !== 'object') throw new BadRequestException('Invalid daily log item');
      const item = raw as Record<string, unknown>;
      const note = this.optionalString(item.note, 'dailyLog.note');
      if (note && note.length > 4_000) throw new BadRequestException('Imported note exceeds 4000 characters');
      const symptoms = Array.isArray(item.customSymptoms)
        ? item.customSymptoms.map((symptom) => {
            if (typeof symptom !== 'string') throw new BadRequestException('Imported symptoms must be strings');
            return symptom;
          }).slice(0, 30)
        : [];
      if (symptoms.some((symptom) => symptom.length > 80)) throw new BadRequestException('Imported symptom exceeds 80 characters');
      return {
        id: this.optionalUuid(item.id),
        localDate: this.requiredDate(item.localDate, 'dailyLog.localDate'),
        flowLevel: this.flow(item.flowLevel),
        painLevel: this.boundedInteger(item.painLevel, 0, 10),
        mood: this.optionalString(item.mood, 'dailyLog.mood')?.slice(0, 64) ?? null,
        energy: this.boundedInteger(item.energy, 0, 10),
        sleepQuality: this.boundedInteger(item.sleepQuality, 0, 10),
        bodyTemperature: this.boundedNumber(item.bodyTemperature, 30, 45),
        customSymptoms: symptoms,
        note,
      };
    });
  }

  private encryptExport(plainText: string, password: string): string {
    const salt = randomBytes(16);
    const iv = randomBytes(12);
    const key = pbkdf2Sync(password, salt, 600_000, 32, 'sha256');
    const cipher = createCipheriv('aes-256-gcm', key, iv);
    const encrypted = Buffer.concat([cipher.update(plainText, 'utf8'), cipher.final()]);
    const tag = cipher.getAuthTag();
    return Buffer.concat([Buffer.from('LUNARA1'), salt, iv, tag, encrypted]).toString('base64');
  }

  private periodCsv(periods: Array<{ id: string; startDate: string; endDate: string | null; source: string }>): string {
    return [
      'id,startDate,endDate,source',
      ...periods.map((item) => [item.id, item.startDate, item.endDate ?? '', item.source].map(this.csv).join(',')),
    ].join('\r\n');
  }

  private dailyLogCsv(logs: Array<Record<string, unknown>>): string {
    const headers = ['id', 'localDate', 'flowLevel', 'painLevel', 'mood', 'energy', 'sleepQuality', 'bodyTemperature', 'customSymptoms', 'note'];
    return [
      headers.join(','),
      ...logs.map((item) => headers.map((key) => this.csv(Array.isArray(item[key]) ? JSON.stringify(item[key]) : item[key])).join(',')),
    ].join('\r\n');
  }

  private readonly csv = (value: unknown): string => {
    const raw =
      value == null
        ? ''
        : typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean'
          ? `${value}`
          : (JSON.stringify(value) ?? '');
    // Spreadsheet applications may execute cells beginning with formula
    // sigils even when the CSV field is quoted.
    const string = /^[=+\-@\t\r]/.test(raw) ? `'${raw}` : raw;
    return /[",\r\n]/.test(string) ? `"${string.replaceAll('"', '""')}"` : string;
  };

  private async importChange(
    tx: Prisma.TransactionClient,
    user: AuthenticatedUser,
    entityType: string,
    entityId: string,
  ): Promise<void> {
    await tx.syncChange.create({
      data: {
        householdId: user.householdId,
        ownerUserId: user.userId,
        entityType,
        entityId,
        operation: SyncOperation.UPSERT,
        entityVersion: 1,
      },
    });
  }

  private requiredDate(value: unknown, field: string): string {
    if (typeof value !== 'string') throw new BadRequestException(`${field} must be a local date`);
    this.parseDate(value);
    return value;
  }

  private parseDate(value: string): Date {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new BadRequestException(`Invalid local date: ${value}`);
    const date = new Date(`${value}T00:00:00.000Z`);
    if (Number.isNaN(date.valueOf()) || this.date(date) !== value) throw new BadRequestException(`Invalid local date: ${value}`);
    return date;
  }

  private optionalUuid(value: unknown): string | undefined {
    if (value === undefined || value === null) return undefined;
    if (typeof value !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)) {
      throw new BadRequestException('Invalid UUID in import');
    }
    return value;
  }

  private flow(value: unknown): ImportDailyLog['flowLevel'] {
    if (value === null || value === undefined) return null;
    if (typeof value !== 'string' || !['SPOTTING', 'LIGHT', 'MEDIUM', 'HEAVY'].includes(value)) {
      throw new BadRequestException('Invalid flowLevel');
    }
    return value as ImportDailyLog['flowLevel'];
  }

  private boundedInteger(value: unknown, min: number, max: number): number | null {
    if (value === null || value === undefined) return null;
    if (typeof value !== 'number' || !Number.isInteger(value) || value < min || value > max) throw new BadRequestException('Numeric import value is out of range');
    return value;
  }

  private boundedNumber(value: unknown, min: number, max: number): number | null {
    if (value === null || value === undefined) return null;
    if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max) throw new BadRequestException('Numeric import value is out of range');
    return value;
  }

  private optionalString(value: unknown, field: string): string | null {
    if (value === null || value === undefined) return null;
    if (typeof value !== 'string') throw new BadRequestException(`${field} must be a string`);
    return value;
  }

  private date(value: Date): string {
    return value.toISOString().slice(0, 10);
  }

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }
}
