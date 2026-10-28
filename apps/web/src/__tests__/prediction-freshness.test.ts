import { describe, expect, it } from 'vitest';
import { calendarPredictionBand, calendarPredictionContains } from '../calendar-presentation';
import { hasFertileEstimate } from '../fertility-presentation';
import { millisecondsUntilNextPredictionDay, predictionReferenceDate, predictionSummary } from '../prediction-freshness';

const prediction = Object.freeze({
  available: true, predictedStartFrom: '2026-04-20', predictedStartTo: '2026-04-26',
  predictedEndFrom: '2026-04-24', predictedEndTo: '2026-04-30',
});

describe('current prediction summary, independent of historical calendar', () => {
  it('keeps equality available and expires the same cached response after midnight, without a network response', () => {
    expect(predictionSummary(prediction, '2026-04-26')).toEqual({ kind: 'current', from: '2026-04-20', to: '2026-04-26' });
    expect(predictionSummary(prediction, '2026-04-27')).toEqual({ kind: 'stale' });
    expect(calendarPredictionContains('2026-04-27', calendarPredictionBand(prediction))).toBe(true);
    expect(prediction.available).toBe(true);
    expect(prediction.predictedEndTo).toBe('2026-04-30');
  });
  it('does not claim a current server estimate while local changes await synchronization', () => {
    expect(predictionSummary(prediction, '2026-04-26', true)).toEqual({ kind: 'pending' });
    expect(predictionSummary(prediction, '2026-04-26', false).kind).toBe('current');
  });
  it('fails closed for missing, invalid, reversed or unavailable estimates', () => {
    for (const input of [null, { ...prediction, available: false }, { available: true, predictedStartTo: prediction.predictedStartTo }, { ...prediction, predictedStartTo: '2026-02-30' }, { ...prediction, predictedStartTo: '2026-04-19' }]) {
      expect(predictionSummary(input, '2026-04-26')).toEqual({ kind: 'unavailable' });
    }
    expect(predictionSummary(prediction, 'not-a-date')).toEqual({ kind: 'unavailable' });
    expect(predictionSummary({ available: false, reason: 'STALE_HISTORY' }, '2026-04-26')).toEqual({ kind: 'stale' });
  });
  it('leaves independent fertility sharing intact without guessing hidden start dates', () => {
    const fertilityOnly = Object.freeze({ available: false, fertileWindowFrom: '2026-04-20', fertileWindowTo: '2026-04-30', fertilityEstimate: { enabled: true, available: true, estimated: true, notContraception: true, doesNotConfirmOvulation: true, reason: 'AVAILABLE' } });
    expect(predictionSummary(fertilityOnly, '2026-04-27')).toEqual({ kind: 'unavailable' });
    expect(hasFertileEstimate(fertilityOnly)).toBe(true);
  });
  it('uses a known timezone precisely, otherwise the device day without inferring partner metadata', () => {
    const now = new Date('2026-04-26T22:30:00Z');
    expect(predictionReferenceDate(now, 'Europe/Berlin')).toBe('2026-04-27');
    expect(predictionReferenceDate(now, 'America/Los_Angeles')).toBe('2026-04-26');
    expect(predictionReferenceDate(now, 'invalid')).toBe(predictionReferenceDate(now));
  });
  it('schedules the next midnight once rather than frequently polling', () => {
    const now = new Date(2026, 3, 26, 23, 59, 59, 500);
    expect(millisecondsUntilNextPredictionDay(now)).toBe(525);
    expect(millisecondsUntilNextPredictionDay(new Date(2026, 3, 27, 0, 0, 0))).toBeGreaterThan(22 * 60 * 60 * 1000);
  });
});
