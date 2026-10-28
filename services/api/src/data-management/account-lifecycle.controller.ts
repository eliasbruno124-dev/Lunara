import {
  Body, ConflictException, Controller, Headers, Injectable, Post,
  ServiceUnavailableException, UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AuditEventType, UserRole } from '@prisma/client';
import { Throttle } from '@nestjs/throttler';
import { ApiExcludeController } from '@nestjs/swagger';
import { IsOptional, IsString, IsUUID, Length, MaxLength } from 'class-validator';
import { readFileSync } from 'node:fs';
import { timingSafeEqual } from 'node:crypto';
import { Public } from '../common/decorators/public.decorator.js';
import { lockMembership, lockOidcIdentity, lockRelationship, lockUserAuthentication } from '../common/database/database-coordination.js';
import { PrismaService } from '../prisma/prisma.service.js';

export class LifecycleIdentityDto {
  @IsString() @Length(1, 512) issuer!: string;
  @IsString() @Length(1, 255) subject!: string;
  @IsOptional() @IsUUID() targetUserId?: string;
}

export class LifecycleDeleteDto extends LifecycleIdentityDto {
  @IsUUID() declare targetUserId: string;
  @IsString() @MaxLength(80) confirm!: string;
}

@Injectable()
export class AccountLifecycleService {
  constructor(private readonly prisma: PrismaService, private readonly config: ConfigService) {}

  authorize(key: string | undefined, dto: LifecycleIdentityDto): void {
    let expected: Buffer;
    try {
      expected = Buffer.from(readFileSync(this.config.get<string>('ACCOUNT_LIFECYCLE_KEY_FILE', '/run/secrets/lunara-lifecycle.key'), 'utf8').trim());
    } catch {
      throw new ServiceUnavailableException('Account lifecycle is unavailable');
    }
    if (expected.length < 32 || expected.length > 128) {
      throw new ServiceUnavailableException('Account lifecycle is unavailable');
    }
    // Padding equalizes lengths before constant-time comparison. The bounded
    // header also prevents attacker-controlled allocation of large buffers.
    const supplied = Buffer.from(typeof key === 'string' && key.length <= 128 ? key : '');
    const padded = Buffer.alloc(expected.length);
    supplied.copy(padded, 0, 0, expected.length);
    if (!timingSafeEqual(expected, padded) || supplied.length !== expected.length ||
        !this.config.get<string>('lunara.oidc.issuer', '') ||
        dto.issuer !== this.config.get<string>('lunara.oidc.issuer', '') ||
        typeof dto.subject !== 'string' || !dto.subject || dto.subject.length > 255 || dto.subject !== dto.subject.trim() ||
        Array.from(dto.subject).some((character) => character.charCodeAt(0) <= 31 || character.charCodeAt(0) === 127)) {
      throw new UnauthorizedException('Account lifecycle authentication failed');
    }
  }

  async preview(dto: LifecycleIdentityDto) {
    const linked = await this.prisma.externalIdentity.findUnique({
      where: { issuer_subject: { issuer: dto.issuer, subject: dto.subject } },
      select: { userId: true },
    });
    if (dto.targetUserId && linked && linked.userId !== dto.targetUserId) {
      throw new ConflictException('Immutable identity binding changed');
    }
    if (dto.targetUserId && !linked) {
      const retained = await this.prisma.user.findUnique({ where: { id: dto.targetUserId }, select: { id: true } });
      if (retained) throw new ConflictException('External identity binding is missing; owner reconciliation required');
    }
    // No account listing, household ID, member data, email or health data.
    return { account: linked ? { id: linked.userId } : null };
  }

  async delete(dto: LifecycleDeleteDto) {
    if (!dto.targetUserId || dto.confirm !== `DELETE:${dto.targetUserId}`) {
      throw new ConflictException('Immutable account confirmation required');
    }
    return this.prisma.$transaction(async (tx) => {
      await lockOidcIdentity(tx, dto.issuer, dto.subject);
      const linked = await tx.externalIdentity.findUnique({
        where: { issuer_subject: { issuer: dto.issuer, subject: dto.subject } },
        select: { userId: true },
      });
      if (linked && linked.userId !== dto.targetUserId) throw new ConflictException('Immutable identity binding changed');
      const account = await tx.user.findUnique({ where: { id: dto.targetUserId }, select: { id: true } });
      if (!account && !linked) return { deleted: false, gone: true };
      if (!linked || !account) throw new ConflictException('External identity binding is missing');
      const initial = await tx.membership.findUnique({ where: { userId: dto.targetUserId } });
      let householdDeleted = false;
      if (initial) {
        await lockMembership(tx, initial.id, dto.targetUserId);
        const membership = await tx.membership.findUnique({ where: { userId: dto.targetUserId } });
        if (!membership || membership.householdId !== initial.householdId || membership.role !== initial.role) {
          throw new ConflictException('Membership changed; retry after reconciliation');
        }
        await lockRelationship(tx, membership.householdId);
        await lockUserAuthentication(tx, dto.targetUserId);
        if (membership.role === UserRole.TRACKER) {
          const members = await tx.membership.findMany({ where: { householdId: membership.householdId }, select: { userId: true } });
          if (members.length !== 1 || members[0]?.userId !== dto.targetUserId) {
            throw new ConflictException('Shared household: separate members or transfer tracker first');
          }
          householdDeleted = true;
        }
      } else {
        await lockUserAuthentication(tx, dto.targetUserId);
      }
      await tx.user.delete({ where: { id: dto.targetUserId } });
      if (initial && householdDeleted) await tx.household.delete({ where: { id: initial.householdId } });
      await tx.auditEvent.create({ data: { type: AuditEventType.ACCOUNT_DELETED, metadata: { administrative: true, householdDeleted, deletedUserCount: 1 } } });
      return { deleted: true, gone: true };
    });
  }
}

@ApiExcludeController()
@Public()
@Throttle({ default: { limit: 60, ttl: 60_000 } })
@Controller('api/v1/internal/account-lifecycle')
export class AccountLifecycleController {
  constructor(private readonly lifecycle: AccountLifecycleService) {}

  @Post('preview')
  preview(@Headers('x-lunara-lifecycle-key') key: string | undefined, @Body() dto: LifecycleIdentityDto) {
    this.lifecycle.authorize(key, dto);
    return this.lifecycle.preview(dto);
  }

  @Post('delete')
  delete(@Headers('x-lunara-lifecycle-key') key: string | undefined, @Body() dto: LifecycleDeleteDto) {
    this.lifecycle.authorize(key, dto);
    return this.lifecycle.delete(dto);
  }
}
