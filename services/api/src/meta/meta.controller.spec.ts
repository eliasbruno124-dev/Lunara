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
      });
      expect(prisma.serverState.findUniqueOrThrow).toHaveBeenCalledWith({
        where: { id: 1 },
        select: { bootstrappedAt: true },
      });
      expect(prisma.user.count).not.toHaveBeenCalled();
    },
  );
});
