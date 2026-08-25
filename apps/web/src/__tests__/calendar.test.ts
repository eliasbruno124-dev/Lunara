import { describe, expect, it } from 'vitest';
import { swipeMonthDirection } from '../calendar';
import { addMonths, calendarCells, isWithin, monthDistance } from '../date';

describe('calendar presentation', () => {
  it('changes to the next month on a deliberate left swipe', () => {
    expect(swipeMonthDirection({ x: 260, y: 80 }, { x: 110, y: 86 })).toBe(1);
    expect(addMonths('2026-12', 1)).toBe('2027-01');
  });

  it('changes to the previous month on a deliberate right swipe', () => {
    expect(swipeMonthDirection({ x: 80, y: 80 }, { x: 220, y: 74 })).toBe(-1);
    expect(addMonths('2026-01', -1)).toBe('2025-12');
  });

  it('does not steal a vertical scroll or small tap', () => {
    expect(swipeMonthDirection({ x: 200, y: 100 }, { x: 180, y: 260 })).toBe(0);
    expect(swipeMonthDirection({ x: 200, y: 100 }, { x: 170, y: 102 })).toBe(0);
  });

  it('builds complete Monday-first future month grids', () => {
    const cells = calendarCells('2027-02');
    expect(cells.length % 7).toBe(0);
    expect(cells.filter((cell) => cell.isoDate).length).toBe(28);
    expect(monthDistance('2026-08', '2031-08')).toBe(60);
  });

  it('marks every day in an inclusive predicted range', () => {
    expect(isWithin('2026-09-18', '2026-09-18', '2026-09-21')).toBe(true);
    expect(isWithin('2026-09-21', '2026-09-18', '2026-09-21')).toBe(true);
    expect(isWithin('2026-09-22', '2026-09-18', '2026-09-21')).toBe(false);
  });
});
