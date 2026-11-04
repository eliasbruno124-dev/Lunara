import { periodStore, queueStorage, settingsStore, type QueueStorage } from './db';
import { ApiError, type LunaraApi } from './api';
import { StaleIdentityError, type IdentityGuard } from './identity-epoch';
import type { PeriodEvent, SyncMutation, SyncResponse } from './types';

export const MAX_AUTOMATIC_ATTEMPTS = 8;
const MAX_BACKOFF_MS = 5 * 60_000;

export function retryDelayMs(attemptCount: number): number {
  return Math.min(MAX_BACKOFF_MS, 2_000 * 2 ** Math.max(0, attemptCount - 1));
}

export function failedMutation(mutation: SyncMutation, now: number, error: string): SyncMutation {
  const attemptCount = mutation.attemptCount + 1;
  return {
    ...mutation,
    // The request may have reached the server even though no answer arrived.
    sent: true,
    attemptCount,
    nextAttemptAt: now + retryDelayMs(attemptCount),
    state: attemptCount >= MAX_AUTOMATIC_ATTEMPTS ? 'FAILED' : 'PENDING',
    lastError: error.slice(0, 200),
  };
}

export function canAttempt(mutation: SyncMutation, now: number, force: boolean): boolean {
  return force || (mutation.state === 'PENDING' && mutation.nextAttemptAt <= now);
}

export async function queuePeriodStart(
  startDate: string,
  storage: QueueStorage = queueStorage,
): Promise<PeriodEvent> {
  const entityId = crypto.randomUUID();
  const mutation: SyncMutation = {
    clientMutationId: crypto.randomUUID(),
    entityId,
    entityType: 'PERIOD',
    operation: 'UPSERT',
    payload: { id: entityId, startDate, endDate: null, source: 'MANUAL' },
    createdAt: Date.now(),
    attemptCount: 0,
    nextAttemptAt: 0,
    state: 'PENDING',
  };
  const period: PeriodEvent = {
    id: entityId,
    startDate,
    endDate: null,
    source: 'MANUAL',
    pending: true,
  };
  await storage.putMutation(mutation);
  if (storage === queueStorage) await periodStore.put(period);
  return period;
}

export async function queuePeriodEnd(
  period: PeriodEvent,
  endDate: string,
  storage: QueueStorage = queueStorage,
): Promise<PeriodEvent> {
  if (endDate < period.startDate) throw new Error('Period end precedes start');
  const existing = (await storage.getMutations()).find(
    (mutation) => mutation.entityId === period.id && mutation.operation === 'UPSERT',
  );
  if (existing && !('version' in existing.payload)) {
    const updatedMutation: SyncMutation = {
      ...existing,
      payload: { ...existing.payload, endDate },
      attemptCount: 0,
      nextAttemptAt: 0,
      state: 'PENDING',
    };
    await storage.putMutation(updatedMutation);
  } else {
    if (!period.version) throw new Error('The period must sync before it can be updated');
    await storage.putMutation({
      clientMutationId: crypto.randomUUID(),
      entityId: period.id,
      entityType: 'PERIOD',
      operation: 'UPSERT',
      payload: { id: period.id, version: period.version, endDate },
      createdAt: Date.now(),
      attemptCount: 0,
      nextAttemptAt: 0,
      state: 'PENDING',
    });
  }
  const updated = { ...period, endDate, pending: true };
  if (storage === queueStorage) await periodStore.put(updated);
  return updated;
}

/**
 * Removes a period. A period that never reached the server only loses its
 * pending creation; a synced period gets a versioned DELETE, and pending
 * edits of it are dropped because the deletion supersedes them.
 */
export async function queuePeriodDelete(
  period: PeriodEvent,
  storage: QueueStorage = queueStorage,
): Promise<void> {
  const related = (await storage.getMutations()).filter((mutation) => mutation.entityId === period.id);
  const unsyncedCreate = related.find(
    (mutation) => mutation.operation === 'UPSERT' && !('version' in mutation.payload),
  );
  // A creation that was already sent may exist on the server even if its answer got
  // lost. Only a never-sent creation can be dropped; otherwise sync first.
  if (unsyncedCreate && (unsyncedCreate.sent || unsyncedCreate.attemptCount > 0)) {
    throw new Error('The period must sync before it can be deleted');
  }
  for (const mutation of related) await storage.deleteMutation(mutation.clientMutationId);
  if (!unsyncedCreate) {
    if (!period.version) throw new Error('The period must sync before it can be deleted');
    await storage.putMutation({
      clientMutationId: crypto.randomUUID(),
      entityId: period.id,
      entityType: 'PERIOD',
      operation: 'DELETE',
      payload: { id: period.id, version: period.version },
      createdAt: Date.now(),
      attemptCount: 0,
      nextAttemptAt: 0,
      state: 'PENDING',
    });
  }
  if (storage === queueStorage) await periodStore.delete(period.id);
}

