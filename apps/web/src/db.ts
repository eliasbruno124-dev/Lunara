import type { PeriodEvent, SyncMutation } from './types';

const DATABASE_NAME = 'lunara-web';
const DATABASE_VERSION = 1;
const LOCAL_VAULT_KEY = 'local-vault-v1';

type StoreName = 'settings' | 'periods' | 'mutations' | 'secrets';

let databasePromise: Promise<IDBDatabase> | undefined;

function requestResult<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('IndexedDB request failed'));
  });
}

function transactionDone(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error ?? new Error('IndexedDB transaction failed'));
    transaction.onabort = () => reject(transaction.error ?? new Error('IndexedDB transaction aborted'));
  });
}

export function openDatabase(): Promise<IDBDatabase> {
  if (databasePromise) return databasePromise;
  databasePromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION);
    request.onupgradeneeded = () => {
      const database = request.result;
      for (const store of ['settings', 'periods', 'mutations', 'secrets'] satisfies StoreName[]) {
        if (!database.objectStoreNames.contains(store)) database.createObjectStore(store);
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('IndexedDB could not be opened'));
  });
  return databasePromise;
}

async function getValue<T>(storeName: StoreName, key: IDBValidKey): Promise<T | undefined> {
  const database = await openDatabase();
  const transaction = database.transaction(storeName, 'readonly');
  const value = await requestResult(transaction.objectStore(storeName).get(key));
  await transactionDone(transaction);
  return value as T | undefined;
}

async function putValue(storeName: StoreName, key: IDBValidKey, value: unknown): Promise<void> {
  const database = await openDatabase();
  const transaction = database.transaction(storeName, 'readwrite');
  transaction.objectStore(storeName).put(value, key);
  await transactionDone(transaction);
}

async function deleteValue(storeName: StoreName, key: IDBValidKey): Promise<void> {
  const database = await openDatabase();
  const transaction = database.transaction(storeName, 'readwrite');
  transaction.objectStore(storeName).delete(key);
  await transactionDone(transaction);
}

export const settingsStore = {
  get: <T>(key: string) => getValue<T>('settings', key),
  put: (key: string, value: unknown) => putValue('settings', key, value),
  delete: (key: string) => deleteValue('settings', key),
};

export const secretStore = {
  get: <T>(key: string) => getValue<T>('secrets', key),
  put: (key: string, value: unknown) => putValue('secrets', key, value),
  delete: (key: string) => deleteValue('secrets', key),
};

export interface QueueStorage {
  getMutations(): Promise<SyncMutation[]>;
  putMutation(mutation: SyncMutation): Promise<void>;
  deleteMutation(clientMutationId: string): Promise<void>;
}

export const queueStorage: QueueStorage = {
  async getMutations(): Promise<SyncMutation[]> {
    const database = await openDatabase();
    const transaction = database.transaction('mutations', 'readonly');
    const values = await requestResult(transaction.objectStore('mutations').getAll());
    await transactionDone(transaction);
    return (values as SyncMutation[]).sort((left, right) => left.createdAt - right.createdAt);
  },
  putMutation: (mutation) => putValue('mutations', mutation.clientMutationId, mutation),
  deleteMutation: (clientMutationId) => deleteValue('mutations', clientMutationId),
};

export const periodStore = {
  async all(scope: 'server' | 'local' = 'server'): Promise<PeriodEvent[]> {
    const database = await openDatabase();
    const transaction = database.transaction('periods', 'readonly');
    const values = await requestResult(transaction.objectStore('periods').getAll());
    await transactionDone(transaction);
    return (values as Array<PeriodEvent & { cacheScope?: string }>)
      .filter((period) => period.cacheScope === scope)
      .filter((period) => !period.deletedAt)
      .sort((left, right) => right.startDate.localeCompare(left.startDate));
  },
  put: (period: PeriodEvent, scope: 'server' | 'local' = 'server') =>
    putValue('periods', `${scope}:${period.id}`, { ...period, cacheScope: scope }),
  delete: (id: string, scope: 'server' | 'local' = 'server') =>
    deleteValue('periods', `${scope}:${id}`),
  async replace(periods: PeriodEvent[], scope: 'server' | 'local' = 'server'): Promise<void> {
    const database = await openDatabase();
    const transaction = database.transaction('periods', 'readwrite');
    const store = transaction.objectStore('periods');
    const keys = await requestResult(store.getAllKeys());
    for (const key of keys) {
      if (typeof key === 'string' && key.startsWith(`${scope}:`)) store.delete(key);
    }
    for (const period of periods) store.put({ ...period, cacheScope: scope }, `${scope}:${period.id}`);
    await transactionDone(transaction);
  },
};

