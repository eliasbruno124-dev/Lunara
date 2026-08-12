import {
  BadRequestException,
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { CustomSymptom, Prisma } from '@prisma/client';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { PREDEFINED_SYMPTOMS } from './symptom-catalog.js';
import { CreateCustomSymptomDto, UpdateCustomSymptomDto } from './symptoms.dto.js';

@Injectable()
export class SymptomsService {
  constructor(private readonly prisma: PrismaService) {}

  async list(userId: string, includeArchived: boolean) {
    const custom = await this.prisma.customSymptom.findMany({
      where: { userId, ...(includeArchived ? {} : { archivedAt: null }) },
      orderBy: [{ archivedAt: 'asc' }, { label: 'asc' }, { id: 'asc' }],
    });
    return {
      predefined: PREDEFINED_SYMPTOMS,
      custom: custom.map((symptom) => this.serialize(symptom)),
    };
  }

  async create(user: AuthenticatedUser, dto: CreateCustomSymptomDto) {
    const label = this.normalizeLabel(dto.label);
    this.rejectPredefinedLabel(label);
    const existingId = await this.prisma.customSymptom.findUnique({ where: { id: dto.id } });
    if (existingId) {
      if (existingId.userId !== user.userId || existingId.label !== label) {
        throw new ConflictException('Client identifier is already in use');
      }
      return this.serialize(existingId);
    }
    await this.rejectDuplicate(user.userId, label);
    try {
      const symptom = await this.prisma.customSymptom.create({
        data: { id: dto.id, userId: user.userId, label },
      });
      return this.serialize(symptom);
    } catch (error) {
      if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002') {
        throw new ConflictException('A custom symptom with this label or identifier already exists');
      }
      throw error;
    }
  }

  async update(user: AuthenticatedUser, id: string, dto: UpdateCustomSymptomDto) {
    if (dto.label === undefined && dto.archived === undefined) {
      throw new BadRequestException('At least one custom symptom change is required');
    }
    const current = await this.prisma.customSymptom.findFirst({
      where: { id, userId: user.userId },
    });
    if (!current) throw new NotFoundException('Custom symptom not found');

    const label = dto.label === undefined ? current.label : this.normalizeLabel(dto.label);
    this.rejectPredefinedLabel(label);
    if (label !== current.label) await this.rejectDuplicate(user.userId, label, id);
    try {
      const symptom = await this.prisma.customSymptom.update({
        where: { id },
        data: {
          label,
          ...(dto.archived === undefined
            ? {}
            : { archivedAt: dto.archived ? new Date() : null }),
        },
      });
      return this.serialize(symptom);
    } catch (error) {
      if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002') {
        throw new ConflictException('A custom symptom with this label already exists');
      }
      throw error;
    }
  }

  async archive(user: AuthenticatedUser, id: string): Promise<void> {
    const result = await this.prisma.customSymptom.updateMany({
      where: { id, userId: user.userId, archivedAt: null },
      data: { archivedAt: new Date() },
    });
    if (result.count === 1) return;
    const owned = await this.prisma.customSymptom.count({
      where: { id, userId: user.userId },
    });
    if (owned === 0) throw new NotFoundException('Custom symptom not found');
    // Archiving is intentionally idempotent.
  }

  private async rejectDuplicate(userId: string, label: string, exceptId?: string): Promise<void> {
    const duplicate = await this.prisma.customSymptom.findFirst({
      where: {
        userId,
        label: { equals: label, mode: 'insensitive' },
        ...(exceptId ? { id: { not: exceptId } } : {}),
      },
      select: { id: true },
    });
    if (duplicate) throw new ConflictException('A custom symptom with this label already exists');
  }

  private rejectPredefinedLabel(label: string): void {
    const normalized = label.toLocaleLowerCase('de-DE');
    if (
      PREDEFINED_SYMPTOMS.some(
        (item) =>
          item.labelDe.toLocaleLowerCase('de-DE') === normalized ||
          item.labelEn.toLocaleLowerCase('en-US') === normalized,
      )
    ) {
      throw new ConflictException('This symptom is already available in the predefined catalogue');
    }
  }

  private normalizeLabel(value: string): string {
    const normalized = value.trim().replace(/\s+/g, ' ');
    if (normalized.length < 1 || normalized.length > 80) {
      throw new BadRequestException('Custom symptom label must contain 1 to 80 characters');
    }
    return normalized;
  }

  private serialize(symptom: CustomSymptom) {
    return {
      id: symptom.id,
      label: symptom.label,
      archivedAt: symptom.archivedAt,
      createdAt: symptom.createdAt,
      updatedAt: symptom.updatedAt,
    };
  }
}
