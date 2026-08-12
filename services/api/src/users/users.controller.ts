import { Body, Controller, Get, Patch } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../common/decorators/current-user.decorator.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { UpdateMeDto } from './users.dto.js';

@ApiTags('account')
@ApiBearerAuth()
@Controller('api/v1/me')
export class UsersController {
  constructor(private readonly prisma: PrismaService) {}

  @Get()
  async get(@CurrentUser() current: AuthenticatedUser) {
    const membership = await this.prisma.membership.findUniqueOrThrow({
      where: { id: current.membershipId },
      include: {
        user: {
          select: {
            id: true,
            username: true,
            displayName: true,
            preferredLanguage: true,
            createdAt: true,
            updatedAt: true,
          },
        },
      },
    });
    return {
      ...membership.user,
      householdId: membership.householdId,
      role: membership.role,
      customRoleLabel: membership.customRoleLabel,
    };
  }

  @Patch()
  async update(@CurrentUser() current: AuthenticatedUser, @Body() dto: UpdateMeDto) {
    await this.prisma.$transaction([
      this.prisma.user.update({
        where: { id: current.userId },
        data: {
          displayName: dto.displayName?.trim(),
          preferredLanguage: dto.preferredLanguage,
        },
      }),
      this.prisma.membership.update({
        where: { id: current.membershipId },
        data: { customRoleLabel: dto.customRoleLabel?.trim() },
      }),
    ]);
    return this.get(current);
  }
}
