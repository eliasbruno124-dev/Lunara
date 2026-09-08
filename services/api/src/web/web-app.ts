import { existsSync } from 'node:fs';
import { extname, resolve } from 'node:path';
import express, { type Express, type NextFunction, type Request, type Response } from 'express';

const RESERVED_PATHS = [
  '/api',
  '/health',
  '/ready',
  '/api-docs',
  '/api-docs-json',
  '/.well-known',
  '/invite',
] as const;

function isReservedPath(pathname: string): boolean {
  return RESERVED_PATHS.some(
    (reserved) => pathname === reserved || pathname.startsWith(`${reserved}/`),
  );
}

export function isWebNavigation(request: Pick<Request, 'method' | 'path' | 'headers'>): boolean {
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    return false;
  }
  if (isReservedPath(request.path) || extname(request.path) !== '') {
    return false;
  }
  const accept = request.headers.accept;
  return typeof accept === 'string' && accept.includes('text/html');
}

/**
 * Serves the optional, same-origin PWA without changing API behavior in source
 * checkouts where no web build is present. The SPA fallback is deliberately
 * fail-closed for API, health and OpenAPI paths.
 */
export function installWebApp(server: Express, configuredDirectory?: string): boolean {
  const directory = resolve(configuredDirectory ?? 'apps/web/dist');
  const indexPath = resolve(directory, 'index.html');
  if (!existsSync(indexPath)) {
    return false;
  }

  const staticFiles = express.static(directory, {
    dotfiles: 'deny',
    etag: true,
    fallthrough: true,
    index: false,
    redirect: false,
    setHeaders(response, filePath) {
      if (filePath.includes('/assets/')) {
        response.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
      } else if (
        filePath.endsWith('/sw.js') ||
        filePath.endsWith('/index.html') ||
        filePath.endsWith('/manifest.webmanifest')
      ) {
        response.setHeader('Cache-Control', 'no-cache');
      } else {
        response.setHeader('Cache-Control', 'public, max-age=86400');
      }
    },
  });
  server.use((request: Request, response: Response, next: NextFunction) => {
    if (isReservedPath(request.path)) {
      next();
      return;
    }
    staticFiles(request, response, next);
  });

  server.use((request: Request, response: Response, next: NextFunction) => {
    if (!isWebNavigation(request)) {
      next();
      return;
    }
    response.setHeader('Cache-Control', 'no-cache');
    response.sendFile(indexPath);
  });
  return true;
}
