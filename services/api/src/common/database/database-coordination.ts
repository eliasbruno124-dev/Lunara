import { ForbiddenException } from '@nestjs/common';
import { Prisma, UserRole } from '@prisma/client';
import { AuthenticatedUser } from '../types/authenticated-user.js';
import { PrismaService } from '../../prisma/prisma.service.js';

export const SERVER_STATE_ID = 1;

type ServerStateDatabase =
  | Pick<PrismaService, 'serverState'>
  | Pick<Prisma.TransactionClient, 'serverState'>;

export async function isServerBootstrapped(
  database: ServerStateDatabase,
): Promise<boolean> {
  // The migration creates this singleton. Failing closed when it is missing is
  // safer than ever reopening bootstrap based on the current user count.
  const state = await database.serverState.findUniqueOrThrow({
    where: { id: SERVER_STATE_ID },
    select: { bootstrappedAt: true },
  });
  return state.bootstrappedAt !== null;
}

export async function markServerBootstrapped(
  database: Prisma.TransactionClient,
): Promise<boolean> {
  const marked = await database.serverState.updateMany({
    where: { id: SERVER_STATE_ID, bootstrappedAt: null },
    data: { bootstrappedAt: new Date() },
  });
  return marked.count === 1;
}

export async function lockBootstrap(
  database: Prisma.TransactionClient,
): Promise<void> {
  await database.$queryRaw`SELECT 1 FROM pg_advisory_xact_lock(hashtextextended('lunara:bootstrap', 0))`;
}

export async function lockRelationship(
  database: Prisma.TransactionClient,
  householdId: string,
): Promise<void> {
  await database.$queryRaw`SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(${`lunara:relationship:${householdId}`}, 0))`;
}

/**
 * Serializes first-use provisioning and login for one exact external identity.
 * The issuer is part of the key deliberately: an equal subject from another
 * provider is a different identity and must never be linked implicitly.
 */
export async function lockOidcIdentity(
  database: Prisma.TransactionClient,
  issuer: string,
  subject: string,
): Promise<void> {
  await database.$queryRaw`SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(${`lunara:oidc:${issuer.length}:${issuer}:${subject}`}, 0))`;
}

/** Serializes period-range validation and writes for one tracker across devices. */
export async function lockUserPeriods(
  database: Prisma.TransactionClient,
  userId: string,
): Promise<void> {
  await database.$queryRaw`SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(${`lunara:periods:${userId}`}, 0))`;
}

/** Serializes password changes, identity linking and session creation per user. */
export async function lockUserAuthentication(
  database: Prisma.TransactionClient,
  userId: string,
): Promise<void> {
  await database.$queryRaw`SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(${userId}, 0))`;
}

/**
 * Locks the membership row that determines a request's current relationship
 * and role. Callers must re-read and authorize it while holding this lock;
 * this serializes a role/household move against tracker data writes.
 */
export async function lockMembership(
  database: Prisma.TransactionClient,
  membershipId: string,
  userId: string,
): Promise<void> {
  await database.$queryRaw`
    SELECT "id"
    FROM "Membership"
    WHERE "id" = ${membershipId} AND "userId" = ${userId}
    FOR UPDATE
  `;
}

/**
 * Re-authorizes a tracker write while holding the membership row that an
 * invitation acceptance moves. This must precede any relationship or
 * per-user advisory lock so concurrent writers and acceptance serialize in
 * one consistent order: membership, relationship, then user-periods.
 */
export async function assertCurrentTrackerMembership(
  database: Prisma.TransactionClient,
  user: AuthenticatedUser,
): Promise<void> {
  await lockMembership(database, user.membershipId, user.userId);
  const membership = await database.membership.findUnique({
    where: { id: user.membershipId },
    select: { userId: true, householdId: true, role: true },
  });
  if (
    !membership ||
    membership.userId !== user.userId ||
    membership.householdId !== user.householdId ||
    membership.role !== UserRole.TRACKER
  ) {
    throw new ForbiddenException(
      'This account no longer has tracker permission',
    );
  }
}
