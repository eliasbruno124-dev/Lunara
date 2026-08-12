import {
  Body,
  Controller,
  DefaultValuePipe,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseBoolPipe,
  ParseUUIDPipe,
  Patch,
  Post,
  Query,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiCreatedResponse,
  ApiNoContentResponse,
  ApiOkResponse,
  ApiQuery,
  ApiTags,
} from '@nestjs/swagger';
import { UserRole } from '@prisma/client';
import { CurrentUser } from '../common/decorators/current-user.decorator.js';
import { Roles } from '../common/decorators/roles.decorator.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import {
  CreateCustomSymptomDto,
  CustomSymptomResponseDto,
  SymptomCatalogResponseDto,
  UpdateCustomSymptomDto,
} from './symptoms.dto.js';
import { SymptomsService } from './symptoms.service.js';

@ApiTags('symptoms')
@ApiBearerAuth()
@Roles(UserRole.TRACKER)
@Controller('api/v1/symptoms')
export class SymptomsController {
  constructor(private readonly symptoms: SymptomsService) {}

  @Get()
  @ApiQuery({ name: 'includeArchived', required: false, type: Boolean })
  @ApiOkResponse({ type: SymptomCatalogResponseDto })
  list(
    @CurrentUser() user: AuthenticatedUser,
    @Query('includeArchived', new DefaultValuePipe(false), ParseBoolPipe)
    includeArchived: boolean,
  ) {
    return this.symptoms.list(user.userId, includeArchived);
  }

  @Post()
  @ApiCreatedResponse({ type: CustomSymptomResponseDto })
  create(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: CreateCustomSymptomDto,
  ) {
    return this.symptoms.create(user, dto);
  }

  @Patch(':id')
  @ApiOkResponse({ type: CustomSymptomResponseDto })
  update(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateCustomSymptomDto,
  ) {
    return this.symptoms.update(user, id, dto);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiNoContentResponse({ description: 'Custom symptom archived' })
  archive(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.symptoms.archive(user, id);
  }
}
