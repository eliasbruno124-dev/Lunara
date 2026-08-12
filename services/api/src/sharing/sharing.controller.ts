import { Body, Controller, Get, Patch } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { UserRole } from '@prisma/client';
import { CurrentUser } from '../common/decorators/current-user.decorator.js';
import { Roles } from '../common/decorators/roles.decorator.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { UpdateSharingDto } from './sharing.dto.js';
import { SharingService } from './sharing.service.js';

@ApiTags('sharing')
@ApiBearerAuth()
@Roles(UserRole.TRACKER)
@Controller('api/v1/sharing')
export class SharingController {
  constructor(private readonly sharing: SharingService) {}

  @Get()
  get(@CurrentUser() user: AuthenticatedUser) {
    return this.sharing.get(user);
  }

  @Patch()
  update(@CurrentUser() user: AuthenticatedUser, @Body() dto: UpdateSharingDto) {
    return this.sharing.update(user, dto);
  }
}
