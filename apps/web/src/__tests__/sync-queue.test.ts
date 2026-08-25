import { describe, expect, it } from 'vitest';
import { ApiError } from '../api';
import type { QueueStorage } from '../db';
import { IdentityEpochCoordinator } from '../identity-epoch';
import {
  MAX_AUTOMATIC_ATTEMPTS,
  PeriodSyncEngine,
  canAttempt,
  failedMutation,
  queuePeriodEnd,
  queuePeriodStart,
  retryDelayMs,
  type SyncMetadataStorage,
} from '../sync';
import type { SyncMutation } from '../types';

class MemoryQueue implements QueueStorage {
  readonly values = new Map<string, SyncMutation>();

  getMutations(): Promise<SyncMutation[]> {
    return Promise.resolve([...this.values.values()]);
  }

  putMutation(mutation: SyncMutation): Promise<void> {
    this.values.set(mutation.clientMutationId, structuredClone(mutation));
    return Promise.resolve();
  }

  deleteMutation(clientMutationId: string): Promise<void> {
    this.values.delete(clientMutationId);
    return Promise.resolve();
  }
}

describe('offline period mutation queue', () => {
  it('creates stable entity and idempotency ids before any network request', async () => {
    const storage = new MemoryQueue();
    const period = await queuePeriodStart('2026-08-25', storage);
    const queued = [...storage.values.values()];
    expect(queued).toHaveLength(1);
    expect(queued[0]?.entityId).toBe(period.id);
    expect(queued[0]?.clientMutationId).toMatch(/^[0-9a-f-]{36}$/u);
    expect(queued[0]?.payload).toEqual({
      id: period.id,
      startDate: '2026-08-25',
      endDate: null,
      source: 'MANUAL',
    });
  });

  it('merges an offline end date into the unsent creation mutation', async () => {
    const storage = new MemoryQueue();
    const period = await queuePeriodStart('2026-08-22', storage);
    const mutationId = [...storage.values.keys()][0];
    const closed = await queuePeriodEnd(period, '2026-08-25', storage);
    expect(storage.values.size).toBe(1);
    expect([...storage.values.keys()][0]).toBe(mutationId);
    expect([...storage.values.values()][0]?.payload.endDate).toBe('2026-08-25');
    expect(closed.endDate).toBe('2026-08-25');
  });

  it('uses bounded retry delay and stops automatic retries', () => {
    let mutation: SyncMutation = {
      clientMutationId: crypto.randomUUID(),
      entityId: crypto.randomUUID(),
      entityType: 'PERIOD',
      operation: 'UPSERT',
      payload: {},
      createdAt: 0,
      attemptCount: 0,
      nextAttemptAt: 0,
      state: 'PENDING',
    };
    for (let attempt = 0; attempt < MAX_AUTOMATIC_ATTEMPTS; attempt += 1) {
      mutation = failedMutation(mutation, 1_000, 'offline');
    }
    expect(mutation.state).toBe('FAILED');
    expect(canAttempt(mutation, Number.MAX_SAFE_INTEGER, false)).toBe(false);
    expect(canAttempt(mutation, 1_000, true)).toBe(true);
    expect(retryDelayMs(50)).toBe(5 * 60_000);
  });

  it('does not rewrite account A mutations after a 401 account boundary', async () => {
    const error = new ApiError(401, 'unauthorized');
    const storage = new MemoryQueue();
    await queuePeriodStart('2026-08-25', storage);
    const original = structuredClone([...storage.values.values()][0]);
    const api = { sync: () => Promise.reject(error) };
    const metadata: SyncMetadataStorage = {
      getCursor: () => Promise.resolve('0'),
      putCursor: () => Promise.resolve(),
      getLastSuccessfulAt: () => Promise.resolve(null),
      putLastSuccessfulAt: () => Promise.resolve(),
    };
    const engine = new PeriodSyncEngine(api, storage, metadata);

    await expect(engine.flush()).rejects.toBe(error);
    expect([...storage.values.values()][0]).toEqual(original);
  });

  it('does not apply a delayed account-A sync response after switching identity', async () => {
    let resolveResponse!: (value: {
      cursor: string;
      hasMore: boolean;
      results: never[];
      changes: never[];
    }) => void;
    const response = new Promise<{
      cursor: string;
      hasMore: boolean;
      results: never[];
      changes: never[];
    }>((resolve) => {
      resolveResponse = resolve;
    });
    const storage = new MemoryQueue();
    await queuePeriodStart('2026-08-25', storage);
    const metadataWrites: string[] = [];
    const metadata: SyncMetadataStorage = {
      getCursor: () => Promise.resolve('0'),
      putCursor: async (cursor) => {
        metadataWrites.push(`cursor:${cursor}`);
      },
      getLastSuccessfulAt: () => Promise.resolve(null),
      putLastSuccessfulAt: async (timestamp) => {
        metadataWrites.push(`last:${timestamp}`);
      },
    };
    let notifyApiStarted!: () => void;
    const apiStarted = new Promise<void>((resolve) => {
      notifyApiStarted = resolve;
    });
    const api = { sync: () => {
      notifyApiStarted();
      return response;
    } };
    const engine = new PeriodSyncEngine(api, storage, metadata);
    const identities = new IdentityEpochCoordinator();
    await identities.transition('A', () => Promise.resolve());

    const syncingA = identities.run('A', (assertCurrent) => engine.flush(false, assertCurrent));
    await apiStarted;
    const adoptB = identities.transition('B', () => Promise.resolve());
    resolveResponse({ cursor: '1', hasMore: false, results: [], changes: [] });

    await expect(syncingA).rejects.toThrow(/identity changed/u);
    await adoptB;
    expect(storage.values.size).toBe(1);
    expect(metadataWrites).toEqual([]);
  });
});
