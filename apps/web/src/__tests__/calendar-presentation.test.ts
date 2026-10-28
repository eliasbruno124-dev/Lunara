import { describe, expect, it } from 'vitest';
import { calendarPredictionBand, calendarPredictionContains } from '../calendar-presentation';
import type { Prediction } from '../types';

const prediction: Prediction = {
  available: true,
  predictedStartFrom: '2026-12-29', predictedStartTo: '2027-01-02',
  predictedEndFrom: '2027-01-01', predictedEndTo: '2027-01-06',
};

describe('Android-compatible calendar prediction projection', () => {
  it('includes start and duration in one estimated band, including a year boundary', () => {
    const band = calendarPredictionBand(prediction);
    expect(band).not.toBeNull();
    expect(calendarPredictionContains('2026-12-28', band)).toBe(false);
    expect(calendarPredictionContains('2026-12-29', band)).toBe(true);
    expect(calendarPredictionContains('2027-01-02', band)).toBe(true);
    expect(calendarPredictionContains('2027-01-03', band)).toBe(true);
    expect(calendarPredictionContains('2027-01-06', band)).toBe(true);
    expect(calendarPredictionContains('2027-01-07', band)).toBe(false);
  });

  it.each(['predictedStartFrom', 'predictedStartTo', 'predictedEndFrom', 'predictedEndTo'] as const)('does not invent %s when a response is incomplete or invalid', (key) => {
    const incomplete = { ...prediction };
    delete incomplete[key];
    expect(calendarPredictionBand(incomplete)).toBeNull();
    expect(calendarPredictionBand({ ...prediction, [key]: '2027-02-30' })).toBeNull();
    expect(calendarPredictionBand({ ...prediction, [key]: 'not-a-date' })).toBeNull();
  });

  it.each([
    { predictedStartTo: '2026-12-28' },
    { predictedEndFrom: '2026-12-28' },
    { predictedEndFrom: '2027-01-07' },
    { predictedEndTo: '2027-01-01' },
  ])('rejects inconsistent boundaries: %j', (invalid) => {
    expect(calendarPredictionBand({ ...prediction, ...invalid })).toBeNull();
  });

  it('keeps unavailable and withheld predictions hidden', () => {
    expect(calendarPredictionBand(null)).toBeNull();
    expect(calendarPredictionBand({ ...prediction, available: false })).toBeNull();
    const withheld = { ...prediction };
    delete withheld.available;
    expect(calendarPredictionBand(withheld)).toBeNull();
    expect(calendarPredictionContains('2027-01-01', null)).toBe(false);
  });

  it('does not modify incoming prediction data or force a duration after the latest end', () => {
    const frozen = Object.freeze({ ...prediction, predictedEndFrom: '2027-01-02', predictedEndTo: '2027-01-02' });
    const band = calendarPredictionBand(frozen);
    expect(calendarPredictionContains('2027-01-02', band)).toBe(true);
    expect(calendarPredictionContains('2027-01-03', band)).toBe(false);
    expect(frozen.predictedEndTo).toBe('2027-01-02');
  });
});
