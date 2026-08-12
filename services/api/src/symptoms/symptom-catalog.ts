export interface PredefinedSymptom {
  key: string;
  labelDe: string;
  labelEn: string;
}

export const PREDEFINED_SYMPTOMS: readonly PredefinedSymptom[] = Object.freeze([
  { key: 'CRAMPS', labelDe: 'Krämpfe', labelEn: 'Cramps' },
  { key: 'HEADACHE', labelDe: 'Kopfschmerzen', labelEn: 'Headache' },
  { key: 'BACK_PAIN', labelDe: 'Rückenschmerzen', labelEn: 'Back pain' },
  { key: 'FATIGUE', labelDe: 'Müdigkeit', labelEn: 'Fatigue' },
  { key: 'CRAVINGS', labelDe: 'Heißhunger', labelEn: 'Cravings' },
  { key: 'NAUSEA', labelDe: 'Übelkeit', labelEn: 'Nausea' },
  { key: 'BREAST_TENDERNESS', labelDe: 'Empfindliche Brust', labelEn: 'Breast tenderness' },
  { key: 'SKIN_CHANGES', labelDe: 'Unreine Haut', labelEn: 'Skin changes' },
  { key: 'IRRITABILITY', labelDe: 'Reizbarkeit', labelEn: 'Irritability' },
  { key: 'STRESS', labelDe: 'Stress', labelEn: 'Stress' },
  { key: 'GOOD_MOOD', labelDe: 'Gute Stimmung', labelEn: 'Good mood' },
  { key: 'HIGH_ENERGY', labelDe: 'Hohe Energie', labelEn: 'High energy' },
]);