/**
 * Persists the local-only vault and removes every legacy clear-text Solo
 * record in the same IndexedDB transaction. The envelope contains ciphertext
 * only; its passphrase-derived key lives exclusively in the page session.
 */
export async function persistLocalVaultEnvelope(envelope: unknown): Promise<void> {
  const database = await openDatabase();
  const transaction = database.transaction(['periods', 'settings', 'secrets'], 'readwrite');
  const periodsStore = transaction.objectStore('periods');
  const keys = await requestResult(periodsStore.getAllKeys());
  for (const key of keys) {
    if (typeof key === 'string' && key.startsWith('local:')) periodsStore.delete(key);
  }
  const settings = transaction.objectStore('settings');
  settings.put(true, 'local-mode');
  for (const legacyKey of ['local-user', 'local-prediction', 'portable-backup-extras']) {
    settings.delete(legacyKey);
  }
  transaction.objectStore('secrets').put(envelope, LOCAL_VAULT_KEY);
  await transactionDone(transaction);
}

export function getLocalVaultEnvelope<T>(): Promise<T | undefined> {
  return secretStore.get<T>(LOCAL_VAULT_KEY);
}

export async function clearPrivateData(): Promise<void> {
  const database = await openDatabase();
  const transaction = database.transaction(
    ['settings', 'periods', 'mutations', 'secrets'],
    'readwrite',
  );
  for (const store of ['settings', 'periods', 'mutations', 'secrets'] satisfies StoreName[]) {
    transaction.objectStore(store).clear();
  }
  await transactionDone(transaction);
}

async function clearPeriodScope(scope: 'server' | 'local'): Promise<void> {
  const database = await openDatabase();
  const transaction = database.transaction('periods', 'readwrite');
  const store = transaction.objectStore('periods');
  const keys = await requestResult(store.getAllKeys());
  for (const key of keys) {
    if (typeof key === 'string' && key.startsWith(`${scope}:`)) store.delete(key);
  }
  await transactionDone(transaction);
}

export async function clearServerPrivateData(): Promise<void> {
  await clearServerHealthData();
  const database = await openDatabase();
  const transaction = database.transaction('secrets', 'readwrite');
  transaction.objectStore('secrets').clear();
  await transactionDone(transaction);
}

export async function clearServerHealthData(): Promise<void> {
  const database = await openDatabase();
  const transaction = database.transaction(['settings', 'mutations'], 'readwrite');
  for (const key of [
    'user',
    'prediction',
    'partner-dashboard',
    'partner-tip',
    'sync-cursor',
    'last-sync',
  ]) {
    transaction.objectStore('settings').delete(key);
  }
  transaction.objectStore('mutations').clear();
  await transactionDone(transaction);
  await clearPeriodScope('server');
}

export async function clearLocalPrivateData(): Promise<void> {
  const database = await openDatabase();
  const transaction = database.transaction(['settings', 'periods', 'secrets'], 'readwrite');
  const settings = transaction.objectStore('settings');
  for (const key of ['local-mode', 'local-user', 'local-prediction', 'portable-backup-extras']) {
    settings.delete(key);
  }
  transaction.objectStore('secrets').delete(LOCAL_VAULT_KEY);
  const periods = transaction.objectStore('periods');
  const keys = await requestResult(periods.getAllKeys());
  for (const key of keys) {
    if (typeof key === 'string' && key.startsWith('local:')) periods.delete(key);
  }
  await transactionDone(transaction);
}
