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
  it('returns only prediction by default and strips fertility fields', () => {
    const result = projectPartnerDashboard({
      settings: allPrivate,
      prediction: {
        predictedStartFrom: '2026-09-18',
        predictedStartTo: '2026-09-21',
        fertileWindowFrom: '2026-09-01',
        fertileWindowTo: '2026-09-07',
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
      predictedStartFrom: '2026-09-18',
      predictedStartTo: '2026-09-21',
      fertileWindowFrom: undefined,
      fertileWindowTo: undefined,
    });
    expect(result.phase).toBeNull();
    expect(result.periodDates).toEqual([]);
    expect(result.todayLog).toBeNull();
    expect(JSON.stringify(result)).not.toContain('private words');
    expect(JSON.stringify(result)).not.toContain('cramps');
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
        sharePain: true,
        privateDate: new Date('2026-08-12T00:00:00.000Z'),
      },
      prediction: null,
      phase: null,
      periodDates: [],
      todayLog: {
        localDate: '2026-08-12', flowLevel: null, painLevel: 8, mood: null,
        energy: null, sleepQuality: null, customSymptoms: [], note: null,
      },
      today: '2026-08-12',
    });
    expect(result.todayLog).toBeNull();
  });
});
