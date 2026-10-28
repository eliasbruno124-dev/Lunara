// Local vectors follow the rounded, 24px Android navigation icons. No font or
// remote asset is needed, and the surrounding control supplies its text label.
export type IconName = 'today' | 'calendar' | 'settings' | 'preferences' | 'sharing' | 'data' | 'app';

const paths: Record<IconName, string> = {
  today: 'M3 10.5 12 3l9 7.5M5 9v12h5v-7h4v7h5V9',
  calendar: 'M5 5h14a2 2 0 0 1 2 2v13H3V7a2 2 0 0 1 2-2ZM7 3v4M17 3v4M3 10h18M7 14h1M12 14h1M17 14h1M7 17h1M12 17h1',
  settings: 'm9 3-.5 2-2 1-2-.5-2 3 1.5 1.5v3L2.5 15l2 3 2-.5 2 1L9 21h4l.5-2.5 2-1 2 .5 2-3-1.5-2v-3L19.5 8l-2-3-2 .5-2-1L13 3ZM14.5 12a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0Z',
  preferences: 'M12 3a9 9 0 1 0 9 9h-9ZM12 3v9',
  sharing: 'M16 8a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 21v-2a8 8 0 0 1 16 0v2',
  data: 'M5 3h10l4 4v14H5ZM14 3v5h5M8 12h8M8 16h8',
  app: 'M12 17v-5M12 8h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
};

export function createIcon(name: IconName, className: string): HTMLElement {
  const container = document.createElement('span');
  container.className = className;
  container.setAttribute('aria-hidden', 'true');
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '1.8');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('focusable', 'false');
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  path.setAttribute('d', paths[name]);
  svg.append(path);
  container.append(svg);
  return container;
}

export function createBrandMark(): HTMLElement {
  const mark = document.createElement('span');
  mark.className = 'brand-mark';
  mark.setAttribute('aria-hidden', 'true');
  // The existing public/icons/lunara.svg mark without the launcher tile, so
  // its ink can follow the same light/dark header colors as Android.
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '80 80 340 340');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('focusable', 'false');
  const arc = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  arc.setAttribute('d', 'M354 367 A146 146 0 1 1 354 145 M285 352 A100 100 0 0 1 220 161');
  arc.setAttribute('stroke', 'currentColor');
  arc.setAttribute('stroke-width', '14');
  arc.setAttribute('stroke-linecap', 'round');
  const dot = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
  dot.setAttribute('cx', '389');
  dot.setAttribute('cy', '205');
  dot.setAttribute('r', '19');
  dot.setAttribute('fill', '#D97762');
  svg.append(arc, dot);
  mark.append(svg);
  return mark;
}
