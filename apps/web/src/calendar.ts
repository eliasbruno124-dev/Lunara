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
import { translate, type MessageKey } from './i18n';
import { hasFertileEstimate } from './fertility-presentation';
import { calendarPredictionBand, calendarPredictionContains, type CalendarPredictionBand } from './calendar-presentation';
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
  selectedDate?: string | null;
  onSelect?: (date: string) => void;
  onMonthChange?: (month: string) => void;
}

// Presentation only: recorded entries take precedence over either estimate.
// These same symbols label the day and its legend, including in forced colors.
const calendarStates = {
  actual: { marker: 'P', label: 'actualPeriod', legend: 'calendarActualLegend' },
  predicted: { marker: '≈', label: 'predictedPeriod', legend: 'calendarPredictionLegend' },
  fertile: { marker: 'F?', label: 'fertileEstimate', legend: 'calendarFertileLegend' },
} as const satisfies Record<string, { marker: string; label: MessageKey; legend: MessageKey }>;

export function createCalendarLegend(language: Language, showFertile: boolean): HTMLElement {
  const legend = document.createElement('div');
  legend.className = 'calendar-legend';
  for (const state of ['actual', 'predicted', 'fertile'] as const) {
    if (state === 'fertile' && !showFertile) continue;
    const key = document.createElement('span');
    key.className = `legend-key ${state}-key`;
    key.textContent = translate(language, calendarStates[state].legend);
    legend.append(key);
  }
  return legend;
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
    this.selected = options.selectedDate ?? null;
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
      if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
        event.preventDefault();
        this.move(event.key === 'ArrowLeft' ? -1 : 1);
        this.element.focus();
      }
    });
    this.render();
  }

  update(options: Partial<CalendarOptions>): void {
    this.options = { ...this.options, ...options };
    if (options.selectedDate !== undefined) this.selected = options.selectedDate;
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
    const controls = document.createElement('div');
    controls.className = 'calendar-controls';
    for (const direction of [-1, 1] as const) {
      const control = document.createElement('button');
      control.type = 'button';
      control.className = 'month-button';
      control.textContent = direction === -1 ? '←' : '→';
      control.setAttribute('aria-label', translate(this.options.language, direction === -1 ? 'previousMonth' : 'nextMonth'));
      control.disabled = direction === -1 ? this.month <= this.firstMonth : this.month >= this.lastMonth;
      control.addEventListener('click', () => {
        this.move(direction);
        this.element.querySelectorAll<HTMLButtonElement>('.month-button')[direction === -1 ? 0 : 1]?.focus();
      });
      controls.append(control);
    }
    const hint = document.createElement('p');
    hint.className = 'swipe-hint';
    hint.textContent = translate(this.options.language, 'swipeHint');
    header.append(heading, controls);

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
    grid.setAttribute('role', 'group');
    grid.setAttribute('aria-label', formatMonth(this.month, this.options.language));
    const predictionBand = calendarPredictionBand(this.options.prediction);
    for (const cell of calendarCells(this.month)) {
      if (!cell.isoDate || cell.day === null) {
        const placeholder = document.createElement('span');
        placeholder.className = 'calendar-day empty';
        placeholder.setAttribute('aria-hidden', 'true');
        grid.append(placeholder);
        continue;
      }
      grid.append(this.dayButton(cell.isoDate, cell.day, predictionBand));
    }
    this.element.append(header, weekdays, grid, hint);
  }

  private dayButton(date: string, day: number, predictionBand: CalendarPredictionBand | null): HTMLButtonElement {
    const actual = this.options.periods.some((period) =>
      isWithin(date, period.startDate, period.endDate ?? period.startDate),
    );
    const predicted = calendarPredictionContains(date, predictionBand);
    const fertile = Boolean(
      hasFertileEstimate(this.options.prediction) && this.options.prediction &&
        isWithin(
          date,
          this.options.prediction.fertileWindowFrom,
          this.options.prediction.fertileWindowTo,
        ),
    );
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'calendar-day';
    const state = actual ? 'actual' : predicted ? 'predicted' : fertile ? 'fertile' : null;
    if (state) button.classList.add(state);
    if (date === todayIso()) button.classList.add('is-today');
    if (date === todayIso()) button.setAttribute('aria-current', 'date');
    if (date === this.selected) button.classList.add('selected');
    button.setAttribute('aria-pressed', String(date === this.selected));
    button.dataset.date = date;
    const number = document.createElement('span');
    number.className = 'day-number';
    number.textContent = String(day);
    button.append(number);
    if (state) {
      const marker = document.createElement('span');
      marker.className = 'day-marker';
      marker.setAttribute('aria-hidden', 'true');
      marker.textContent = calendarStates[state].marker;
      button.append(marker);
    }
    // Preserve all disclosed status descriptions for assistive technology, as
    // Android does, while a recorded day wins the visible fill and marker.
    const stateLabels = [
      actual ? translate(this.options.language, calendarStates.actual.label) : null,
      predicted ? translate(this.options.language, calendarStates.predicted.label) : null,
      fertile ? translate(this.options.language, calendarStates.fertile.label) : null,
    ].filter(Boolean);
    button.setAttribute(
      'aria-label',
      `${formatDate(date, this.options.language)}${stateLabels.length ? `, ${stateLabels.join(', ')}` : ''}`,
    );
    button.addEventListener('click', () => {
      this.selected = date;
      this.options.onSelect?.(date);
      this.render();
    });
    return button;
  }
}
