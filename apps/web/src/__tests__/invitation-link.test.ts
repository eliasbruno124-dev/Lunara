import { describe, expect, it } from 'vitest';
import {
  captureInvitationHash,
  clearPendingInvitation,
  hasUnresolvedInvitationMutations,
  pendingInvitation,
  storeInvitation,
} from '../invitation-link';

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>();

  get length(): number { return this.values.size; }
  clear(): void { this.values.clear(); }
  getItem(key: string): string | null { return this.values.get(key) ?? null; }
  key(index: number): string | null { return [...this.values.keys()][index] ?? null; }
  removeItem(key: string): void { this.values.delete(key); }
  setItem(key: string, value: string): void { this.values.set(key, value); }
}

describe('invitation link hand-off', () => {
  it('captures only one normalized code from a fragment and leaves no code in a URL query', () => {
    const storage = new MemoryStorage();
    expect(captureInvitationHash('#invite=%20abcd2345%20', storage, 123)).toBe('captured');
    expect(pendingInvitation(storage)).toEqual({ invitationCode: 'ABCD2345', capturedAt: 123 });
  });

  it('does not capture missing, duplicate, extra, or malformed invitation fragments', () => {
    for (const hash of ['', '#tab=settings', '#invite=ABC123', '#invite=ABCD2345&x=1', '#invite=ABCD2345&invite=EFGH2345']) {
      const storage = new MemoryStorage();
      const result = captureInvitationHash(hash, storage, 123);
      expect(result).toBe(hash === '' || hash === '#tab=settings' ? 'absent' : 'invalid');
      expect(pendingInvitation(storage)).toBeNull();
    }
  });

  it('keeps a valid invite through an interrupted sign-in and clears it only after an explicit decision', () => {
    const storage = new MemoryStorage();
    expect(storeInvitation(storage, 'abcd2345', 123)).toBe(true);
    expect(pendingInvitation(storage)?.invitationCode).toBe('ABCD2345');
    expect(pendingInvitation(storage)?.invitationCode).toBe('ABCD2345');
    clearPendingInvitation(storage);
    expect(pendingInvitation(storage)).toBeNull();
  });

  it('removes malformed persisted data rather than using it as a bearer credential', () => {
    const storage = new MemoryStorage();
    storage.setItem('lunara-invitation.v1', JSON.stringify({ invitationCode: 'not-valid', capturedAt: 123 }));
    expect(pendingInvitation(storage)).toBeNull();
    expect(storage.length).toBe(0);
  });

  it('blocks acceptance while any local health mutation remains unresolved', () => {
    expect(hasUnresolvedInvitationMutations([])).toBe(false);
    expect(hasUnresolvedInvitationMutations([{ clientMutationId: 'pending' }])).toBe(true);
    expect(hasUnresolvedInvitationMutations([{ clientMutationId: 'failed' }])).toBe(true);
  });
});
