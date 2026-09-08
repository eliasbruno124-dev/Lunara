import { normalizeInvitationCode } from './oidc';

/**
 * A short-lived hand-off from the public `/invite` landing page to the
 * authenticated application shell.  The invitation remains in the fragment
 * until that page stores it, so it is never sent to the reverse proxy or OIDC
 * provider.
 */
export const INVITATION_STORAGE_KEY = 'lunara-invitation.v1';

interface StoredInvitation {
  invitationCode: string;
  capturedAt: number;
}

function storedInvitation(value: unknown): StoredInvitation | null {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return null;
  const candidate = value as Record<string, unknown>;
  if (typeof candidate.invitationCode !== 'string' || typeof candidate.capturedAt !== 'number') {
    return null;
  }
  const invitationCode = normalizeInvitationCode(candidate.invitationCode);
  if (!invitationCode || !Number.isSafeInteger(candidate.capturedAt) || candidate.capturedAt <= 0) {
    return null;
  }
  return { invitationCode, capturedAt: candidate.capturedAt };
}

/** Returns a validated pending code without consuming it. */
export function pendingInvitation(storage: Storage): StoredInvitation | null {
  const raw = storage.getItem(INVITATION_STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = storedInvitation(JSON.parse(raw) as unknown);
    if (parsed) return parsed;
  } catch {
    // Treat malformed persisted state like an invalid bearer credential.
  }
  storage.removeItem(INVITATION_STORAGE_KEY);
  return null;
}

export function storeInvitation(storage: Storage, invitationCode: string, capturedAt = Date.now()): boolean {
  const normalized = normalizeInvitationCode(invitationCode);
  if (!normalized || !Number.isSafeInteger(capturedAt) || capturedAt <= 0) return false;
  storage.setItem(INVITATION_STORAGE_KEY, JSON.stringify({ invitationCode: normalized, capturedAt }));
  return true;
}

export function clearPendingInvitation(storage: Storage): void {
  storage.removeItem(INVITATION_STORAGE_KEY);
}

/** Invitation acceptance must never discard health changes that have not reached the server. */
export function hasUnresolvedInvitationMutations(
  mutations: readonly unknown[],
): boolean {
  return mutations.length > 0;
}

/**
 * Supports a direct root-shell fallback (`/#invite=CODE`) as well as the
 * public landing-page hand-off. A code is accepted only when it is the sole
 * hash parameter; URLs with an ambiguous or malformed bearer value are
 * cleaned rather than propagated.
 */
export function captureInvitationHash(
  hash: string,
  storage: Storage,
  capturedAt = Date.now(),
): 'captured' | 'invalid' | 'absent' {
  if (!hash.startsWith('#')) return 'absent';
  const params = new URLSearchParams(hash.slice(1));
  const entries = [...params.entries()];
  if (!entries.some(([key]) => key === 'invite')) return 'absent';
  if (entries.length !== 1 || entries[0]?.[0] !== 'invite') return 'invalid';
  return storeInvitation(storage, entries[0][1], capturedAt) ? 'captured' : 'invalid';
}
