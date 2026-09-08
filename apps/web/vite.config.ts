import { defineConfig } from 'vitest/config';
import { VitePWA } from 'vite-plugin-pwa';

const runtimeEnvironment = (
  globalThis as typeof globalThis & {
    process?: { env?: Record<string, string | undefined> };
  }
).process?.env;

export default defineConfig({
  base: '/',
  build: {
    target: 'es2022',
    sourcemap: false,
  },
  server: {
    proxy: {
      '/api': {
        target: runtimeEnvironment?.LUNARA_DEV_API ?? 'http://127.0.0.1:3000',
        changeOrigin: false,
      },
    },
  },
  plugins: [
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icons/lunara.svg', 'icons/lunara-maskable.svg'],
      manifest: false,
      workbox: {
        cleanupOutdatedCaches: true,
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [
          /^\/api(?:\/|$)/,
          /^\/health(?:\/|$)/,
          /^\/ready(?:\/|$)/,
          /^\/api-docs/u,
          /^\/invite(?:\/|$)/u,
          /^\/outpost\.goauthentik\.io(?:\/|$)/u,
          /^\/\.well-known(?:\/|$)/u,
        ],
        globPatterns: ['**/*.{html,js,css,svg,png,webmanifest}'],
        runtimeCaching: [
          {
            urlPattern: ({ request, url }) =>
              request.method === 'GET' &&
              url.origin === self.location.origin &&
              !/^\/(?:api(?:\/|$)|health(?:\/|$)|ready(?:\/|$)|api-docs|invite(?:\/|$)|outpost\.goauthentik\.io(?:\/|$)|\.well-known(?:\/|$))/u.test(url.pathname),
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'lunara-app-shell',
              expiration: { maxEntries: 40, maxAgeSeconds: 30 * 24 * 60 * 60 },
            },
          },
        ],
      },
      devOptions: { enabled: true },
    }),
  ],
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
});
