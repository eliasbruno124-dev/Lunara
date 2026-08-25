import { Module } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { AuthController } from './auth.controller.js';
import { AuthService } from './auth.service.js';
import { OidcController } from './oidc.controller.js';
import { OidcService } from './oidc.service.js';
import { OidcTokenVerifier } from './oidc-token-verifier.js';

@Module({
  imports: [
    JwtModule.registerAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        secret: config.getOrThrow<string>('AUTH_SIGNING_KEY'),
        signOptions: { algorithm: 'HS512', issuer: 'lunara-api', audience: 'lunara-android' },
        verifyOptions: { algorithms: ['HS512'], issuer: 'lunara-api', audience: 'lunara-android' },
      }),
    }),
  ],
  controllers: [AuthController, OidcController],
  providers: [AuthService, OidcService, OidcTokenVerifier],
  exports: [AuthService, JwtModule],
})
export class AuthModule {}
