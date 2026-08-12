import { Body, Controller, Get, Post, Query } from '@nestjs/common';
import { ApiBearerAuth, ApiQuery, ApiTags } from '@nestjs/swagger';
import { UserRole } from '@prisma/client';
import { CurrentUser } from '../common/decorators/current-user.decorator.js';
import { Roles } from '../common/decorators/roles.decorator.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { SyncRequestDto } from './sync.dto.js';
import { SyncService } from './sync.service.js';

@ApiTags('sync')
@ApiBearerAuth()
@Controller('api/v1/sync')
export class SyncController {
  constructor(private readonly sync: SyncService) {}

  @Get()
  @ApiQuery({ name: 'cursor', required: false, type: String })
  pull(@CurrentUser() user: AuthenticatedUser, @Query('cursor') cursor?: string) {
    return this.sync.pull(user, cursor ?? '0');
  }

  @Roles(UserRole.TRACKER)
  @Post()
  push(@CurrentUser() user: AuthenticatedUser, @Body() dto: SyncRequestDto) {
    return this.sync.push(user, dto);
  }
}
