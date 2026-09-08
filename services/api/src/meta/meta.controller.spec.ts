import { MetaController } from './meta.controller.js';

describe('MetaController', () => {
  it.each([
    [null, true],
    [new Date('2026-08-13T12:00:00.000Z'), false],
  ])(
    'derives setupRequired from persistent server state (%p)',
    async (bootstrappedAt, setupRequired) => {
      const prisma = {
        serverState: {
          findUniqueOrThrow: jest.fn().mockResolvedValue({ bootstrappedAt }),
        },
        // Deliberately fail if the removed User.count heuristic returns.
        user: { count: jest.fn(() => Promise.reject(new Error('must not run'))) },
      };
      const config = {
        get: jest.fn((_key: string, defaultValue: unknown) => defaultValue),
      };
      const controller = new MetaController(prisma as never, config as never);

      await expect(controller.getMeta()).resolves.toMatchObject({
        setupRequired,
        gatewayMode: 'direct',
      });
      expect(prisma.serverState.findUniqueOrThrow).toHaveBeenCalledWith({
        where: { id: 1 },
        select: { bootstrappedAt: true },
      });
      expect(prisma.user.count).not.toHaveBeenCalled();
    },
  );

  it('advertises only password login when OIDC is disabled', async () => {
    const prisma = {
      serverState: {
        findUniqueOrThrow: jest
          .fn()
          .mockResolvedValue({ bootstrappedAt: new Date() }),
      },
    };
    const config = {
      get: jest.fn((_key: string, defaultValue: unknown) => defaultValue),
    };
    const response = await new MetaController(
      prisma as never,
      config as never,
    ).getMeta();

    expect(response.authenticationMethods).toEqual(['PASSWORD']);
    expect(response.primaryAuthenticationMethod).toBe('PASSWORD');
    expect(response).not.toHaveProperty('oidc');
  });

  it('publishes only non-secret PKCE discovery settings when OIDC is enabled', async () => {
    const prisma = {
      serverState: {
        findUniqueOrThrow: jest
          .fn()
          .mockResolvedValue({ bootstrappedAt: new Date() }),
      },
    };
    const values = new Map<string, unknown>([
      ['lunara.gatewayMode', 'authentik'],
      ['lunara.oidc.enabled', true],
      ['lunara.oidc.providerName', 'Authentik'],
      ['lunara.oidc.issuer', 'https://auth.example/application/o/lunara/'],
      ['lunara.oidc.audience', 'lunara-api'],
      ['lunara.oidc.clientId', 'lunara-android'],
      ['lunara.oidc.scopes', ['openid', 'profile']],
    ]);
    const config = {
      get: jest.fn((key: string, defaultValue: unknown) =>
        values.has(key) ? values.get(key) : defaultValue,
      ),
    };
    const response = await new MetaController(
      prisma as never,
      config as never,
    ).getMeta();

    expect(response.authenticationMethods).toEqual(['OIDC', 'PASSWORD']);
    expect(response.primaryAuthenticationMethod).toBe('OIDC');
    expect(response.gatewayMode).toBe('authentik');
    expect(response.oidc).toEqual({
      providerName: 'Authentik',
      issuer: 'https://auth.example/application/o/lunara/',
      audience: 'lunara-api',
      clientId: 'lunara-android',
      scopes: ['openid', 'profile'],
      discoveryUrl:
        'https://auth.example/application/o/lunara/.well-known/openid-configuration',
      enrollment: {
        registration: false,
        invitation: true,
        localPasswordRequired: false,
        createIfMissingRequired: true,
      },
    });
    expect(JSON.stringify(response)).not.toMatch(/secret|jwks/iu);
  });
});
