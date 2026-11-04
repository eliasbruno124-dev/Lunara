import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { describe, expect, it } from 'vitest';
import { formatDate } from '../date';
import { hasCurrentFertileEstimate } from '../fertility-presentation';
import { translate, type MessageKey } from '../i18n';
import { predictionSummary } from '../prediction-freshness';
import type { Prediction } from '../types';
import { MinimalElement } from './minimal-dom';

// Exercise the real, narrowly scoped presentation method without starting the app,
// service worker, storage or network. This adapter makes no layout claims.
function predictionCard(prediction: Prediction, language: 'de' | 'en'): MinimalElement {
  const source = readFileSync(new URL('../main.ts', import.meta.url), 'utf8');
  const start = source.indexOf('  private predictionCard(): HTMLElement {');
  const end = source.indexOf('\n  private tipCard()', start);
  if (start < 0 || end < start) throw new Error('Prediction presentation method is missing');
  // The method has only a return annotation and non-null assertions to erase.
  const method = source.slice(start, end)
    .replace('private predictionCard(): HTMLElement', 'function predictionCard()')
    .replace(/!(?=[.,)])/gu, '');
  const render = runInNewContext(`(${method})`, {
    element: (tag: string, className = '', text = '') => {
      const node = new MinimalElement(tag);
      node.className = className;
      node.textContent = text;
      return node;
    },
    predictionReferenceDate: () => '2026-04-16',
    predictionSummary,
    hasCurrentFertileEstimate,
    formatDate,
  }) as (this: unknown) => MinimalElement;
  return render.call({
    prediction, language, localMode: true,
    user: { role: 'TRACKER' },
    syncState: { pending: 0, failed: 0 },
    t: (key: MessageKey, values?: Record<string, string | number>) => translate(language, key, values),
  });
}

describe('unreliable prediction presentation', () => {
  it.each([
    { language: 'en' as const, message: 'Your cycles vary too much for a useful estimate yet' },
    { language: 'de' as const, message: 'Deine Zyklen schwanken noch zu stark für eine hilfreiche Schätzung.' },
  ])('explains the unavailable range in $language without rendering a date or fertile estimate', ({ language, message }) => {
    const card = predictionCard({ available: false, confidence: 'NONE', reason: 'UNRELIABLE_RANGE', basedOnCycleCount: 2 }, language);
    expect(card.querySelectorAll('.subtle').map(node => node.textContent)).toContain(message);
    expect(card.querySelectorAll('.prediction-range').map(node => node.textContent)).toEqual([translate(language, 'predictionUnavailable')]);
    expect(card.querySelectorAll('.current-fertility')).toHaveLength(0);
    expect(card.querySelectorAll('.prediction-basis')).toHaveLength(0);
  });

  it('keeps missing-history guidance separate from an unreliable range', () => {
    const card = predictionCard({ available: false, confidence: 'NONE', reason: 'INSUFFICIENT_DATA' }, 'en');
    expect(card.querySelectorAll('.subtle').map(node => node.textContent)).toContain(translate('en', 'noPrediction'));
  });
});
