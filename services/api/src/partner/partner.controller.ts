import { Controller, Get } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { UserRole } from '@prisma/client';
import { CurrentUser } from '../common/decorators/current-user.decorator.js';
import { Roles } from '../common/decorators/roles.decorator.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PartnerService } from './partner.service.js';

@ApiTags('partner')
@ApiBearerAuth()
@Roles(UserRole.PARTNER)
@Controller('api/v1/partner')
export class PartnerController {
  constructor(private readonly partner: PartnerService) {}

  @Get('dashboard')
  dashboard(@CurrentUser() user: AuthenticatedUser) {
    return this.partner.dashboard(user);
  }
}
