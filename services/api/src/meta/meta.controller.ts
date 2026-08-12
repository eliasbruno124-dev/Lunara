import { Controller, Get } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { Public } from '../common/decorators/public.decorator.js';
import { PrismaService } from '../prisma/prisma.service.js';

export interface MetaResponse {
  name: string;
  version: string;
  apiVersion: 'v1';
  setupRequired: boolean;
  registrationEnabled: boolean;
  serverTime: string;
  gatewayHeaderName: 'X-Lunara-Authorization';
}

@ApiTags('meta')
@Controller('api/v1/meta')
export class MetaController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly config: ConfigService,
  ) {}

  @Public()
  @Get()
  @ApiOperation({ summary: 'Server identity, compatibility and setup state' })
  async getMeta(): Promise<MetaResponse> {
    const users = await this.prisma.user.count();
    return {
      name: this.config.get<string>('lunara.appName', 'Lunara'),
      version: this.config.get<string>('lunara.appVersion', '1.0.0'),
      apiVersion: 'v1',
      setupRequired: users === 0,
      registrationEnabled: this.config.get<boolean>('lunara.registrationEnabled', false),
      serverTime: new Date().toISOString(),
      gatewayHeaderName: 'X-Lunara-Authorization',
    };
  }
}
