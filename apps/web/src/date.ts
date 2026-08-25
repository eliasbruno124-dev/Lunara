import type { Language } from './types';

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export function todayIso(now = new Date()): string {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function parseIsoDate(value: string): Date {
  if (!ISO_DATE.test(value)) throw new Error('Invalid local date');
  const [yearRaw, monthRaw, dayRaw] = value.split('-');
  const year = Number(yearRaw);
  const month = Number(monthRaw);
  const day = Number(dayRaw);
  const result = new Date(Date.UTC(year, month - 1, day));
  if (
    result.getUTCFullYear() !== year ||
    result.getUTCMonth() !== month - 1 ||
    result.getUTCDate() !== day
  ) {
    throw new Error('Invalid local date');
  }
  return result;
}

export function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

export function addDays(value: string, days: number): string {
  const date = parseIsoDate(value);
  date.setUTCDate(date.getUTCDate() + days);
  return toIsoDate(date);
}

export function daysBetween(from: string, to: string): number {
  return Math.round((parseIsoDate(to).getTime() - parseIsoDate(from).getTime()) / 86_400_000);
}

export function monthKey(date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

export function addMonths(month: string, offset: number): string {
  const [yearRaw, monthRaw] = month.split('-');
  const date = new Date(Date.UTC(Number(yearRaw), Number(monthRaw) - 1 + offset, 1));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;
}

export function monthDistance(from: string, to: string): number {
  const [fromYear, fromMonth] = from.split('-').map(Number);
  const [toYear, toMonth] = to.split('-').map(Number);
  return (toYear! - fromYear!) * 12 + toMonth! - fromMonth!;
}

export function formatDate(value: string, language: Language, options?: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(language === 'de' ? 'de-DE' : 'en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
    ...options,
  }).format(parseIsoDate(value));
}

export function formatMonth(value: string, language: Language): string {
  return new Intl.DateTimeFormat(language === 'de' ? 'de-DE' : 'en-GB', {
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(parseIsoDate(`${value}-01`));
}

export interface CalendarCell {
  isoDate: string | null;
  day: number | null;
}

export function calendarCells(month: string): CalendarCell[] {
  const first = parseIsoDate(`${month}-01`);
  const year = first.getUTCFullYear();
  const monthIndex = first.getUTCMonth();
  const mondayOffset = (first.getUTCDay() + 6) % 7;
  const daysInMonth = new Date(Date.UTC(year, monthIndex + 1, 0)).getUTCDate();
  const cells: CalendarCell[] = Array.from({ length: mondayOffset }, () => ({
    isoDate: null,
    day: null,
  }));
  for (let day = 1; day <= daysInMonth; day += 1) {
    cells.push({
      isoDate: `${month}-${String(day).padStart(2, '0')}`,
      day,
    });
  }
  while (cells.length % 7 !== 0) cells.push({ isoDate: null, day: null });
  return cells;
}

export function isWithin(date: string, from?: string | null, to?: string | null): boolean {
  return Boolean(from && to && date >= from && date <= to);
}
