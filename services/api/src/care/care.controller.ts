import { Body, Controller, Get, NotFoundException, Patch } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { UserRole } from '@prisma/client';
import { CurrentUser } from '../common/decorators/current-user.decorator.js';
import { Roles } from '../common/decorators/roles.decorator.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PrismaService } from '../prisma/prisma.service.js';
import { UpdateCarePreferencesDto } from './care.dto.js';

@ApiTags('care preferences')
@ApiBearerAuth()
@Roles(UserRole.TRACKER)
@Controller('api/v1/care-preferences')
export class CareController {
  constructor(private readonly prisma: PrismaService) {}

  @Get()
  async get(@CurrentUser() user: AuthenticatedUser) {
    const preferences = await this.prisma.carePreferences.findUnique({
      where: { userId: user.userId },
    });
    if (!preferences) throw new NotFoundException('Care preferences not found');
    return preferences;
  }

  @Patch()
  update(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: UpdateCarePreferencesDto,
  ) {
    return this.prisma.carePreferences.update({
      where: { userId: user.userId },
      data: dto,
    });
  }
}
