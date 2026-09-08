import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Post,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { UserRole } from '@prisma/client';
import { AuthResponse } from '../auth/auth.service.js';
import { CurrentUser } from '../common/decorators/current-user.decorator.js';
import { Public } from '../common/decorators/public.decorator.js';
import { Roles } from '../common/decorators/roles.decorator.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import {
  AcceptInvitationDto,
  CreateInvitationDto,
  JoinHouseholdDto,
} from './household.dto.js';
import { HouseholdService } from './household.service.js';

@ApiTags('household')
@Controller('api/v1/household')
export class HouseholdController {
  constructor(private readonly households: HouseholdService) {}

  @ApiBearerAuth()
  @Get()
  get(@CurrentUser() user: AuthenticatedUser) {
    return this.households.getHousehold(user);
  }

  @ApiBearerAuth()
  @Roles(UserRole.TRACKER)
  @Get('invitations')
  listInvitations(@CurrentUser() user: AuthenticatedUser) {
    return this.households.listOpenInvitations(user);
  }

  @ApiBearerAuth()
  @Roles(UserRole.TRACKER)
  @Throttle({ default: { limit: 3, ttl: 60_000 } })
  @Post('invitations')
  createInvitation(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: CreateInvitationDto,
  ) {
    return this.households.createInvitation(user, dto);
  }

  @ApiBearerAuth()
  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @HttpCode(HttpStatus.OK)
  @Post('invitations/accept')
  acceptInvitation(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: AcceptInvitationDto,
  ): Promise<{ accepted: true }> {
    return this.households.acceptInvitation(user, dto);
  }

  @ApiBearerAuth()
  @Roles(UserRole.TRACKER)
  @HttpCode(HttpStatus.NO_CONTENT)
  @Delete('invitations/:id')
  async revokeInvitation(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) invitationId: string,
  ): Promise<void> {
    await this.households.revokeInvitation(user, invitationId);
  }

  @Public()
  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @Post('join')
  join(@Body() dto: JoinHouseholdDto): Promise<AuthResponse> {
    return this.households.join(dto);
  }
}
