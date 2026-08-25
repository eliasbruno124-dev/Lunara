import { Body, Controller, Get, Post } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { CurrentUser } from '../common/decorators/current-user.decorator.js';
import { Public } from '../common/decorators/public.decorator.js';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { OidcLinkDto, OidcLoginDto } from './auth.dto.js';
import { AuthResponse } from './auth.service.js';
import { OidcService, OidcStatusResponse } from './oidc.service.js';

@ApiTags('authentication')
@Controller('api/v1/auth/oidc')
export class OidcController {
  constructor(private readonly oidc: OidcService) {}

  @Public()
  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @Post('login')
  @ApiOperation({
    summary: 'Exchange a verified, explicitly linked OIDC identity for a Lunara session',
  })
  login(@Body() dto: OidcLoginDto): Promise<AuthResponse> {
    return this.oidc.login(dto);
  }

  @ApiBearerAuth()
  @Throttle({ default: { limit: 3, ttl: 60_000 } })
  @Post('link')
  @ApiOperation({
    summary: 'Link the signed-in Lunara account to the exact OIDC subject after password re-authentication',
  })
  link(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: OidcLinkDto,
  ): Promise<OidcStatusResponse> {
    return this.oidc.link(user, dto);
  }

  @ApiBearerAuth()
  @Get('status')
  @ApiOperation({ summary: 'Show external sign-in and local password status' })
  status(
    @CurrentUser() user: AuthenticatedUser,
  ): Promise<OidcStatusResponse> {
    return this.oidc.status(user.userId);
  }
}
