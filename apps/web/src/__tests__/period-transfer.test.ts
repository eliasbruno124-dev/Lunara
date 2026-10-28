import { describe, expect, it, vi } from 'vitest';
import { normalizePeriodDate, parsePeriodFile, periodImportPlan, serializePeriods, PERIOD_FILE_LIMIT } from '../period-transfer';

const today = '2026-10-28';
const periods = [
  { id: 'private-id-a', startDate: '2026-09-01', endDate: '2026-09-05', pending: true },
  { id: 'private-id-b', startDate: '2026-10-01', endDate: null },
];
const ranges = periods.map(({ startDate, endDate }) => ({ startDate, endDate }));

describe('portable recorded periods', () => {
  it.each(['csv', 'json'] as const)('round trips %s including ongoing and pending periods without identities', (format) => {
    const data = serializePeriods([...periods].reverse(), format, today);
    expect(parsePeriodFile(data, today)).toEqual(ranges);
    expect(data).not.toContain('private-id'); expect(data).not.toContain('pending');
  });
  it('does not export deleted records or extra personal fields', () => {
    const result = serializePeriods([...periods, { ...periods[0]!, deletedAt: today }], 'json', today);
    expect(JSON.parse(result).periods).toHaveLength(2);
    expect(Object.keys(JSON.parse(result).periods[0])).toEqual(['startDate', 'endDate']);
  });
  it.each([',', ';', '\t'])('handles quotes, BOM, CRLF and German aliases with %j', (separator) => {
    expect(parsePeriodFile(`\uFEFF"Periodenbeginn"${separator}"Enddatum"\r\n"01.09.2026"${separator}"05.09.2026"\r\n`, today)).toEqual([ranges[0]]);
  });
  it('ignores extra quoted metadata, including escaped quotes and line breaks', () => {
    expect(parsePeriodFile('start_date,end_date,note\n2026-09-01,2026-09-05,"a,b\n""private"""', today)).toEqual([ranges[0]]);
  });
  it('accepts start-only CSV as explicitly ongoing', () => {
    expect(parsePeriodFile('Beginn\n01.10.2026', today)).toEqual([ranges[1]]);
  });
  it.each(['', '\n', '\r\n'])('preserves a terminal TSV empty end date before %j', (lineEnd) => {
    expect(parsePeriodFile(`startDate\tendDate\n2026-10-01\t${lineEnd}`, today)).toEqual([ranges[1]]);
  });
  it('preserves an empty leading TSV metadata column and accepts leading JSON whitespace', () => {
    expect(parsePeriodFile('\tstartDate\tendDate\n\t2026-10-01\t', today)).toEqual([ranges[1]]);
    expect(parsePeriodFile(`\t\r\n${JSON.stringify(ranges)}\t`, today)).toEqual(ranges);
  });
  it.each([
    JSON.stringify(ranges), JSON.stringify({ periods: ranges }), JSON.stringify({ data: { periods: ranges } }),
    JSON.stringify({ format: 'lunara-backup', periods: ranges, account: { username: 'discarded' } }),
  ])('reads recorded periods in supported JSON envelopes', (input) => expect(parsePeriodFile(input, today)).toEqual(ranges));
  it.each(['2026-09-20', '2026/09/20', '20.09.2026', '20/09/2026', '09/20/2026', '2026-09-20T00:00:00+02:00'])('normalizes %s without moving the calendar date', (value) => expect(normalizePeriodDate(value)).toBe('2026-09-20'));
  it('refuses an ambiguous slash date', () => expect(() => normalizePeriodDate('03/04/2026')).toThrow('ambiguous'));
  it.each(['2026-02-30', '31.04.2026', '20/20/2026', '=HYPERLINK("x")', '2026-09-20T30:90:00Z', 'not-a-date'])('rejects invalid or executable date %s', (value) => expect(() => normalizePeriodDate(value)).toThrow());
  it.each([
    'startDate,endDate\n2026-11-01,', 'startDate,endDate\n2026-10-01,2026-11-01',
    'startDate,endDate\n2026-09-05,2026-09-01', 'start,start_date\n2026-09-01,2026-09-01',
    'startDate,endDate\n"2026-09-01,', 'startDate,endDate\n2026-09-01,2026-09-05,extra',
    '{"prediction":{"startDate":"2026-09-01"}}', '{"format":"lunara-periods","formatVersion":99,"periods":[]}',
  ])('rejects malformed data rather than guessing', (input) => expect(() => parsePeriodFile(input, today)).toThrow());
  it('bounds bytes and record count before import', () => {
    expect(() => parsePeriodFile(' '.repeat(PERIOD_FILE_LIMIT + 1), today)).toThrow('size');
    expect(() => parsePeriodFile(JSON.stringify(Array.from({ length: 5001 }, () => ranges[0])), today)).toThrow('size');
  });
  it.each([
    '[{"startDate":"2026-09-01","startDate":"2026-10-01"}]',
    '[{"startDate":"2026-09-01","start\\u0044ate":"2026-10-01"}]',
    '{"periods":[],"periods":[{"startDate":"2026-09-01"}]}',
    '{"periods":[{"startDate":"2026-09-01","extra":{"same":1,"same":2}}]}',
  ])('rejects duplicate JSON keys before they can select a date', (input) => {
    expect(() => parsePeriodFile(input, today)).toThrow('format');
  });
  it('bounds JSON nesting at depth 32, including ignored metadata', () => {
    const nested = (arrays: number) => `{"periods":${JSON.stringify(ranges)},"extra":${'['.repeat(arrays)}null${']'.repeat(arrays)}}`;
    expect(parsePeriodFile(nested(31), today)).toEqual(ranges);
    expect(() => parsePeriodFile(nested(32), today)).toThrow('format');
    expect(() => parsePeriodFile(nested(20_000), today)).toThrow('format');
  });
  it('accepts escaped JSON strings and repeated names in separate objects', () => {
    expect(parsePeriodFile(JSON.stringify({ periods: ranges, extra: { quoted: 'a\\"b', first: { name: true }, second: { name: null }, number: -1.5e3 } }), today)).toEqual(ranges);
  });
  it.each(['csv', 'json'] as const)('bounds %s export count while retaining the 5000-record round trip', (format) => {
    const maximum = Array.from({ length: 5000 }, () => periods[0]!);
    expect(parsePeriodFile(serializePeriods(maximum, format, today), today)).toHaveLength(5000);
    expect(() => serializePeriods([...maximum, periods[0]!], format, today)).toThrow('size');
    expect(parsePeriodFile(serializePeriods([...maximum, { ...periods[0]!, deletedAt: today }], format, today), today)).toHaveLength(5000);
  });
  it('rejects serialized output above the byte ceiling even if record count is small', () => {
    // Current date-only rows fit below 2 MiB at the count limit; guard future serializer growth too.
    const padded = `${' '.repeat(PERIOD_FILE_LIMIT)}${JSON.stringify({ periods: ranges })}`;
    const stringify = vi.spyOn(JSON, 'stringify').mockReturnValueOnce(padded);
    try { expect(() => serializePeriods(periods, 'json', today)).toThrow('size'); }
    finally { stringify.mockRestore(); }
  });
  it('skips exact duplicates from file and existing history', () => {
    expect(periodImportPlan([ranges[0]!], [ranges[0]!, ranges[1]!, ranges[1]!])).toEqual({ added: [ranges[1]], duplicates: 2, conflicts: 0 });
  });
  it('detects partial overlap, shared boundary, open periods and incoming conflicts without mutation', () => {
    const existing = structuredClone(ranges);
    const plan = periodImportPlan(existing, [
      { startDate: '2026-09-05', endDate: '2026-09-10' },
      { startDate: '2026-10-20', endDate: '2026-10-22' },
    ]);
    expect(plan.conflicts).toBe(2); expect(existing).toEqual(ranges);
    expect(periodImportPlan([], [ranges[0]!, { startDate: '2026-09-03', endDate: '2026-09-06' }]).conflicts).toBe(1);
  });
});