export interface SyncState {
  pending: number;
  failed: number;
  lastSuccessfulAt: string | null;
}

export interface SyncMetadataStorage {
  getCursor(): Promise<string>;
  putCursor(cursor: string): Promise<void>;
  getLastSuccessfulAt(): Promise<string | null>;
  putLastSuccessfulAt(timestamp: string): Promise<void>;
}

const browserSyncMetadata: SyncMetadataStorage = {
  async getCursor() {
    return (await settingsStore.get<string>('sync-cursor')) ?? '0';
  },
  putCursor: (cursor) => settingsStore.put('sync-cursor', cursor),
  async getLastSuccessfulAt() {
    return (await settingsStore.get<string>('last-sync')) ?? null;
  },
  putLastSuccessfulAt: (timestamp) => settingsStore.put('last-sync', timestamp),
};

export class PeriodSyncEngine extends EventTarget {
  private running: Promise<SyncState> | null = null;

  constructor(
    private readonly api: Pick<LunaraApi, 'sync'>,
    private readonly storage: QueueStorage = queueStorage,
    private readonly metadata: SyncMetadataStorage = browserSyncMetadata,
  ) {
    super();
  }

  flush(force = false, assertCurrent: IdentityGuard = () => undefined): Promise<SyncState> {
    if (this.running) return this.running;
    this.running = this.performFlush(force, assertCurrent).finally(() => {
      this.running = null;
    });
    return this.running;
  }

  private async performFlush(force: boolean, assertCurrent: IdentityGuard): Promise<SyncState> {
    assertCurrent();
    this.dispatchEvent(new Event('change'));
    const cursor = await this.metadata.getCursor();
    assertCurrent();
    let currentCursor = cursor;
    const mutations = await this.storage.getMutations();
    assertCurrent();
    for (const queued of mutations) {
      if (!canAttempt(queued, Date.now(), force)) continue;
      // Stored before the request leaves: if the tab closes mid-request, a later
      // delete still knows the server may already have this change.
      const mutation: SyncMutation = queued.sent ? queued : { ...queued, sent: true };
      if (!queued.sent) await this.storage.putMutation(mutation);
      try {
        const response = await this.api.sync(currentCursor, mutation);
        assertCurrent();
        currentCursor = response.cursor;
        await this.applyResponse(response, assertCurrent);
        assertCurrent();
        await this.storage.deleteMutation(mutation.clientMutationId);
      } catch (error) {
        // An expired/revoked session is an account boundary, not a retryable
        // mutation failure. The app clears the complete server-side cache and
        // queue before another account can be adopted.
        if (error instanceof ApiError && error.status === 401) throw error;
        if (error instanceof StaleIdentityError) throw error;
        const message = error instanceof Error ? error.message : 'Sync failed';
        assertCurrent();
        await this.storage.putMutation(failedMutation(mutation, Date.now(), message));
        if (!force) break;
      }
    }
    if (this.storage === queueStorage) {
      assertCurrent();
      await this.metadata.putCursor(currentCursor);
      if (currentCursor !== cursor || mutations.length === 0) {
        assertCurrent();
        await this.metadata.putLastSuccessfulAt(new Date().toISOString());
      }
    }
    const remaining = await this.storage.getMutations();
    assertCurrent();
    const state: SyncState = {
      pending: remaining.filter((mutation) => mutation.state === 'PENDING').length,
      failed: remaining.filter((mutation) => mutation.state === 'FAILED').length,
      lastSuccessfulAt:
        this.storage === queueStorage
          ? await this.metadata.getLastSuccessfulAt()
          : null,
    };
    assertCurrent();
    this.dispatchEvent(new CustomEvent('synced', { detail: state }));
    return state;
  }

  private async applyResponse(response: SyncResponse, assertCurrent: IdentityGuard): Promise<void> {
    if (this.storage !== queueStorage) return;
    for (const result of response.results ?? []) {
      assertCurrent();
      if (result.entity) await periodStore.put({ ...result.entity, pending: false });
      assertCurrent();
      if (result.deleted && result.id) await periodStore.delete(result.id);
    }
    for (const change of response.changes ?? []) {
      if (change.entityType !== 'PeriodEvent') continue;
      assertCurrent();
      if (change.operation === 'DELETE' || !change.entity) await periodStore.delete(change.entityId);
      else await periodStore.put({ ...change.entity, pending: false });
    }
  }
}
