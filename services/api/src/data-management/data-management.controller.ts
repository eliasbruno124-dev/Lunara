import { Body, Controller, Delete, HttpCode, HttpStatus, Post } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { UserRole } from '@prisma/client';
import { CurrentUser } from '../common/decorators/current-user.decorator.js';
import { Roles } from '../common/decorators/roles.decorator.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { DataManagementService } from './data-management.service.js';
import { DeleteAccountDto, ExportDto, ImportDto } from './data-management.dto.js';

@ApiTags('data management')
@ApiBearerAuth()
@Controller('api/v1')
export class DataManagementController {
  constructor(private readonly data: DataManagementService) {}

  @Roles(UserRole.TRACKER)
  @Post('export')
  export(@CurrentUser() user: AuthenticatedUser, @Body() dto: ExportDto) {
    return this.data.export(user, dto);
  }

  @Roles(UserRole.TRACKER)
  @Post('import')
  import(@CurrentUser() user: AuthenticatedUser, @Body() dto: ImportDto) {
    return this.data.import(user, dto);
  }

  @HttpCode(HttpStatus.NO_CONTENT)
  @Delete('account')
  deleteAccount(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: DeleteAccountDto,
  ): Promise<void> {
    return this.data.deleteAccount(user, dto);
  }
}
