import {
  Body,
  ConflictException,
  Controller,
  Get,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Post,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { CheckInStatus, UserRole } from '@prisma/client';
import { CurrentUser } from '../common/decorators/current-user.decorator.js';
import { Roles } from '../common/decorators/roles.decorator.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { CreateCheckInDto, RespondCheckInDto } from './check-ins.dto.js';

@ApiTags('check-ins')
@ApiBearerAuth()
@Controller('api/v1/check-ins')
export class CheckInsController {
  constructor(private readonly prisma: PrismaService) {}

  @Get()
  list(@CurrentUser() user: AuthenticatedUser) {
    return this.prisma.checkIn.findMany({
      where: {
        householdId: user.householdId,
        OR: [{ senderUserId: user.userId }, { recipientUserId: user.userId }],
        expiresAt: { gt: new Date() },
      },
      orderBy: { createdAt: 'desc' },
      take: 20,
    });
  }

  @Roles(UserRole.PARTNER)
  @Post()
  async create(@CurrentUser() user: AuthenticatedUser, @Body() dto: CreateCheckInDto) {
    const tracker = await this.prisma.membership.findFirst({
      where: { householdId: user.householdId, role: UserRole.TRACKER },
      select: { userId: true },
    });
    if (!tracker) throw new NotFoundException('Tracker account not found');
    const settings = await this.prisma.sharingSettings.findUnique({
      where: {
        ownerUserId_householdId: {
          ownerUserId: tracker.userId,
          householdId: user.householdId,
        },
      },
    });
    if (!settings?.checkInsEnabled) throw new ConflictException('Check-ins are disabled');
    const recent = await this.prisma.checkIn.count({
      where: {
        householdId: user.householdId,
        senderUserId: user.userId,
        createdAt: { gt: new Date(Date.now() - 6 * 60 * 60_000) },
        status: CheckInStatus.PENDING,
      },
    });
    if (recent > 0) throw new ConflictException('A check-in is already pending');
    return this.prisma.checkIn.create({
      data: {
        householdId: user.householdId,
        senderUserId: user.userId,
        recipientUserId: tracker.userId,
        prompt: dto.prompt?.trim() || 'Wie kann ich dich gerade unterstützen?',
        expiresAt: new Date(Date.now() + 12 * 60 * 60_000),
      },
    });
  }

  @Roles(UserRole.TRACKER)
  @Post(':id/respond')
  async respond(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: RespondCheckInDto,
  ) {
    const result = await this.prisma.checkIn.updateMany({
      where: {
        id,
        householdId: user.householdId,
        recipientUserId: user.userId,
        status: CheckInStatus.PENDING,
        expiresAt: { gt: new Date() },
      },
      data: {
        response: dto.response.trim(),
        status: CheckInStatus.ANSWERED,
        answeredAt: new Date(),
      },
    });
    if (result.count !== 1) throw new NotFoundException('Active check-in not found');
    return this.prisma.checkIn.findUniqueOrThrow({ where: { id } });
  }
}
