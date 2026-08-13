import { Controller, Get, Header } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { isServerBootstrapped } from '../common/database/database-coordination.js';
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
  @Header('Cache-Control', 'no-store, max-age=0')
  @Header('Pragma', 'no-cache')
  @ApiOperation({ summary: 'Server identity, compatibility and setup state' })
  async getMeta(): Promise<MetaResponse> {
    const bootstrapped = await isServerBootstrapped(this.prisma);
    return {
      name: this.config.get<string>('lunara.appName', 'Lunara'),
      version: this.config.get<string>('lunara.appVersion', '1.0.3'),
      apiVersion: 'v1',
      setupRequired: !bootstrapped,
      registrationEnabled: this.config.get<boolean>(
        'lunara.registrationEnabled',
        false,
      ),
      serverTime: new Date().toISOString(),
      gatewayHeaderName: 'X-Lunara-Authorization',
    };
  }
}
