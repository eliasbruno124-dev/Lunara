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
  CreateUserDefinedTipDto,
  TipFeedbackDto,
  TodayTipQueryDto,
  UpdateUserDefinedTipDto,
  UserDefinedTipResponseDto,
} from './tips.dto.js';
import { TipsService } from './tips.service.js';

@ApiTags('partner tips')
@ApiBearerAuth()
@Roles(UserRole.PARTNER)
@Controller('api/v1/tips')
export class TipsController {
  constructor(private readonly tips: TipsService) {}

  @Get('today')
  today(@CurrentUser() user: AuthenticatedUser, @Query() query: TodayTipQueryDto) {
    return this.tips.today(user, query.variant);
  }

  @Post(':id/feedback')
  feedback(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) tipId: string,
    @Body() dto: TipFeedbackDto,
  ) {
    return this.tips.feedback(user, tipId, dto);
  }
}

@ApiTags('partner tips')
@ApiBearerAuth()
@Roles(UserRole.TRACKER)
@Controller('api/v1/tips/custom')
export class TrackerTipsController {
  constructor(private readonly tips: TipsService) {}

  @Get()
  @ApiQuery({ name: 'includeDisabled', required: false, type: Boolean })
  @ApiOkResponse({ type: UserDefinedTipResponseDto, isArray: true })
  list(
    @CurrentUser() user: AuthenticatedUser,
    @Query('includeDisabled', new DefaultValuePipe(false), ParseBoolPipe)
    includeDisabled: boolean,
  ) {
    return this.tips.listUserDefined(user, includeDisabled);
  }

  @Post()
  @ApiCreatedResponse({ type: UserDefinedTipResponseDto })
  create(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: CreateUserDefinedTipDto,
  ) {
    return this.tips.createUserDefined(user, dto);
  }

  @Patch(':id')
  @ApiOkResponse({ type: UserDefinedTipResponseDto })
  update(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateUserDefinedTipDto,
  ) {
    return this.tips.updateUserDefined(user, id, dto);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiNoContentResponse({ description: 'User-defined tip disabled' })
  disable(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.tips.disableUserDefined(user, id);
  }
}
