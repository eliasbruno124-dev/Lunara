import { PredictionService } from './prediction.service.js';

describe('PredictionService', () => {
  afterEach(() => {
    jest.useRealTimers();
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
});
