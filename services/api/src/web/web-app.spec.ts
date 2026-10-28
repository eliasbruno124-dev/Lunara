import express from 'express';
import request, { type Response as TestResponse } from 'supertest';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { installWebApp, isWebNavigation } from './web-app.js';

function navigation(path: string, accept = 'text/html') {
  return {
    method: 'GET',
    path,
    headers: { accept },
  };
}

describe('web application routing policy', () => {
  it.each([
    '/api',
    '/api/v1/meta',
    '/health',
    '/ready',
    '/api-docs',
    '/api-docs-json',
    '/.well-known/assetlinks.json',
    '/invite',
    '/invite/private',
  ]) (
    'never treats %s as an SPA navigation',
    (path) => {
      expect(isWebNavigation(navigation(path))).toBe(false);
    },
  );

  it('serves browser routes including the exact OIDC callback through the SPA', () => {
    expect(isWebNavigation(navigation('/'))).toBe(true);
    expect(isWebNavigation(navigation('/auth/callback'))).toBe(true);
  });

  it('does not turn missing static resources or JSON requests into the app shell', () => {
    expect(isWebNavigation(navigation('/missing.svg'))).toBe(false);
    expect(isWebNavigation(navigation('/calendar', 'application/json'))).toBe(false);
    expect(isWebNavigation({ ...navigation('/calendar'), method: 'POST' })).toBe(false);
  });

  it('leaves API-style paths available to later middleware', async () => {
    const server = express();
    server.use((incoming, response, next) => {
      if (isWebNavigation(incoming)) {
        response.status(200).send('spa');
        return;
      }
      next();
    });
    server.get('/api/v1/meta', (_incoming, response) => response.json({ name: 'Lunara' }));

    await request(server).get('/api/v1/meta').set('Accept', 'text/html').expect(200, {
      name: 'Lunara',
    });
  });
});

describe('APK downloads', () => {
  let directory: string;
  let server: ReturnType<typeof express>;
  const binary = (response: TestResponse, done: (error: Error | null, body: Buffer) => void) => {
    const chunks: Buffer[] = [];
    response.on('data', (chunk: Buffer) => chunks.push(chunk));
    response.on('end', () => done(null, Buffer.concat(chunks)));
    response.on('error', (error: Error) => done(error, Buffer.alloc(0)));
  };

  beforeEach(() => {
    directory = mkdtempSync(join(tmpdir(), 'lunara-apk-route-'));
    mkdirSync(join(directory, 'downloads'));
    writeFileSync(join(directory, 'index.html'), '<html>application shell</html>');
    writeFileSync(join(directory, 'downloads/lunara-1.3.0.apk'), Buffer.from('PK\x03\x04synthetic-apk'));
    writeFileSync(join(directory, 'downloads/lunara.apk'), Buffer.from('PK\x03\x04bundled-apk'));
    writeFileSync(join(directory, 'downloads/private.txt'), 'must not be served by download endpoint');
    server = express();
    installWebApp(server, directory);
  });

  afterEach(() => rmSync(directory, { recursive: true, force: true }));

  it('serves an attachment rather than the app shell even for browser navigation', async () => {
    const result = await request(server).get('/api/v1/downloads/lunara-1.3.0.apk')
      .buffer(true).parse(binary)
      .set('Accept', 'text/html').expect(200)
      .expect('Content-Type', 'application/vnd.android.package-archive')
      .expect('Content-Disposition', /^attachment; filename="?lunara-1\.3\.0\.apk"?$/u);
    expect(result.body).toEqual(Buffer.from('PK\x03\x04synthetic-apk'));
  });

  it('supports download metadata and resuming a partial download', async () => {
    await request(server).head('/api/v1/downloads/lunara-1.3.0.apk').expect(200)
      .expect('Content-Length', '17');
    const partial = await request(server).get('/api/v1/downloads/lunara-1.3.0.apk')
      .buffer(true).parse(binary)
      .set('Range', 'bytes=0-3').expect(206).expect('Content-Range', 'bytes 0-3/17');
    expect(partial.body).toEqual(Buffer.from('PK\x03\x04'));
  });

  it.each(['private.txt', 'missing.apk', 'lunara-9.9.9.apk', '..%2Findex.html', 'lunara-1.3.0.apk%2F..%2Fprivate.txt'])(
    'does not return another file or app shell for %s', async (filename) => {
      await request(server).get(`/api/v1/downloads/${filename}`).set('Accept', 'text/html').expect(404);
    },
  );

  it('also marks the legacy static APK route as a download', async () => {
    await request(server).get('/downloads/lunara-1.3.0.apk').expect(200)
      .expect('Content-Disposition', 'attachment');
  });

  it('serves the bundled APK through a stable same-origin URL', async () => {
    const result = await request(server).get('/api/v1/downloads/lunara.apk')
      .buffer(true).parse(binary).set('Accept', 'text/html').expect(200)
      .expect('Content-Type', 'application/vnd.android.package-archive')
      .expect('Content-Disposition', /^attachment; filename="?lunara\.apk"?$/u);
    expect(result.body).toEqual(Buffer.from('PK\x03\x04bundled-apk'));
  });
});
