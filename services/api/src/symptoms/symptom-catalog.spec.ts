import { PREDEFINED_SYMPTOMS } from './symptom-catalog.js';

describe('predefined symptom catalogue', () => {
  it('contains the twelve stable bilingual V1 symptoms without duplicate keys', () => {
    expect(PREDEFINED_SYMPTOMS).toHaveLength(12);
    expect(new Set(PREDEFINED_SYMPTOMS.map((symptom) => symptom.key)).size).toBe(12);
    for (const symptom of PREDEFINED_SYMPTOMS) {
      expect(symptom.key).toMatch(/^[A-Z_]+$/);
      expect(symptom.labelDe.trim()).not.toBe('');
      expect(symptom.labelEn.trim()).not.toBe('');
    }
  });
});
