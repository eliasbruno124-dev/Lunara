import { describe, expect, it } from 'vitest';
import { IdentityEpochCoordinator } from '../identity-epoch';

function deferred<T = void>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

describe('server identity epoch', () => {
  it('does not let a delayed account-A response repopulate state after adopting B', async () => {
    const coordinator = new IdentityEpochCoordinator();
    const state: { identity: string | null; periods: string[] } = { identity: null, periods: [] };
    await coordinator.transition('A', async () => {
      state.identity = 'A';
    });
    const response = deferred<string[]>();
    const requestStarted = deferred();
    const oldFlow = coordinator.run('A', async (assertCurrent) => {
      requestStarted.resolve();
      const periods = await response.promise;
      assertCurrent();
      state.periods = periods;
    });
    await requestStarted.promise;
    const adoptB = coordinator.transition('B', async () => {
      state.identity = 'B';
      state.periods = [];
    });
    response.resolve(['account-A-health-data']);

    await expect(oldFlow).rejects.toThrow(/identity changed/u);
    await expect(adoptB).resolves.toBe(true);
    expect(state).toEqual({ identity: 'B', periods: [] });
  });

  it('marks an account-A unauthorized result stale instead of clearing B', async () => {
    const coordinator = new IdentityEpochCoordinator();
    let signedIn: string | null = null;
    await coordinator.transition('A', async () => {
      signedIn = 'A';
    });
    const response = deferred();
    const started = deferred();
    const oldFlow = coordinator.run('A', async (assertCurrent) => {
      started.resolve();
      await response.promise;
      assertCurrent();
      signedIn = null;
    });
    await started.promise;
    const adoptB = coordinator.transition('B', async () => {
      signedIn = 'B';
    });
    response.resolve();

    await expect(oldFlow).rejects.toThrow(/identity changed/u);
    await adoptB;
    expect(signedIn).toBe('B');
  });
});
