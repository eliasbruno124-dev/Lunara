import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseIntPipe,
  ParseUUIDPipe,
  Patch,
  Post,
  Query,
} from '@nestjs/common';
import { ApiBearerAuth, ApiQuery, ApiTags } from '@nestjs/swagger';
import { UserRole } from '@prisma/client';
import { CurrentUser } from '../common/decorators/current-user.decorator.js';
import { Roles } from '../common/decorators/roles.decorator.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { CreatePeriodDto, UpdatePeriodDto } from './periods.dto.js';
import { PeriodsService } from './periods.service.js';

@ApiTags('periods')
@ApiBearerAuth()
@Roles(UserRole.TRACKER)
@Controller('api/v1/periods')
export class PeriodsController {
  constructor(private readonly periods: PeriodsService) {}

  @Get()
  list(@CurrentUser() user: AuthenticatedUser) {
    return this.periods.list(user.userId);
  }

  @Post()
  create(@CurrentUser() user: AuthenticatedUser, @Body() dto: CreatePeriodDto) {
    return this.periods.create(user, dto);
  }

  @Patch(':id')
  update(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdatePeriodDto,
  ) {
    return this.periods.update(user, id, dto);
  }

  @HttpCode(HttpStatus.NO_CONTENT)
  @Delete(':id')
  @ApiQuery({ name: 'version', required: true, type: Number })
  async remove(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Query('version', ParseIntPipe) version: number,
  ): Promise<void> {
    await this.periods.remove(user, id, version);
  }
}
