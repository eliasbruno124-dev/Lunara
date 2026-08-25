import {
  addMonths,
  calendarCells,
  formatDate,
  formatMonth,
  isWithin,
  monthDistance,
  monthKey,
  todayIso,
} from './date';
import { translate } from './i18n';
import type { Language, PeriodEvent, Prediction, Role } from './types';

export interface SwipePoint {
  x: number;
  y: number;
}

export function swipeMonthDirection(start: SwipePoint, end: SwipePoint): -1 | 0 | 1 {
  const deltaX = end.x - start.x;
  const deltaY = end.y - start.y;
  if (Math.abs(deltaX) < 42 || Math.abs(deltaX) <= Math.abs(deltaY) * 1.25) return 0;
  return deltaX < 0 ? 1 : -1;
}

export interface CalendarOptions {
  language: Language;
  role: Role;
  periods: PeriodEvent[];
  prediction: Prediction | null;
  initialMonth?: string;
  onSelect?: (date: string) => void;
  onMonthChange?: (month: string) => void;
}

export class SwipeCalendar {
  readonly element: HTMLElement;
  private month: string;
  private selected: string | null = null;
  private pointerStart: SwipePoint | null = null;
  private readonly firstMonth: string;
  private readonly lastMonth: string;

  constructor(private options: CalendarOptions) {
    this.month = options.initialMonth ?? monthKey();
    this.firstMonth = addMonths(monthKey(), -120);
    this.lastMonth = addMonths(monthKey(), 60);
    this.element = document.createElement('section');
    this.element.className = 'calendar-card';
    this.element.dataset.testid = 'swipe-calendar';
    this.element.tabIndex = 0;
    this.element.addEventListener('pointerdown', (event) => {
      this.pointerStart = { x: event.clientX, y: event.clientY };
    });
    this.element.addEventListener('pointerup', (event) => {
      if (!this.pointerStart) return;
      const direction = swipeMonthDirection(this.pointerStart, {
        x: event.clientX,
        y: event.clientY,
      });
      this.pointerStart = null;
      if (direction !== 0) this.move(direction);
    });
    this.element.addEventListener('pointercancel', () => {
      this.pointerStart = null;
    });
    this.element.addEventListener('keydown', (event) => {
      if (event.key === 'ArrowLeft') this.move(-1);
      if (event.key === 'ArrowRight') this.move(1);
    });
    this.render();
  }

  update(options: Partial<CalendarOptions>): void {
    this.options = { ...this.options, ...options };
    this.render();
  }

  selectedDate(): string | null {
    return this.selected;
  }

  private move(direction: -1 | 1): void {
    const next = addMonths(this.month, direction);
    if (monthDistance(this.firstMonth, next) < 0 || monthDistance(next, this.lastMonth) < 0) {
      return;
    }
    this.month = next;
    this.options.onMonthChange?.(next);
    this.element.classList.remove('slide-left', 'slide-right');
    void this.element.offsetWidth;
    this.element.classList.add(direction > 0 ? 'slide-left' : 'slide-right');
    this.render();
  }

  private render(): void {
    this.element.replaceChildren();
    const header = document.createElement('header');
    header.className = 'calendar-header';
    const heading = document.createElement('h2');
    heading.textContent = formatMonth(this.month, this.options.language);
    heading.setAttribute('aria-live', 'polite');
    const hint = document.createElement('span');
    hint.className = 'swipe-hint';
    hint.textContent = `↔ ${translate(this.options.language, 'swipeHint')}`;
    header.append(heading, hint);

    const weekdays = document.createElement('div');
    weekdays.className = 'calendar-weekdays';
    const weekdayNames = this.options.language === 'de'
      ? ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']
      : ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    for (const name of weekdayNames) {
      const label = document.createElement('span');
      label.textContent = name;
      weekdays.append(label);
    }

    const grid = document.createElement('div');
    grid.className = 'calendar-grid';
    grid.setAttribute('role', 'grid');
    grid.setAttribute('aria-label', formatMonth(this.month, this.options.language));
    for (const cell of calendarCells(this.month)) {
      if (!cell.isoDate || cell.day === null) {
        const placeholder = document.createElement('span');
        placeholder.className = 'calendar-day empty';
        placeholder.setAttribute('aria-hidden', 'true');
        grid.append(placeholder);
        continue;
      }
      grid.append(this.dayButton(cell.isoDate, cell.day));
    }
    this.element.append(header, weekdays, grid);
  }

  private dayButton(date: string, day: number): HTMLButtonElement {
    const actual = this.options.periods.some((period) =>
      isWithin(date, period.startDate, period.endDate ?? period.startDate),
    );
    const predicted = Boolean(
      this.options.prediction?.available &&
        isWithin(
          date,
          this.options.prediction.predictedStartFrom,
          this.options.prediction.predictedStartTo,
        ),
    );
    const fertile = Boolean(
      this.options.prediction?.available &&
        isWithin(
          date,
          this.options.prediction.fertileWindowFrom,
          this.options.prediction.fertileWindowTo,
        ),
    );
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'calendar-day';
    if (actual) button.classList.add('actual');
    if (predicted) button.classList.add('predicted');
    if (fertile) button.classList.add('fertile');
    if (date === todayIso()) button.classList.add('is-today');
    if (date === this.selected) button.classList.add('selected');
    button.dataset.date = date;
    const number = document.createElement('span');
    number.className = 'day-number';
    number.textContent = String(day);
    button.append(number);
    if (actual || predicted || fertile) {
      const marker = document.createElement('span');
      marker.className = 'day-marker';
      marker.setAttribute('aria-hidden', 'true');
      marker.textContent = actual ? '●' : predicted ? '≈' : '◇';
      button.append(marker);
    }
    const states = [
      actual ? translate(this.options.language, 'actualPeriod') : null,
      predicted ? translate(this.options.language, 'predictedPeriod') : null,
      fertile ? translate(this.options.language, 'fertileEstimate') : null,
    ].filter(Boolean);
    button.setAttribute(
      'aria-label',
      `${formatDate(date, this.options.language)}${states.length ? `, ${states.join(', ')}` : ''}`,
    );
    button.addEventListener('click', () => {
      this.selected = date;
      this.options.onSelect?.(date);
      this.render();
    });
    return button;
  }
}
