import 'reflect-metadata';
import { timingSafeEqual } from 'node:crypto';
import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { SwaggerModule, DocumentBuilder } from '@nestjs/swagger';
import helmet from 'helmet';
import type { NextFunction, Request, Response } from 'express';
import type { Express } from 'express';
import { AppModule } from './app.module.js';
import { installWebApp } from './web/web-app.js';
import { installInvitationLanding } from './web/invitation-landing.js';

function safeEqual(left: string, right: string): boolean {
  const a = Buffer.from(left);
  const b = Buffer.from(right);
  return a.length === b.length && timingSafeEqual(a, b);
}

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule, { bufferLogs: true });
  app.enableShutdownHooks();
  const express = app.getHttpAdapter().getInstance() as Express;
  express.disable('x-powered-by');
  express.set('trust proxy', 1);
  express.use('/api', (request: Request, response: Response, next: NextFunction) => {
    const declaredLength = Number(request.header('content-length') ?? '0');
    if (Number.isFinite(declaredLength) && declaredLength > 1_048_576) {
      response.status(413).json({ statusCode: 413, message: 'Request body is too large' });
      return;
    }
    next();
  });
  const oidcConnectOrigin = (() => {
    if (process.env.OIDC_ENABLED !== 'true' || !process.env.OIDC_ISSUER) return null;
    try {
      return new URL(process.env.OIDC_ISSUER).origin;
    } catch {
      return null;
    }
  })();
  const webHeaders = helmet({
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        baseUri: ["'self'"],
        connectSrc: ["'self'", ...(oidcConnectOrigin ? [oidcConnectOrigin] : [])],
        fontSrc: ["'self'"],
        formAction: ["'self'"],
        frameAncestors: ["'none'"],
        imgSrc: ["'self'", 'data:'],
        manifestSrc: ["'self'"],
        objectSrc: ["'none'"],
        scriptSrc: ["'self'"],
        styleSrc: ["'self'"],
        workerSrc: ["'self'"],
        ...(process.env.NODE_ENV === 'production' ? { upgradeInsecureRequests: [] } : {}),
      },
    },
  });
  const openApiHeaders = helmet({ contentSecurityPolicy: false });
  app.use((request: Request, response: Response, next: NextFunction) => {
    const isOpenApi =
      process.env.OPENAPI_ENABLED === 'true' &&
      (request.path === '/api-docs' || request.path.startsWith('/api-docs/'));
    return (isOpenApi ? openApiHeaders : webHeaders)(request, response, next);
  });
  installInvitationLanding(express);
  installWebApp(express, process.env.WEB_DIST_DIR);
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      transformOptions: { enableImplicitConversion: false },
    }),
  );

  const origins = (process.env.CORS_ORIGINS || '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
  app.enableCors({
    origin: origins.length > 0 ? origins : false,
    credentials: false,
    methods: ['GET', 'POST', 'PATCH', 'DELETE'],
    allowedHeaders: [
      'Authorization',
      'X-Lunara-Authorization',
      'Content-Type',
      'Idempotency-Key',
    ],
  });

  if (process.env.OPENAPI_ENABLED === 'true') {
    const trustAuthentik = process.env.OPENAPI_TRUST_AUTHENTIK === 'true';
    const docsUser = process.env.OPENAPI_USERNAME ?? '';
    const docsPassword = process.env.OPENAPI_PASSWORD ?? '';
    if (!trustAuthentik && (!docsUser || docsPassword.length < 16)) {
      throw new Error(
        'Protected OpenAPI requires Authentik mode or OPENAPI_USERNAME and a 16+ character OPENAPI_PASSWORD',
      );
    }
    app.use(
      ['/api-docs', '/api-docs-json'],
      (request: Request, response: Response, next: NextFunction) => {
        if (trustAuthentik) {
          // The API has no published host port and this header is injected by
          // the sole external path: the Authentik proxy outpost. Requiring it
          // prevents accidental exposure if the outer application policy is
          // misconfigured while avoiding a second Authorization header that
          // would conflict with Authentik header authentication.
          if (!request.header('x-authentik-uid')) {
            response.status(401).json({ statusCode: 401, message: 'OpenAPI authentication required' });
            return;
          }
          next();
          return;
        }
        const header = request.headers.authorization;
        const encoded = header?.match(/^Basic\s+(.+)$/i)?.[1];
        const credentials = encoded ? Buffer.from(encoded, 'base64').toString('utf8') : '';
        const separator = credentials.indexOf(':');
        const user = separator >= 0 ? credentials.slice(0, separator) : '';
        const password = separator >= 0 ? credentials.slice(separator + 1) : '';
        if (!safeEqual(user, docsUser) || !safeEqual(password, docsPassword)) {
          response.setHeader('WWW-Authenticate', 'Basic realm="Lunara OpenAPI"');
          response.status(401).end();
          return;
        }
        next();
      },
    );
    const document = SwaggerModule.createDocument(
      app,
      new DocumentBuilder()
        .setTitle(process.env.APP_NAME || 'Lunara')
        .setVersion(process.env.APP_VERSION || '1.1.0')
        .addBearerAuth()
        .addApiKey({ type: 'apiKey', in: 'header', name: 'X-Lunara-Authorization' }, 'gatewayBearer')
        .build(),
    );
    SwaggerModule.setup('api-docs', app, document, { jsonDocumentUrl: 'api-docs-json' });
  }

  await app.listen(Number(process.env.PORT || 3000), '0.0.0.0');
}

void bootstrap();
