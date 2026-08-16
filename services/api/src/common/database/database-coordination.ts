import { Prisma } from '@prisma/client';
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

/** Serializes period-range validation and writes for one tracker across devices. */
export async function lockUserPeriods(
  database: Prisma.TransactionClient,
  userId: string,
): Promise<void> {
  await database.$queryRaw`SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(${`lunara:periods:${userId}`}, 0))`;
}
