import { projectPartnerDashboard } from './partner-projection.js';

const allPrivate = {
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
  sharingPaused: false,
  privateDate: null,
};

describe('projectPartnerDashboard', () => {
  it.each([
    ['2026-09-19', true],
    ['2026-09-20', false],
    ['2026-10-03', false],
    ['2026-08-31', false],
    ['2026-09-31', false],
  ])('accepts at most nineteen inclusive fertility days (%s) independently of period sharing', (to, supported) => {
    for (const metadata of [true, { enabled: true, available: true, estimated: true, notContraception: true, doesNotConfirmOvulation: true }]) {
      const input = {
        settings: { ...allPrivate, sharePrediction: false, shareFertileWindow: true },
        prediction: { predictedStartFrom: '2026-10-10', fertileWindowFrom: '2026-09-01', fertileWindowTo: to, fertilityEstimate: metadata },
        phase: null, periodDates: [], todayLog: null, today: '2026-09-05',
      };
      const result = projectPartnerDashboard(input);
      expect(result.prediction?.fertileWindowFrom).toBe(supported ? '2026-09-01' : null);
      expect(result.prediction?.fertileWindowTo).toBe(supported ? to : null);
      expect(result.prediction?.fertilityEstimate).toEqual(supported ? metadata :
        metadata === true ? false : { ...metadata, available: false, reason: 'UNRELIABLE_DATA' });
      expect(result.prediction).not.toHaveProperty('predictedStartFrom');
      expect(result.calendarAccess).toEqual({ periodDates: false, fertileWindow: true });
      expect(projectPartnerDashboard({ ...input, settings: { ...input.settings, shareFertileWindow: false } }).prediction).toBeNull();
      expect(projectPartnerDashboard({ ...input, settings: { ...input.settings, sharingPaused: true } }).prediction).toBeNull();
      expect(projectPartnerDashboard({ ...input, settings: { ...input.settings, privateDate: new Date('2026-09-05T00:00:00Z') } }).prediction).toBeNull();
    }
  });

  it('keeps bounded historical calendar fertility while current-day consumers can expire it separately', () => {
    const result = projectPartnerDashboard({
      settings: { ...allPrivate, sharePrediction: false, shareFertileWindow: true },
      prediction: { fertileWindowFrom: '2026-09-01', fertileWindowTo: '2026-09-07', fertilityEstimate: true },
      phase: null, periodDates: [], todayLog: null, today: '2026-09-08',
    });
    expect(result.prediction?.fertileWindowTo).toBe('2026-09-07');
  });

  it('returns only prediction by default and strips fertility fields', () => {
    const result = projectPartnerDashboard({
      settings: allPrivate,
      prediction: {
        available: true,
        predictedStartFrom: '2026-09-18',
        predictedStartTo: '2026-09-21',
        confidence: 'MEDIUM',
        fertileWindowFrom: '2026-09-01',
        fertileWindowTo: '2026-09-07',
        fertilityEstimate: { available: true },
        explanation: { privateHistoryDetail: 'six exact cycles' },
      },
      phase: 'LUTEAL_ESTIMATE',
      periodDates: [{ startDate: '2026-08-20' }],
      todayLog: {
        localDate: '2026-08-12',
        flowLevel: 'HEAVY',
        painLevel: 9,
        mood: 'stressed',
        energy: 2,
        sleepQuality: 3,
        customSymptoms: ['cramps'],
        note: 'private words',
      },
      today: '2026-08-12',
    });
    expect(result.prediction).toEqual({
      available: true,
      predictedStartFrom: '2026-09-18',
      predictedStartTo: '2026-09-21',
      confidence: 'MEDIUM',
    });
    expect(result.phase).toBeNull();
    expect(result.periodDates).toEqual([]);
    expect(result.calendarAccess).toEqual({ periodDates: false, fertileWindow: false });
    expect(result.todayLog).toBeNull();
    expect(JSON.stringify(result)).not.toContain('private words');
    expect(JSON.stringify(result)).not.toContain('cramps');
    expect(JSON.stringify(result)).not.toContain('privateHistoryDetail');
    expect(JSON.stringify(result)).not.toContain('fertilityEstimate');
  });

  it('projects the fertility estimate only under its dedicated permission', () => {
    const result = projectPartnerDashboard({
      settings: {
        ...allPrivate,
        sharePrediction: false,
        shareFertileWindow: true,
      },
      prediction: {
        available: true,
        predictedStartFrom: '2026-09-18',
        fertileWindowFrom: '2026-09-01',
        fertileWindowTo: '2026-09-07',
        fertilityEstimate: {
          enabled: true,
          available: true,
          estimated: true,
          notContraception: true,
          doesNotConfirmOvulation: true,
        },
      },
      phase: 'OVULATION_ESTIMATE',
      periodDates: [],
      todayLog: null,
      today: '2026-09-03',
    });
    expect(result.prediction).toEqual({
      fertileWindowFrom: '2026-09-01',
      fertileWindowTo: '2026-09-07',
      fertilityEstimate: {
        enabled: true,
        available: true,
        estimated: true,
        notContraception: true,
        doesNotConfirmOvulation: true,
      },
    });
    expect(JSON.stringify(result.prediction)).not.toContain('predictedStartFrom');
    expect(result.phase).toBeNull();
    expect(result.calendarAccess).toEqual({ periodDates: false, fertileWindow: true });
  });

  it('stops all projections while sharing is paused', () => {
    const result = projectPartnerDashboard({
      settings: { ...allPrivate, sharingPaused: true, sharePain: true },
      prediction: { predictedStartFrom: '2026-09-18' },
      phase: null,
      periodDates: [],
      todayLog: {
        localDate: '2026-08-12', flowLevel: null, painLevel: 8, mood: null,
        energy: null, sleepQuality: null, customSymptoms: [], note: null,
      },
      today: '2026-08-12',
    });
    expect(result).toEqual({
      sharingPaused: true,
      calendarAccess: { periodDates: false, fertileWindow: false },
      prediction: null,
      phase: null,
      periodDates: [],
      todayLog: null,
    });
  });

  it('honours private-day suppression even when individual fields are shared', () => {
    const result = projectPartnerDashboard({
      settings: {
        ...allPrivate,
        sharePeriodDates: true,
        sharePhase: true,
        sharePain: true,
        shareFertileWindow: true,
        privateDate: new Date('2026-08-12T00:00:00.000Z'),
      },
      prediction: {
        available: true,
        predictedStartFrom: '2026-09-18',
        fertileWindowFrom: '2026-08-10',
        fertileWindowTo: '2026-08-16',
        fertilityEstimate: {
          enabled: true,
          available: true,
          estimated: true,
          notContraception: true,
          doesNotConfirmOvulation: true,
        },
      },
      phase: 'OVULATION_ESTIMATE',
      periodDates: [{ startDate: '2026-08-12', endDate: null }],
      todayLog: {
        localDate: '2026-08-12', flowLevel: null, painLevel: 8, mood: null,
        energy: null, sleepQuality: null, customSymptoms: [], note: null,
      },
      today: '2026-08-12',
    });
    expect(result.todayLog).toBeNull();
    expect(result.calendarAccess).toEqual({ periodDates: false, fertileWindow: false });
    expect(result.phase).toBeNull();
    expect(result.periodDates).toEqual([]);
    expect(result.prediction).toEqual({
      available: true,
      predictedStartFrom: '2026-09-18',
    });
    expect(JSON.stringify(result.prediction)).not.toContain('fertileWindow');
    expect(JSON.stringify(result)).not.toContain('fertilityEstimate');
  });
});
