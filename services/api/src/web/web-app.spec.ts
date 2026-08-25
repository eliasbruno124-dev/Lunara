import express from 'express';
import request from 'supertest';
import { isWebNavigation } from './web-app.js';

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
