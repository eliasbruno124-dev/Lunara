import { describe, expect, it } from 'vitest';
import { identityChanged, purgeBeforeIdentitySwitch } from '../account-isolation';
import type { SyncMutation, UserIdentity } from '../types';

const trackerA: UserIdentity = {
  id: 'user-a',
  username: 'a',
  displayName: 'A',
  preferredLanguage: 'DE',
  role: 'TRACKER',
  householdId: 'household-a',
};

const trackerB: UserIdentity = {
  ...trackerA,
  id: 'user-b',
  username: 'b',
  displayName: 'B',
  householdId: 'household-b',
};

describe('cross-account cache isolation', () => {
  it('keys identity by origin, household and user', () => {
    expect(identityChanged('https://lunara.example', trackerA, trackerA)).toBe(false);
    expect(identityChanged('https://lunara.example', trackerA, trackerB)).toBe(true);
  });

  it('purges account A queue before account B can be adopted', async () => {
    const queue: SyncMutation[] = [{
      clientMutationId: crypto.randomUUID(),
      entityId: crypto.randomUUID(),
      entityType: 'PERIOD',
      operation: 'UPSERT',
      payload: { id: crypto.randomUUID(), startDate: '2026-08-25' },
      createdAt: 0,
      attemptCount: 0,
      nextAttemptAt: 0,
      state: 'PENDING',
    }];
    await purgeBeforeIdentitySwitch(
      'https://lunara.example',
      trackerA,
      trackerB,
      async () => {
        queue.splice(0);
      },
    );
    expect(queue).toEqual([]);
  });
});
