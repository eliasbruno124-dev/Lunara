import { CyclePhase } from '@prisma/client';
import { TIP_CATALOG } from './tip-catalog.js';

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
  });
});
