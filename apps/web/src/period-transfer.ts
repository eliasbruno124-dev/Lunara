import { parseIsoDate, todayIso } from './date';
import type { PeriodEvent } from './types';

export const PERIOD_FILE_LIMIT = 2 * 1024 * 1024;
export const PERIOD_COUNT_LIMIT = 5_000;
export type PeriodRange = { startDate: string; endDate: string | null };
export type PeriodTransferFormat = 'csv' | 'json';
export class PeriodTransferError extends Error {
  constructor(readonly code: 'format' | 'date' | 'ambiguous' | 'size' | 'empty') { super(code); }
}

const normalized = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[\s_-]/g, '');
const starts = new Set(['startdate', 'start', 'periodstart', 'periodstartdate', 'beginn', 'startdatum', 'periodenbeginn', 'von']);
const ends = new Set(['enddate', 'end', 'periodend', 'periodenddate', 'ende', 'enddatum', 'periodenende', 'bis']);

export function normalizePeriodDate(raw: unknown): string {
  if (typeof raw !== 'string') throw new PeriodTransferError('date');
  const value = raw.trim();
  let iso = value;
  if (/^\d{4}\/\d{2}\/\d{2}$/.test(value)) iso = value.replaceAll('/', '-');
  else if (/^\d{1,2}\.\d{1,2}\.\d{4}$/.test(value)) {
    const [day, month, year] = value.split('.');
    iso = `${year}-${month!.padStart(2, '0')}-${day!.padStart(2, '0')}`;
  } else if (/^\d{1,2}\/\d{1,2}\/\d{4}$/.test(value)) {
    const [first, second, year] = value.split('/');
    const a = Number(first), b = Number(second);
    if (a <= 12 && b <= 12 && a !== b) throw new PeriodTransferError('ambiguous');
    const day = a > 12 ? a : b, month = a > 12 ? b : a;
    iso = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
  } else if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/.test(value)) {
    if (!Number.isFinite(Date.parse(value))) throw new PeriodTransferError('date');
    // A recorded calendar date must not move to another day due to timezone conversion.
    iso = value.slice(0, 10);
  }
  try { parseIsoDate(iso); } catch { throw new PeriodTransferError('date'); }
  return iso;
}

function range(raw: unknown, today: string): PeriodRange {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new PeriodTransferError('format');
  const entries = Object.entries(raw as Record<string, unknown>);
  const startFields = entries.filter(([key]) => starts.has(normalized(key)));
  const endFields = entries.filter(([key]) => ends.has(normalized(key)));
  if (startFields.length !== 1 || endFields.length > 1) throw new PeriodTransferError('format');
  const startDate = normalizePeriodDate(startFields[0]![1]);
  const end = endFields[0]?.[1];
  const endDate = end === undefined || end === null || (typeof end === 'string' && !end.trim()) ? null : normalizePeriodDate(end);
  if (startDate > today || (endDate !== null && (endDate < startDate || endDate > today))) throw new PeriodTransferError('date');
  return { startDate, endDate };
}

