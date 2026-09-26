/** Exact public references only. Never append account or health information. */
const publishers = new Map<string, string>([
  ['https://www.nhs.uk/symptoms/period-pain/', 'NHS'],
  ['https://www.nhs.uk/conditions/periods/fertility-in-the-menstrual-cycle/', 'NHS'],
  ['https://www.nhs.uk/conditions/pre-menstrual-syndrome/', 'NHS'],
  ['https://womenshealth.gov/menstrual-cycle/your-menstrual-cycle', 'Office on Women’s Health'],
]);

export function tipSourcePublisher(url: string | null | undefined): string | undefined {
  return typeof url === 'string' ? publishers.get(url) : undefined;
}
