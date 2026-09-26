import { CyclePhase, TipCategory } from '@prisma/client';
import { EDITORIAL_COPY } from './tip-editorial.js';

export interface CatalogTip {
  id: string;
  category: TipCategory;
  titleDe: string;
  bodyDe: string;
  callToActionDe: string;
  titleEn: string;
  bodyEn: string;
  callToActionEn: string;
  requiredSharedFields: string[];
  applicablePhase?: CyclePhase;
  applicableSymptoms?: string[];
  priority: number;
}

// Explicit numbers protect identity when entries move. Never renumber a retired
// entry or reuse its UUID: feedback and dismissals belong to that identity.
type Specification = [number: number, category: TipCategory, required?: string[], phase?: CyclePhase, symptoms?: string[]];
const specifications: Specification[] = [
  [1, TipCategory.COMMUNICATION],
  [2, TipCategory.COMMUNICATION],
  [3, TipCategory.COMMUNICATION],
  [4, TipCategory.COMMUNICATION],
  [5, TipCategory.COMMUNICATION],
  [6, TipCategory.PRACTICAL_HELP],
  [7, TipCategory.PRACTICAL_HELP],
  [8, TipCategory.PRACTICAL_HELP],
  [9, TipCategory.PRACTICAL_HELP],
  [10, TipCategory.PRACTICAL_HELP],
  [11, TipCategory.COMFORT],
  [12, TipCategory.COMFORT],
  [13, TipCategory.COMFORT],
  [14, TipCategory.COMFORT],
  [15, TipCategory.COMFORT],
  [16, TipCategory.SPACE],
  [17, TipCategory.SPACE],
  [18, TipCategory.SPACE],
  [19, TipCategory.SPACE],
  [20, TipCategory.SPACE],
  [21, TipCategory.FOOD_AND_DRINK],
  [22, TipCategory.FOOD_AND_DRINK],
  [23, TipCategory.FOOD_AND_DRINK],
  [24, TipCategory.FOOD_AND_DRINK],
  [25, TipCategory.FOOD_AND_DRINK],
  [26, TipCategory.EMOTIONAL_SUPPORT],
  [27, TipCategory.EMOTIONAL_SUPPORT],
  [28, TipCategory.EMOTIONAL_SUPPORT],
  [29, TipCategory.EMOTIONAL_SUPPORT],
  [30, TipCategory.EMOTIONAL_SUPPORT],
  [31, TipCategory.PERIOD_DAYS, ['sharePeriodDates'], CyclePhase.MENSTRUATION],
  [32, TipCategory.PERIOD_DAYS, ['sharePeriodDates'], CyclePhase.MENSTRUATION],
  [33, TipCategory.PERIOD_DAYS, ['sharePeriodDates'], CyclePhase.MENSTRUATION],
  [34, TipCategory.PERIOD_DAYS, ['sharePeriodDates'], CyclePhase.MENSTRUATION],
  [35, TipCategory.PERIOD_DAYS, ['sharePeriodDates'], CyclePhase.MENSTRUATION],
  [36, TipCategory.PREMENSTRUAL_ESTIMATE, ['sharePrediction'], CyclePhase.PREMENSTRUAL_ESTIMATE],
  [37, TipCategory.PREMENSTRUAL_ESTIMATE, ['sharePrediction'], CyclePhase.PREMENSTRUAL_ESTIMATE],
  [38, TipCategory.PREMENSTRUAL_ESTIMATE, ['sharePrediction'], CyclePhase.PREMENSTRUAL_ESTIMATE],
  [39, TipCategory.PREMENSTRUAL_ESTIMATE, ['sharePrediction'], CyclePhase.PREMENSTRUAL_ESTIMATE],
  [40, TipCategory.PREMENSTRUAL_ESTIMATE, ['sharePrediction'], CyclePhase.PREMENSTRUAL_ESTIMATE],
  [41, TipCategory.GENERAL],
  [42, TipCategory.GENERAL],
  [43, TipCategory.GENERAL],
  [44, TipCategory.GENERAL],
  [45, TipCategory.GENERAL],
  [46, TipCategory.COMMUNICATION],
  [47, TipCategory.PRACTICAL_HELP],
  [48, TipCategory.COMFORT],
  [49, TipCategory.SPACE],
  [50, TipCategory.FOOD_AND_DRINK],
  [51, TipCategory.GENERAL, ['sharePhase'], CyclePhase.FOLLICULAR_ESTIMATE],
  [52, TipCategory.PRACTICAL_HELP, ['sharePhase'], CyclePhase.FOLLICULAR_ESTIMATE],
  [53, TipCategory.COMMUNICATION, ['sharePhase'], CyclePhase.FOLLICULAR_ESTIMATE],
  [54, TipCategory.GENERAL, ['sharePhase'], CyclePhase.LUTEAL_ESTIMATE],
  [55, TipCategory.COMMUNICATION, ['sharePhase'], CyclePhase.LUTEAL_ESTIMATE],
  [56, TipCategory.PRACTICAL_HELP, ['sharePhase'], CyclePhase.LUTEAL_ESTIMATE],
  [57, TipCategory.GENERAL, ['shareFertileWindow'], CyclePhase.OVULATION_ESTIMATE],
  [58, TipCategory.COMMUNICATION, ['shareFertileWindow'], CyclePhase.OVULATION_ESTIMATE],
  [59, TipCategory.EMOTIONAL_SUPPORT, ['shareFertileWindow'], CyclePhase.OVULATION_ESTIMATE],
  [60, TipCategory.COMFORT, ['shareSymptoms'], undefined, ['CRAMPS', 'Krämpfe', 'Cramps']],
  [61, TipCategory.COMFORT, ['shareSymptoms'], undefined, ['HEADACHE', 'Kopfschmerzen', 'Headache']],
  [62, TipCategory.PRACTICAL_HELP, ['shareSymptoms'], undefined, ['FATIGUE', 'Müdigkeit', 'Fatigue']],
  [63, TipCategory.FOOD_AND_DRINK, ['shareSymptoms'], undefined, ['NAUSEA', 'Übelkeit', 'Nausea']],
  [64, TipCategory.EMOTIONAL_SUPPORT, ['shareSymptoms'], undefined, ['STRESS', 'Stress']],
  [65, TipCategory.COMFORT, ['shareSymptoms'], undefined, ['BACK_PAIN', 'Rückenschmerzen', 'Back pain']],
  [66, TipCategory.PERIOD_DAYS, ['sharePhase'], CyclePhase.MENSTRUATION],
  [67, TipCategory.COMMUNICATION, ['sharePhase'], CyclePhase.MENSTRUATION],
  [68, TipCategory.PRACTICAL_HELP, ['sharePhase'], CyclePhase.MENSTRUATION],
  [69, TipCategory.PREMENSTRUAL_ESTIMATE, ['sharePhase'], CyclePhase.PREMENSTRUAL_ESTIMATE],
  [70, TipCategory.COMMUNICATION, ['sharePhase'], CyclePhase.PREMENSTRUAL_ESTIMATE],
  [71, TipCategory.PRACTICAL_HELP, ['sharePhase'], CyclePhase.PREMENSTRUAL_ESTIMATE],
];

if (specifications.length < 50 ||
    Object.keys(EDITORIAL_COPY).length !== specifications.length ||
    specifications.some(([number]) => !EDITORIAL_COPY[number])) {
  throw new Error('Every built-in tip must have complete bilingual editorial copy');
}

export const TIP_CATALOG: CatalogTip[] = specifications.map(([number, category, required, phase, symptoms]) => {
  const [titleDe, bodyDe, callToActionDe, titleEn, bodyEn, callToActionEn] = EDITORIAL_COPY[number]!;
  return {
    id: `10000000-0000-4000-8000-${String(number).padStart(12, '0')}`,
    category,
    titleDe, bodyDe, callToActionDe, titleEn, bodyEn, callToActionEn,
    requiredSharedFields: required ?? [],
    applicablePhase: phase,
    applicableSymptoms: symptoms ?? [],
    priority: 101 - number,
  };
});
