import { Body, Controller, Get, Patch } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { UserRole } from '@prisma/client';
import { CurrentUser } from '../common/decorators/current-user.decorator.js';
import { Roles } from '../common/decorators/roles.decorator.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { UpdateCycleProfileDto } from './prediction.dto.js';
import { PredictionService } from './prediction.service.js';

@ApiTags('cycle')
@ApiBearerAuth()
@Roles(UserRole.TRACKER)
@Controller('api/v1')
export class PredictionController {
  constructor(private readonly predictions: PredictionService) {}

  @Get('cycle/profile')
  getProfile(@CurrentUser() user: AuthenticatedUser) {
    return this.predictions.getProfile(user.userId);
  }

  @Patch('cycle/profile')
  updateProfile(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: UpdateCycleProfileDto,
  ) {
    return this.predictions.updateProfile(user, dto);
  }

  @Get('predictions')
  getPrediction(@CurrentUser() user: AuthenticatedUser) {
    return this.predictions.calculateForUser(user.userId);
  }
}
