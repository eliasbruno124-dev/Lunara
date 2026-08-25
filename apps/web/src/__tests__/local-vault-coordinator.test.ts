import { describe, expect, it } from 'vitest';
import { LocalVaultCoordinator } from '../local-vault-coordinator';

function deferred<T = void>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

describe('local vault delete boundary', () => {
  it('rejects a delayed write before persistence and clears as the final operation', async () => {
    const coordinator = new LocalVaultCoordinator();
    const continueWrite = deferred();
    const writeStarted = deferred();
    let envelope: string | null = 'old-envelope';

    const writing = coordinator.write(async (assertCurrent) => {
      writeStarted.resolve();
      await continueWrite.promise;
      assertCurrent();
      envelope = 'late-envelope';
    });
    await writeStarted.promise;
    const deleting = coordinator.delete(async () => {
      envelope = null;
    }, () => undefined);
    continueWrite.resolve();

    await expect(writing).rejects.toThrow(/invalidated/u);
    await deleting;
    expect(envelope).toBeNull();
    expect(coordinator.busy).toBe(false);
  });

  it('waits for a write already past persistence and then removes its envelope', async () => {
    const coordinator = new LocalVaultCoordinator();
    const finishWrite = deferred();
    const persisted = deferred();
    let envelope: string | null = 'old-envelope';

    const writing = coordinator.write(async () => {
      envelope = 'late-envelope';
      persisted.resolve();
      await finishWrite.promise;
    });
    await persisted.promise;
    const deleting = coordinator.delete(async () => {
      envelope = null;
    }, () => undefined);
    finishWrite.resolve();

    await expect(writing).rejects.toThrow(/invalidated/u);
    await deleting;
    expect(envelope).toBeNull();
  });

  it('refuses an import/save started after deletion crossed the boundary', async () => {
    const coordinator = new LocalVaultCoordinator();
    const releaseDelete = deferred();
    const deleting = coordinator.delete(async () => {
      await releaseDelete.promise;
    }, () => undefined);

    await expect(coordinator.write(async () => undefined)).rejects.toThrow(/busy/u);
    releaseDelete.resolve();
    await deleting;
  });
});
