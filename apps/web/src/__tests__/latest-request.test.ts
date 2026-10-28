import { describe, expect, it } from 'vitest';
import { IdentityEpochCoordinator } from '../identity-epoch';
import { LatestRequestCoordinator, SupersededRequestError } from '../latest-request';

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { promise, resolve };
}

describe('latest refresh response and serialized cache commit', () => {
  it('accepts a current success and rejects an older response completed after newer revocation', async () => {
    const requests = new LatestRequestCoordinator();
    let state = 'initial';
    await requests.begin().commit(async () => { state = 'shared'; });
    expect(state).toBe('shared');
    const old = requests.begin();
    const oldResponse = deferred<string>();
    const oldFlow = oldResponse.promise.then(value => old.commit(async () => { state = value; }));
    const rejection = expect(oldFlow).rejects.toBeInstanceOf(SupersededRequestError);
    await requests.begin().commit(async () => { state = 'revoked'; });
    oldResponse.resolve('old-shared');
    await rejection;
    expect(state).toBe('revoked');
  });

  it('orders cache writes even when an old asynchronous commit is already in flight', async () => {
    const requests = new LatestRequestCoordinator();
    const started = deferred<void>();
    const oldWrite = deferred<void>();
    let cached = 'initial';
    let visible = 'initial';
    const old = requests.begin();
    const oldFlow = old.commit(async () => {
      started.resolve();
      await oldWrite.promise;
      cached = 'old-shared';
      old.assertCurrent();
      visible = cached;
    });
    const rejection = expect(oldFlow).rejects.toBeInstanceOf(SupersededRequestError);
    await started.promise;
    const current = requests.begin();
    const currentFlow = current.commit(async () => { cached = 'revoked'; visible = cached; });
    oldWrite.resolve();
    await rejection;
    await currentFlow;
    expect({ cached, visible }).toEqual({ cached: 'revoked', visible: 'revoked' });
  });

  it('preserves current errors and does not poison later commits', async () => {
    const requests = new LatestRequestCoordinator();
    const failure = new Error('synthetic write failure');
    await expect(requests.begin().commit(async () => { throw failure; })).rejects.toBe(failure);
    await expect(requests.begin().commit(async () => 'next-success')).resolves.toBe('next-success');
  });

  it('still applies the account identity guard when the request sequence alone remains current', async () => {
    const identities = new IdentityEpochCoordinator();
    const requests = new LatestRequestCoordinator();
    await identities.transition('A', async () => {});
    let cached = 'A';
    const response = deferred<string>();
    const request = requests.begin();
    const oldFlow = identities.run('A', async assertIdentity => {
      const result = await response.promise;
      return request.commit(async () => { assertIdentity(); cached = result; });
    });
    const rejection = expect(oldFlow).rejects.toThrow(/identity changed/);
    const changed = identities.transition('B', async () => { cached = 'B'; });
    response.resolve('old-A');
    await rejection;
    await changed;
    expect(cached).toBe('B');
  });
});
