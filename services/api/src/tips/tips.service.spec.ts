import {
  CyclePhase,
  PreferredLanguage,
  TipCategory,
  UserRole,
} from '@prisma/client';
import { AuthenticatedUser } from '../common/types/authenticated-user.js';
import { PredictionResult } from '../prediction/prediction.algorithm.js';
import { TipsService } from './tips.service.js';

const partner: AuthenticatedUser = {
  userId: 'partner-id',
  sessionId: 'session-id',
  householdId: 'relationship-id',
  membershipId: 'membership-id',
  role: UserRole.PARTNER,
  username: 'partner',
  displayName: 'Partner',
  preferredLanguage: PreferredLanguage.DE,
};

const prediction: PredictionResult = {
  available: true,
  predictedStartFrom: '2026-09-28',
  predictedStartTo: '2026-10-02',
  predictedEndFrom: '2026-10-02',
  predictedEndTo: '2026-10-06',
  fertileWindowFrom: '2026-09-10',
  fertileWindowTo: '2026-09-17',
  fertilityEstimate: {
    enabled: true,
    available: true,
    estimated: true,
    reason: 'AVAILABLE',
    basedOnCycleCount: 6,
    notContraception: true,
    doesNotConfirmOvulation: true,
  },
  confidence: 'MEDIUM',
  basedOnCycleCount: 6,
  calculatedAt: '2026-09-14T00:00:00.000Z',
  algorithmVersion: 'test',
  typicalCycleLength: 29,
  typicalPeriodLength: 5,
  variationDays: 2,
  statistics: {
    averageCycleLength: 29,
    medianCycleLength: 29,
    shortestCycle: 28,
    longestCycle: 30,
    typicalPeriodDuration: 5,
    medianAbsoluteDeviation: 1,
    variationDays: 2,
    consideredCycleCount: 6,
    allObservedCycleLengths: [29, 29, 28, 30, 29, 29],
  },
  explanation: {
    method: 'recency-weighted-median-with-mad',
    dataBasis: 'observed-cycles',
    cyclesUsed: 6,
    outliersRetained: true,
    uncertainty: 'medium',
    note: 'test',
  },
};

describe('TipsService privacy-aware context', () => {
  beforeEach(() => {
    jest.useFakeTimers().setSystemTime(new Date('2026-09-14T10:00:00.000Z'));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('uses a fresh prediction and exposes a fertility tip only with dedicated sharing', async () => {
    const tip = selectableTip({
      id: '10000000-0000-4000-8000-000000000053',
      requiredSharedFields: ['shareFertileWindow'],
      applicablePhase: CyclePhase.OVULATION_ESTIMATE,
    });
    const prisma = prismaFor([tip], { shareFertileWindow: true });
    const predictions = { calculateForUser: jest.fn().mockResolvedValue(prediction) };
    const service = new TipsService(prisma as never, predictions as never);

    const result = await service.today(partner);

    expect(predictions.calculateForUser).toHaveBeenCalledWith('tracker-id');
    expect(result.tip).toMatchObject({
      id: tip.id,
      applicablePhase: CyclePhase.OVULATION_ESTIMATE,
    });
  });

  it('fails closed when fertility sharing is absent or history is insufficient', async () => {
    const tip = selectableTip({
      id: '10000000-0000-4000-8000-000000000053',
      requiredSharedFields: ['shareFertileWindow'],
      applicablePhase: CyclePhase.OVULATION_ESTIMATE,
    });

    const withoutPermission = new TipsService(
      prismaFor([tip], { shareFertileWindow: false }) as never,
      { calculateForUser: jest.fn().mockResolvedValue(prediction) } as never,
    );
    await expect(withoutPermission.today(partner)).resolves.toMatchObject({ tip: null });

    const insufficient = new TipsService(
      prismaFor([tip], { shareFertileWindow: true }) as never,
      {
        calculateForUser: jest.fn().mockResolvedValue({
          ...prediction,
          basedOnCycleCount: 2,
          fertilityEstimate: {
            ...prediction.fertilityEstimate,
            available: false,
            reason: 'INSUFFICIENT_DATA',
            basedOnCycleCount: 2,
          },
        }),
      } as never,
    );
    await expect(insufficient.today(partner)).resolves.toMatchObject({ tip: null });
  });
});

function prismaFor(
  tips: ReturnType<typeof selectableTip>[],
  sharingOverrides: Record<string, unknown>,
) {
  return {
    membership: { findFirst: jest.fn().mockResolvedValue({ userId: 'tracker-id' }) },
    cycleProfile: { findUnique: jest.fn().mockResolvedValue({ timezone: 'Europe/Berlin' }) },
    sharingSettings: {
      findUnique: jest.fn().mockResolvedValue({
        tipsEnabled: true,
        sharingPaused: false,
        disabledTipCategories: [],
        sharePrediction: true,
        sharePeriodDates: false,
        sharePhase: false,
        shareFlow: false,
        sharePain: false,
        shareMood: false,
        shareEnergy: false,
        shareSleep: false,
        shareSymptoms: false,
        shareNotes: false,
        shareFertileWindow: false,
        allowSymptomBasedTips: false,
        privateDate: null,
        ...sharingOverrides,
      }),
    },
    carePreferences: { findUnique: jest.fn().mockResolvedValue(null) },
    dailyLog: { findUnique: jest.fn().mockResolvedValue(null) },
    periodEvent: {
      findFirst: jest.fn().mockResolvedValue({
        startDate: new Date('2026-09-01T00:00:00.000Z'),
        endDate: new Date('2026-09-05T00:00:00.000Z'),
      }),
    },
    partnerTip: {
      createMany: jest.fn().mockResolvedValue({ count: 0 }),
      findMany: jest.fn().mockResolvedValue(tips),
    },
  };
}

function selectableTip(overrides: Record<string, unknown>) {
  return {
    id: '10000000-0000-4000-8000-000000000001',
    category: TipCategory.GENERAL,
    titleDe: 'Titel',
    bodyDe: 'Text',
    callToActionDe: 'Frag nach.',
    titleEn: 'Title',
    bodyEn: 'Text',
    callToActionEn: 'Ask.',
    requiredSharedFields: [],
    applicablePhase: null,
    applicableSymptoms: [],
    priority: 50,
    enabled: true,
    createdByUserId: null,
    householdId: null,
    createdAt: new Date('2026-01-01T00:00:00.000Z'),
    updatedAt: new Date('2026-01-01T00:00:00.000Z'),
    feedback: [],
    ...overrides,
  };
}
