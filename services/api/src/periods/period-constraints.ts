import { Prisma } from '@prisma/client';

export const PERIOD_OVERLAP_CONSTRAINT =
  'PeriodEvent_userId_active_range_excl';
export const PERIOD_OVERLAP_MESSAGE =
  'Period range overlaps an existing period entry';

/**
 * Prisma's JavaScript PostgreSQL adapter currently exposes an exclusion
 * violation as a DriverAdapterError while other engines may wrap it in P2004.
 * Keep the check deliberately narrow to SQLSTATE 23P01 or our constraint name.
 */
export function isPeriodOverlapConstraintError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false;
  const candidate = error as {
    code?: unknown;
    message?: unknown;
    cause?: unknown;
    meta?: unknown;
  };
  const directCode = typeof candidate.code === 'string' ? candidate.code : '';
  const message =
    typeof candidate.message === 'string' ? candidate.message : '';
  const cause =
    candidate.cause && typeof candidate.cause === 'object'
      ? (candidate.cause as {
          code?: unknown;
          originalCode?: unknown;
          message?: unknown;
          originalMessage?: unknown;
        })
      : undefined;
  const causeCode = [cause?.code, cause?.originalCode]
    .filter((value): value is string => typeof value === 'string')
    .join(' ');
  const causeMessage = [cause?.message, cause?.originalMessage]
    .filter((value): value is string => typeof value === 'string')
    .join(' ');
  const metadata =
    error instanceof Prisma.PrismaClientKnownRequestError
      ? JSON.stringify(error.meta ?? {})
      : '';
  return (
    directCode === '23P01' ||
    causeCode.includes('23P01') ||
    message.includes(PERIOD_OVERLAP_CONSTRAINT) ||
    causeMessage.includes(PERIOD_OVERLAP_CONSTRAINT) ||
    metadata.includes(PERIOD_OVERLAP_CONSTRAINT) ||
    metadata.includes('23P01')
  );
}
