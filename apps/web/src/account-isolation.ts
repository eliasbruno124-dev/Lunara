import type { UserIdentity } from './types';

export function serverIdentityKey(origin: string, identity: UserIdentity): string {
  return `${new URL(origin).origin}|${identity.householdId}|${identity.id}`;
}

export function identityChanged(
  origin: string,
  previous: UserIdentity | null,
  next: UserIdentity,
): boolean {
  return Boolean(previous && serverIdentityKey(origin, previous) !== serverIdentityKey(origin, next));
}

export async function purgeBeforeIdentitySwitch(
  origin: string,
  previous: UserIdentity | null,
  next: UserIdentity,
  purge: () => Promise<void>,
): Promise<void> {
  if (identityChanged(origin, previous, next)) await purge();
}