/** Small RFC-style CSV reader: quoted separators/newlines and escaped quotes are preserved. */
function csvRows(text: string): string[][] {
  const header = text.split(/\r?\n/, 1)[0]!;
  const counts = [',', ';', '\t'].map((separator) => {
    let quoted = false, count = 0;
    for (let i = 0; i < header.length; i++) {
      if (header[i] === '"') quoted = !quoted;
      else if (!quoted && header[i] === separator) count++;
    }
    return { separator, count };
  }).sort((a, b) => b.count - a.count);
  const separator = counts[0]!.separator;
  const rows: string[][] = []; let row: string[] = [], field = '', quoted = false, closed = false;
  for (let i = 0; i < text.length; i++) {
    const char = text[i]!;
    if (quoted) {
      if (char === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (char === '"') { quoted = false; closed = true; }
      else field += char;
      continue;
    }
    if (char === '"') {
      if (field || closed) throw new PeriodTransferError('format');
      quoted = true;
    } else if (char === separator) { row.push(field); field = ''; closed = false; }
    else if (char === '\n' || char === '\r') {
      row.push(field); if (row.some((cell) => cell.trim())) rows.push(row);
      row = []; field = ''; closed = false;
      if (char === '\r' && text[i + 1] === '\n') i++;
    } else {
      if (closed && char.trim()) throw new PeriodTransferError('format');
      if (!closed) field += char;
    }
    if (rows.length > PERIOD_COUNT_LIMIT + 1) throw new PeriodTransferError('size');
  }
  if (quoted) throw new PeriodTransferError('format');
  row.push(field); if (row.some((cell) => cell.trim())) rows.push(row);
  return rows;
}

/** Validate raw keys before JSON.parse can silently replace them; bound every value's depth. */
function validatePeriodJson(text: string): void {
  let index = 0;
  const fail = (): never => { throw new PeriodTransferError('format'); };
  const whitespace = () => { while (/[ \t\r\n]/.test(text[index] ?? '\0')) index++; };
  const string = (): string => {
    const start = index;
    if (text[index++] !== '"') return fail();
    while (index < text.length) {
      const char = text[index++];
      if (char === '"') return JSON.parse(text.slice(start, index)) as string;
      if (char === '\\') index++;
    }
    return fail();
  };
  const value = (depth: number): void => {
    if (depth > 32) fail();
    whitespace();
    const opening = text[index];
    if (opening === '{' || opening === '[') {
      index++;
      const closing = opening === '{' ? '}' : ']';
      const keys = new Set<string>();
      whitespace();
      if (text[index] === closing) { index++; return; }
      while (true) {
        if (opening === '{') {
          whitespace();
          const key = string();
          if (keys.has(key)) fail();
          keys.add(key);
          whitespace();
          if (text[index++] !== ':') fail();
        }
        value(depth + 1);
        whitespace();
        const delimiter = text[index++];
        if (delimiter === closing) return;
        if (delimiter !== ',') fail();
      }
    }
    if (opening === '"') { string(); return; }
    const primitive = /^(?:true|false|null|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?)/.exec(text.slice(index));
    if (!primitive) fail();
    index += primitive![0].length;
  };
  value(0);
  whitespace();
  if (index !== text.length) fail();
}

export function parsePeriodFile(input: string, today = todayIso()): PeriodRange[] {
  if (new TextEncoder().encode(input).length > PERIOD_FILE_LIMIT) throw new PeriodTransferError('size');
  // Tabs are CSV delimiters: trimming them would erase an empty end-date column.
  const text = input.replace(/^\uFEFF/, '').replace(/^[ \r\n]+/, '');
  if (!text.trim()) throw new PeriodTransferError('empty');
  const jsonText = text.trim();
  let values: unknown[];
  if (jsonText.startsWith('{') || jsonText.startsWith('[')) {
    let parsed: unknown;
    try { validatePeriodJson(jsonText); parsed = JSON.parse(jsonText); } catch { throw new PeriodTransferError('format'); }
    const object = parsed as { format?: unknown; formatVersion?: unknown; periods?: unknown; data?: { periods?: unknown } } | null;
    if (object?.format === 'lunara-periods' && object.formatVersion !== 1) throw new PeriodTransferError('format');
    const rows = Array.isArray(parsed) ? parsed : object?.periods ?? object?.data?.periods;
    if (!Array.isArray(rows)) throw new PeriodTransferError('format');
    values = rows;
  } else {
    const [headers, ...rows] = csvRows(text);
    if (!headers || new Set(headers.map(normalized)).size !== headers.length) throw new PeriodTransferError('format');
    values = rows.map((cells) => {
      if (cells.length !== headers.length) throw new PeriodTransferError('format');
      return Object.fromEntries(headers.map((header, index) => [header, cells[index]]));
    });
  }
  if (values.length > PERIOD_COUNT_LIMIT) throw new PeriodTransferError('size');
  if (!values.length) throw new PeriodTransferError('empty');
  return values.map((value) => range(value, today));
}

export function periodImportPlan(existing: ReadonlyArray<PeriodRange>, incoming: ReadonlyArray<PeriodRange>) {
  const added: PeriodRange[] = [], known = [...existing];
  let duplicates = 0, conflicts = 0;
  for (const item of incoming) {
    if (known.some((other) => other.startDate === item.startDate && other.endDate === item.endDate)) { duplicates++; continue; }
    // Open periods remain open until explicitly ended; never infer an end to resolve a conflict.
    if (known.some((other) => item.startDate <= (other.endDate ?? '9999-12-31') && other.startDate <= (item.endDate ?? '9999-12-31'))) conflicts++;
    else added.push({ ...item });
    known.push(item);
  }
  return { added, duplicates, conflicts };
}

export function serializePeriods(periods: ReadonlyArray<PeriodEvent>, format: PeriodTransferFormat, today = todayIso()): string {
  const active = periods.filter((period) => !period.deletedAt);
  if (active.length > PERIOD_COUNT_LIMIT) throw new PeriodTransferError('size');
  const ranges = active.map((period) => range({ startDate: period.startDate, endDate: period.endDate }, today))
    .sort((left, right) => left.startDate.localeCompare(right.startDate));
  const output = format === 'json'
    ? JSON.stringify({ format: 'lunara-periods', formatVersion: 1, periods: ranges }, null, 2) + '\n'
    : '\uFEFFstartDate,endDate\r\n' + ranges.map((period) => `${period.startDate},${period.endDate ?? ''}\r\n`).join('');
  if (new TextEncoder().encode(output).length > PERIOD_FILE_LIMIT) throw new PeriodTransferError('size');
  return output;
}
