import { ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import { DailyLog, Prisma, SyncOperation } from '@prisma/client';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { EncryptionService } from '../common/security/encryption.service.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { CreateDailyLogDto, UpdateDailyLogDto } from './daily-logs.dto.js';

@Injectable()
export class DailyLogsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly encryption: EncryptionService,
  ) {}

  async list(userId: string, from?: string, to?: string) {
    const logs = await this.prisma.dailyLog.findMany({
      where: {
        userId,
        deletedAt: null,
        ...(from || to
          ? {
              localDate: {
                ...(from ? { gte: this.parseDate(from) } : {}),
                ...(to ? { lte: this.parseDate(to) } : {}),
              },
            }
          : {}),
      },
      orderBy: { localDate: 'desc' },
      take: 366,
    });
    return logs.map((log) => this.serialize(log));
  }

  async create(user: AuthenticatedUser, dto: CreateDailyLogDto) {
    const existing = await this.prisma.dailyLog.findUnique({ where: { id: dto.id } });
    if (existing) {
      if (existing.userId !== user.userId || !this.matchesCreateRequest(existing, dto)) {
        throw new ConflictException('Client identifier is in use for another daily log payload');
      }
      return this.serialize(existing);
    }
    const sameDate = await this.prisma.dailyLog.findUnique({
      where: { userId_localDate: { userId: user.userId, localDate: this.parseDate(dto.localDate) } },
    });
    if (sameDate) throw new ConflictException('A daily log already exists for this date');

    const log = await this.prisma.$transaction(async (tx) => {
      const created = await tx.dailyLog.create({ data: this.createData(user.userId, dto) });
      await this.recordChange(tx, user, created, SyncOperation.UPSERT);
      return created;
    });
    return this.serialize(log);
  }

  async update(user: AuthenticatedUser, id: string, dto: UpdateDailyLogDto) {
    if (dto.id !== id) throw new ConflictException('Body id must match path id');
    const updated = await this.prisma.$transaction(async (tx) => {
      const result = await tx.dailyLog.updateMany({
        where: { id, userId: user.userId, version: dto.version, deletedAt: null },
        data: {
          localDate: this.parseDate(dto.localDate),
          flowLevel: dto.flowLevel,
          painLevel: dto.painLevel,
          mood:
            dto.mood === undefined ? undefined : dto.mood?.trim() || null,
          energy: dto.energy,
          sleepQuality: dto.sleepQuality,
          bodyTemperature: dto.bodyTemperature,
          ovulationTestResult: dto.ovulationTestResult,
          customSymptoms: dto.customSymptoms,
          encryptedNote: dto.note === undefined ? undefined : dto.note ? this.encryption.encrypt(dto.note) : null,
          version: { increment: 1 },
        },
      });
      if (result.count !== 1) throw new ConflictException('Daily log changed on another device');
      const log = await tx.dailyLog.findUniqueOrThrow({ where: { id } });
      await this.recordChange(tx, user, log, SyncOperation.UPSERT);
      return log;
    });
    return this.serialize(updated);
  }

  async remove(user: AuthenticatedUser, id: string, version: number): Promise<void> {
    await this.prisma.$transaction(async (tx) => {
      const result = await tx.dailyLog.updateMany({
        where: { id, userId: user.userId, version, deletedAt: null },
        data: { deletedAt: new Date(), version: { increment: 1 } },
      });
      if (result.count !== 1) throw new ConflictException('Daily log changed or no longer exists');
      const log = await tx.dailyLog.findUniqueOrThrow({ where: { id } });
      await this.recordChange(tx, user, log, SyncOperation.DELETE);
    });
  }

  async findOwned(userId: string, id: string): Promise<DailyLog> {
    const log = await this.prisma.dailyLog.findFirst({ where: { id, userId, deletedAt: null } });
    if (!log) throw new NotFoundException('Daily log not found');
    return log;
  }

  serialize(log: DailyLog) {
    return {
      id: log.id,
      localDate: log.localDate.toISOString().slice(0, 10),
      flowLevel: log.flowLevel,
      painLevel: log.painLevel,
      mood: log.mood,
      energy: log.energy,
      sleepQuality: log.sleepQuality,
      bodyTemperature: log.bodyTemperature ? Number(log.bodyTemperature) : null,
      ovulationTestResult: log.ovulationTestResult,
      customSymptoms: log.customSymptoms,
      note: log.encryptedNote ? this.encryption.decrypt(log.encryptedNote) : null,
      createdAt: log.createdAt,
      updatedAt: log.updatedAt,
      deletedAt: log.deletedAt,
      version: log.version,
    };
  }

  private createData(userId: string, dto: CreateDailyLogDto): Prisma.DailyLogUncheckedCreateInput {
    return {
      id: dto.id,
      userId,
      localDate: this.parseDate(dto.localDate),
      flowLevel: dto.flowLevel,
      painLevel: dto.painLevel,
      mood: dto.mood?.trim() || null,
      energy: dto.energy,
      sleepQuality: dto.sleepQuality,
      bodyTemperature: dto.bodyTemperature,
      ovulationTestResult: dto.ovulationTestResult,
      customSymptoms: dto.customSymptoms ?? [],
      encryptedNote: dto.note ? this.encryption.encrypt(dto.note) : null,
    };
  }

  private matchesCreateRequest(log: DailyLog, dto: CreateDailyLogDto): boolean {
    const symptoms = Array.isArray(log.customSymptoms) ? log.customSymptoms : [];
    const requestedSymptoms = dto.customSymptoms ?? [];
    const sameSymptoms =
      symptoms.length === requestedSymptoms.length &&
      symptoms.every((value, index) => value === requestedSymptoms[index]);
    return (
      log.localDate.toISOString().slice(0, 10) === dto.localDate &&
      log.flowLevel === (dto.flowLevel ?? null) &&
      log.painLevel === (dto.painLevel ?? null) &&
      log.mood === (dto.mood?.trim() || null) &&
      log.energy === (dto.energy ?? null) &&
      log.sleepQuality === (dto.sleepQuality ?? null) &&
      (log.bodyTemperature === null ? null : Number(log.bodyTemperature)) ===
        (dto.bodyTemperature ?? null) &&
      log.ovulationTestResult === (dto.ovulationTestResult ?? null) &&
      sameSymptoms &&
      (log.encryptedNote ? this.encryption.decrypt(log.encryptedNote) : null) === (dto.note || null)
    );
  }

  private async recordChange(
    tx: Prisma.TransactionClient,
    user: AuthenticatedUser,
    log: DailyLog,
    operation: SyncOperation,
  ): Promise<void> {
    await tx.syncChange.create({
      data: {
        householdId: user.householdId,
        ownerUserId: user.userId,
        entityType: 'DailyLog',
        entityId: log.id,
        operation,
        entityVersion: log.version,
      },
    });
  }

  private parseDate(value: string): Date {
    const date = new Date(`${value}T00:00:00.000Z`);
    if (Number.isNaN(date.valueOf()) || date.toISOString().slice(0, 10) !== value) {
      throw new ConflictException(`Invalid local date: ${value}`);
    }
    return date;
  }
}
