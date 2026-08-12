import {
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Post,
  Body,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { CurrentUser } from '../common/decorators/current-user.decorator.js';
import { Public } from '../common/decorators/public.decorator.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { LoginDto, LogoutDto, RefreshDto, SetupDto } from './auth.dto.js';
import { AuthResponse, AuthService } from './auth.service.js';

@ApiTags('authentication')
@Controller('api/v1')
export class AuthController {
  constructor(private readonly auth: AuthService) {}

  @Public()
  @Throttle({ default: { limit: 3, ttl: 60_000 } })
  @Post('setup')
  @ApiOperation({ summary: 'Create the first household and TRACKER account' })
  setup(@Body() dto: SetupDto): Promise<AuthResponse> {
    return this.auth.setup(dto);
  }

  @Public()
  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @Post('auth/login')
  login(@Body() dto: LoginDto): Promise<AuthResponse> {
    return this.auth.login(dto);
  }

  @Public()
  @Throttle({ default: { limit: 10, ttl: 60_000 } })
  @Post('auth/refresh')
  refresh(@Body() dto: RefreshDto): Promise<AuthResponse> {
    return this.auth.refresh(dto.refreshToken);
  }

  @ApiBearerAuth()
  @HttpCode(HttpStatus.NO_CONTENT)
  @Post('auth/logout')
  async logout(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: LogoutDto,
  ): Promise<void> {
    await this.auth.logout(user, dto.refreshToken);
  }

  @ApiBearerAuth()
  @Get('auth/sessions')
  sessions(@CurrentUser() user: AuthenticatedUser) {
    return this.auth.listSessions(user.userId, user.sessionId);
  }

  @ApiBearerAuth()
  @HttpCode(HttpStatus.NO_CONTENT)
  @Delete('auth/sessions/:id')
  async revokeSession(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) sessionId: string,
  ): Promise<void> {
    await this.auth.revokeSession(user, sessionId);
  }
}
