import { PredictionService } from './prediction.service.js';

describe('PredictionService', () => {
  afterEach(() => {
    jest.useRealTimers();
  });

  it('keeps unconfirmed server rows out of observed duration statistics after day five', async () => {
    jest.useFakeTimers().setSystemTime(new Date('2026-03-31T12:00:00Z'));
    const periods = ['2026-01-01', '2026-01-29', '2026-02-26', '2026-03-26']
      .map(start => ({ startDate: new Date(`${start}T00:00:00Z`), endDate: null }));
    const prisma = {
      cycleProfile: { findUnique: jest.fn().mockResolvedValue({ timezone: 'Europe/Berlin', fertilityPredictionEnabled: true, predictionPaused: false, specialMode: 'NONE' }) },
      periodEvent: { findMany: jest.fn().mockResolvedValue(periods), updateMany: jest.fn() },
    };
    const result = await new PredictionService(prisma as never).calculateForUser('tracker-id');
    expect(result.statistics).toMatchObject({ completedPeriodDurationCount: 0, periodDurationDataBasis: 'default-prior' });
    expect(periods.every(period => period.endDate === null)).toBe(true);
    expect(prisma.periodEvent.updateMany).not.toHaveBeenCalled();
  });

  it('derives the prediction reference date in the cycle profile time zone', async () => {
    // Berlin has already reached 25 August while UTC is still on 24 August.
    // The possible start interval ends on 24 August, so the Berlin profile must
    // receive STALE_HISTORY rather than a one-day-outdated prediction.
    jest.useFakeTimers().setSystemTime(new Date('2026-08-24T22:30:00.000Z'));
    const prisma = {
      cycleProfile: {
        findUnique: jest.fn().mockResolvedValue({
          timezone: 'Europe/Berlin',
          typicalCycleLengthOptional: null,
          typicalPeriodLengthOptional: null,
          lutealLengthOptional: null,
          fertilityPredictionEnabled: false,
          predictionPaused: false,
          specialMode: 'NONE',
        }),
      },
      periodEvent: {
        findMany: jest.fn().mockResolvedValue([
          { startDate: new Date('2026-05-01T00:00:00.000Z'), endDate: null },
          { startDate: new Date('2026-05-29T00:00:00.000Z'), endDate: null },
          { startDate: new Date('2026-06-26T00:00:00.000Z'), endDate: null },
          { startDate: new Date('2026-07-24T00:00:00.000Z'), endDate: null },
        ]),
      },
    };
    const service = new PredictionService(prisma as never);

    await expect(service.calculateForUser('user-id')).resolves.toMatchObject({
      available: false,
      reason: 'STALE_HISTORY',
    });
  });

  it('removes the previous cached forecast when the new range is unreliable', async () => {
    jest.useFakeTimers().setSystemTime(new Date('2026-03-11T12:00:00Z'));
    const cacheTransaction = {
      prediction: {
        deleteMany: jest.fn().mockResolvedValue({ count: 1 }),
        create: jest.fn(),
      },
    };
    const prisma = {
      cycleProfile: {
        findUnique: jest.fn().mockResolvedValue({
          timezone: 'Europe/Berlin',
          fertilityPredictionEnabled: true,
          predictionPaused: false,
          specialMode: 'NONE',
        }),
      },
      periodEvent: {
        findMany: jest.fn().mockResolvedValue(
          ['2026-01-01', '2026-01-29', '2026-03-11']
            .map(start => ({ startDate: new Date(`${start}T00:00:00Z`), endDate: null })),
        ),
      },
      $transaction: jest.fn(async (update: (transaction: typeof cacheTransaction) => Promise<void>) => update(cacheTransaction)),
    };

    const result = await new PredictionService(prisma as never).recalculateAndCache('user-id');

    expect(result).toMatchObject({ available: false, reason: 'UNRELIABLE_RANGE' });
    expect(cacheTransaction.prediction.deleteMany).toHaveBeenCalledWith({ where: { userId: 'user-id' } });
    expect(cacheTransaction.prediction.create).not.toHaveBeenCalled();
    expect(result).not.toHaveProperty('fertileWindowFrom');
  });
});
