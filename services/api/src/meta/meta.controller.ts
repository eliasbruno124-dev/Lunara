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
  gatewayMode: 'direct' | 'authentik';
  setupRequired: boolean;
  registrationEnabled: boolean;
  serverTime: string;
  gatewayHeaderName: 'X-Lunara-Authorization';
  authenticationMethods: Array<'PASSWORD' | 'OIDC'>;
  primaryAuthenticationMethod: 'PASSWORD' | 'OIDC';
  oidc?: {
    providerName: string;
    issuer: string;
    audience: string;
    clientId: string;
    scopes: string[];
    discoveryUrl: string;
    enrollment: {
      registration: boolean;
      invitation: true;
      localPasswordRequired: false;
      createIfMissingRequired: true;
    };
  };
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
    const oidcEnabled = this.config.get<boolean>(
      'lunara.oidc.enabled',
      false,
    );
    const oidcIssuer = this.config.get<string>('lunara.oidc.issuer', '');
    const gatewayMode = this.config.get<'direct' | 'authentik'>(
      'lunara.gatewayMode',
      'direct',
    );
    const registrationEnabled = this.config.get<boolean>(
      'lunara.registrationEnabled',
      false,
    );
    const oidcPrimary = oidcEnabled && gatewayMode === 'authentik';
    return {
      name: this.config.get<string>('lunara.appName', 'Lunara'),
      version: this.config.get<string>('lunara.appVersion', '1.1.0'),
      apiVersion: 'v1',
      gatewayMode,
      setupRequired: !bootstrapped,
      registrationEnabled,
      serverTime: new Date().toISOString(),
      gatewayHeaderName: 'X-Lunara-Authorization',
      authenticationMethods: oidcEnabled
        ? oidcPrimary
          ? ['OIDC', 'PASSWORD']
          : ['PASSWORD', 'OIDC']
        : ['PASSWORD'],
      primaryAuthenticationMethod: oidcPrimary ? 'OIDC' : 'PASSWORD',
      ...(oidcEnabled
        ? {
            oidc: {
              providerName: this.config.get<string>(
                'lunara.oidc.providerName',
                'Authentik',
              ),
              issuer: oidcIssuer,
              audience: this.config.get<string>('lunara.oidc.audience', ''),
              clientId: this.config.get<string>('lunara.oidc.clientId', ''),
              scopes: this.config.get<string[]>(
                'lunara.oidc.scopes',
                ['openid', 'offline_access'],
              ),
              discoveryUrl: this.config.get<string>(
                'lunara.oidc.discoveryUrl',
                `${oidcIssuer.endsWith('/') ? oidcIssuer : `${oidcIssuer}/`}.well-known/openid-configuration`,
              ),
              enrollment: {
                registration: registrationEnabled,
                invitation: true,
                localPasswordRequired: false,
                createIfMissingRequired: true,
              },
            },
          }
        : {}),
    };
  }
}
