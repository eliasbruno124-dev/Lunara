import { PartnerTip, TipCategory } from '@prisma/client';

/** Sources support the short factual context, not the original date ideas or
 * the effectiveness of the catalogue. No URL is derived from personal data.
 */
export const TIP_SOURCES = {
  periodPain: { title: 'NHS: Period pain', url: 'https://www.nhs.uk/symptoms/period-pain/' },
  cycle: { title: 'NHS: Menstrual cycle', url: 'https://www.nhs.uk/conditions/periods/fertility-in-the-menstrual-cycle/' },
  pms: { title: 'NHS: PMS', url: 'https://www.nhs.uk/conditions/pre-menstrual-syndrome/' },
  cycleVariation: { title: 'Office on Women’s Health: Your menstrual cycle', url: 'https://womenshealth.gov/menstrual-cycle/your-menstrual-cycle' },
} as const;

export const SOURCE_TIP_IDS: Record<keyof typeof TIP_SOURCES, readonly number[]> = {
  periodPain: [11, 33, 35, 60],
  cycle: [51, 52, 54, 57, 58, 66, 68],
  pms: [37, 38, 39, 69, 70, 71],
  cycleVariation: [36, 53, 55, 56, 59, 67],
};

export function catalogTipSource(tip: Pick<PartnerTip, 'id' | 'category' | 'householdId' | 'createdByUserId'>) {
  if (tip.category === TipCategory.USER_DEFINED || tip.householdId !== null || tip.createdByUserId !== null) return null;
  const match = /^10000000-0000-4000-8000-0{10}(\d{2})$/.exec(tip.id);
  if (!match) return null;
  const number = Number(match[1]);
  const key = (Object.keys(SOURCE_TIP_IDS) as (keyof typeof TIP_SOURCES)[])
    .find((source) => SOURCE_TIP_IDS[source].includes(number));
  return key ? TIP_SOURCES[key] : null;
}
