import { describe, expect, it } from 'vitest';
import { tipSourcePublisher } from '../tip-source';

describe('public tip background references', () => {
  it('labels the exact known sources', () => {
    for (const url of [
      'https://www.nhs.uk/symptoms/period-pain/',
      'https://www.nhs.uk/conditions/periods/fertility-in-the-menstrual-cycle/',
      'https://www.nhs.uk/conditions/pre-menstrual-syndrome/',
    ]) expect(tipSourcePublisher(url)).toBe('NHS');
    expect(tipSourcePublisher('https://womenshealth.gov/menstrual-cycle/your-menstrual-cycle')).toBe('Office on Women’s Health');
  });

  it('rejects untrusted and personalized links', () => {
    for (const url of [undefined, null, '', 'http://www.nhs.uk/symptoms/period-pain/', 'https://www.nhs.uk.evil.example/symptoms/period-pain/', 'https://user@www.nhs.uk/symptoms/period-pain/', 'https://www.nhs.uk/symptoms/period-pain/?symptom=private', 'https://www.nhs.uk/symptoms/period-pain/#private', 'javascript:alert(1)']) {
      expect(tipSourcePublisher(url)).toBeUndefined();
    }
  });
});
