import { Controller, MessageEvent, Sse } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { UserRole } from '@prisma/client';
import { Observable, distinctUntilChanged, exhaustMap, from, interval, map, startWith } from 'rxjs';
import { CurrentUser } from '../common/decorators/current-user.decorator.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PrismaService } from '../prisma/prisma.service.js';

@ApiTags('realtime')
@ApiBearerAuth()
@Controller('api/v1/events')
export class RealtimeController {
  constructor(private readonly prisma: PrismaService) {}

  @Sse()
  stream(@CurrentUser() user: AuthenticatedUser): Observable<MessageEvent> {
    return interval(3_000).pipe(
      startWith(0),
      exhaustMap(() => from(this.latestVisibleCursor(user))),
      distinctUntilChanged(),
      map((cursor) => ({
        type: 'sync-available',
        id: cursor,
        retry: 5_000,
        // Only a cursor is emitted. Health details remain behind sharing filters
        // and are fetched through /sync or /partner/dashboard.
        data: { cursor },
      })),
    );
  }

  private async latestVisibleCursor(user: AuthenticatedUser): Promise<string> {
    const latest = await this.prisma.syncChange.findFirst({
      where:
        user.role === UserRole.TRACKER
          ? { householdId: user.householdId, ownerUserId: user.userId }
          : { householdId: user.householdId },
      orderBy: { cursor: 'desc' },
      select: { cursor: true },
    });
    return (latest?.cursor ?? 0n).toString();
  }
}
