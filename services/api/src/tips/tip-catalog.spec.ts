import { CyclePhase, TipCategory } from '@prisma/client';
import { TIP_CATALOG } from './tip-catalog.js';
import { EDITORIAL_COPY } from './tip-editorial.js';
import { CARE_ACTION_IDS } from './tip-care-actions.js';

// Regression tripwires, not a claim to measure empathy or clinical quality.
// New copy still needs the situation/action/boundary editorial review.
const emptyAction = /^(frag(e)? (offen |ganz normal )?(nach|was)|ask (what|whether)|tu heute eine kleine|do one small kind|begegne der person|respond to the person|sei (einfach )?da|be there)\b/iu;
const unsafeClaim = /(?:garantiert|guaranteed) (?:schmerzfrei|pain.?free)|(?:klinisch validiert|clinically validated)|(?:du bist|you are) (?:wegen|because of) (?:deiner phase|your phase)|(?:nimm|take) (?:ibuprofen|paracetamol)/iu;

describe('TIP_CATALOG', () => {
  it('contains careful German and English tips for every supported phase', () => {
    for (const phase of [
      CyclePhase.MENSTRUATION,
      CyclePhase.PREMENSTRUAL_ESTIMATE,
      CyclePhase.FOLLICULAR_ESTIMATE,
      CyclePhase.LUTEAL_ESTIMATE,
      CyclePhase.OVULATION_ESTIMATE,
    ]) {
      const phaseTips = TIP_CATALOG.filter((tip) => tip.applicablePhase === phase);
      expect(phaseTips.length).toBeGreaterThanOrEqual(3);
      for (const tip of phaseTips) {
        expect(tip.titleDe.trim()).not.toHaveLength(0);
        expect(tip.bodyDe.trim()).not.toHaveLength(0);
        expect(tip.callToActionDe.trim()).not.toHaveLength(0);
        expect(tip.titleEn.trim()).not.toHaveLength(0);
        expect(tip.bodyEn.trim()).not.toHaveLength(0);
        expect(tip.callToActionEn.trim()).not.toHaveLength(0);
      }
    }
  });

  it('gates every fertility-phase tip behind the dedicated fertile-window permission', () => {
    const fertilityTips = TIP_CATALOG.filter(
      (tip) => tip.applicablePhase === CyclePhase.OVULATION_ESTIMATE,
    );
    expect(fertilityTips.length).toBeGreaterThan(0);
    expect(
      fertilityTips.every((tip) => tip.requiredSharedFields.includes('shareFertileWindow')),
    ).toBe(true);
  });

  it('gates every symptom-targeted tip behind symptom sharing', () => {
    const symptomTips = TIP_CATALOG.filter(
      (tip) => (tip.applicableSymptoms?.length ?? 0) > 0,
    );
    expect(symptomTips.length).toBeGreaterThan(0);
    expect(
      symptomTips.every((tip) => tip.requiredSharedFields.includes('shareSymptoms')),
    ).toBe(true);
  });

  it('uses unique stable identifiers for safe createMany seeding', () => {
    expect(new Set(TIP_CATALOG.map((tip) => tip.id)).size).toBe(TIP_CATALOG.length);
    expect(TIP_CATALOG.length).toBeGreaterThan(50);
    expect(TIP_CATALOG.map((tip) => tip.id)).toEqual(Array.from({ length: 71 }, (_, i) => `10000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`));
  });

  it('has complete, bounded, distinct editorial copy for every ID, with no legacy fallback', () => {
    expect(Object.keys(EDITORIAL_COPY).map(Number)).toEqual(Array.from({ length: TIP_CATALOG.length }, (_, i) => i + 1));
    for (const locale of ['De', 'En'] as const) {
      expect(new Set(TIP_CATALOG.map((tip) => tip[`title${locale}`])).size).toBe(TIP_CATALOG.length);
      expect(new Set(TIP_CATALOG.map((tip) => tip[`callToAction${locale}`])).size).toBe(TIP_CATALOG.length);
      for (const tip of TIP_CATALOG) {
        expect(tip[`title${locale}`].length).toBeLessThanOrEqual(80);
        expect(tip[`body${locale}`].length).toBeLessThanOrEqual(360);
        expect(tip[`callToAction${locale}`].length).toBeLessThanOrEqual(240);
        expect(tip[`callToAction${locale}`]).not.toMatch(emptyAction);
        expect(`${tip[`body${locale}`]} ${tip[`callToAction${locale}`]}`).not.toMatch(unsafeClaim);
      }
    }
  });

  it('proves the editorial tripwires reject counterexamples, not just an empty pattern', () => {
    for (const bad of ['Frag offen nach dem Befinden.', 'Ask what would be helpful.', 'Tu heute eine kleine freundliche Sache.', 'Be there for them.']) expect(bad).toMatch(emptyAction);
    for (const bad of ['Garantiert schmerzfrei.', 'Clinically validated.', 'Nimm Ibuprofen.', 'Take paracetamol.']) expect(bad).toMatch(unsafeClaim);
    expect('Räume die Spülmaschine aus und wische die Arbeitsfläche ab.').not.toMatch(emptyAction);
  });

  it('keeps warmth optional and describes the cloth and personal temperature check in both locales', () => {
    for (const number of [11, 60]) {
      const copy = EDITORIAL_COPY[number]!;
      expect(copy[1]).toMatch(/gewünscht/);
      expect(copy[2]).toMatch(/Tuch/);
      expect(copy[2]).toMatch(/selbst prüfen/);
      expect(copy[4]).toMatch(/requested/);
      expect(copy[5]).toMatch(/cloth/);
      expect(copy[5]).toMatch(/person check/);
    }
  });

  it('preserves non-medical phase boundaries and the specific symptom-sharing gate', () => {
    const allowedFields = new Set(['sharePeriodDates', 'sharePhase', 'sharePrediction', 'shareFertileWindow', 'shareSymptoms']);
    for (const tip of TIP_CATALOG) {
      expect(Object.values(TipCategory)).toContain(tip.category);
      expect(tip.priority).toBeGreaterThanOrEqual(0);
      expect(tip.priority).toBeLessThanOrEqual(100);
      tip.requiredSharedFields.forEach((field) => expect(allowedFields.has(field)).toBe(true));
      if (!tip.applicablePhase && !tip.applicableSymptoms?.length) expect(tip.requiredSharedFields).toEqual([]);
    }
    expect(TIP_CATALOG.filter((tip) => tip.applicableSymptoms?.length)).toHaveLength(6);
    expect(TIP_CATALOG.filter((tip) => !tip.requiredSharedFields.length)).toHaveLength(40);
    expect(EDITORIAL_COPY[57]![1]).toContain('bestätigt keinen Eisprung');
    expect(EDITORIAL_COPY[58]![4]).toContain('not a contraceptive method');
  });

  it('assigns care actions only to existing IDs with no duplicate assignments', () => {
    for (const ids of Object.values(CARE_ACTION_IDS)) {
      expect(new Set(ids).size).toBe(ids.length);
      ids.forEach((id) => expect(EDITORIAL_COPY[id]).toBeDefined());
    }
    expect(CARE_ACTION_IDS.reminders).toEqual([]);
  });
});
