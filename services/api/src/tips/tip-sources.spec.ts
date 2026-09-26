import { TipCategory } from '@prisma/client';
import { TIP_CATALOG } from './tip-catalog.js';
import { catalogTipSource, SOURCE_TIP_IDS, TIP_SOURCES } from './tip-sources.js';

describe('static built-in source attribution', () => {
  const builtIns = TIP_CATALOG.map((tip) => ({ ...tip, householdId: null, createdByUserId: null }));

  it('attributes each sourced card once, to an existing built-in ID and a fixed primary URL', () => {
    const ids = Object.values(SOURCE_TIP_IDS).flat();
    expect(new Set(ids).size).toBe(ids.length);
    expect(ids).toHaveLength(23);
    const allowed = new Set([
      'https://www.nhs.uk/symptoms/period-pain/',
      'https://www.nhs.uk/conditions/periods/fertility-in-the-menstrual-cycle/',
      'https://www.nhs.uk/conditions/pre-menstrual-syndrome/',
      'https://womenshealth.gov/menstrual-cycle/your-menstrual-cycle',
    ]);
    for (const [source, numbers] of Object.entries(SOURCE_TIP_IDS)) {
      const metadata = TIP_SOURCES[source as keyof typeof TIP_SOURCES];
      expect(allowed.has(metadata.url)).toBe(true);
      for (const number of numbers) expect(catalogTipSource(builtIns[number - 1]!)).toEqual(metadata);
    }
    expect(catalogTipSource(builtIns[65]!)).toEqual(TIP_SOURCES.cycle);
    expect(catalogTipSource(builtIns[0]!)).toBeNull();
  });

  it('never attributes user-authored or household-owned content even with a colliding built-in ID', () => {
    for (const number of Object.values(SOURCE_TIP_IDS).flat()) {
      const tip = builtIns[number - 1]!;
      expect(catalogTipSource({ ...tip, category: TipCategory.USER_DEFINED })).toBeNull();
      expect(catalogTipSource({ ...tip, createdByUserId: 'tracker' })).toBeNull();
      expect(catalogTipSource({ ...tip, householdId: 'relationship' })).toBeNull();
    }
    expect(catalogTipSource({ ...builtIns[0]!, id: 'unknown' })).toBeNull();
  });

  it('keeps every educational phase-sharing card linked to its factual source', () => {
    for (const number of [51, 52, 53, 54, 55, 56, 57, 58, 59, 66, 67, 68, 69, 70, 71]) {
      expect(catalogTipSource(builtIns[number - 1]!)).not.toBeNull();
    }
  });
});
