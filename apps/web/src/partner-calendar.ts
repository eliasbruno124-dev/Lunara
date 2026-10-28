import { translate, type MessageKey } from './i18n';
import type { Language, PartnerDashboard } from './types';
import { hasFertileEstimate as isFertilityVisible } from './fertility-presentation';

export function visiblePartnerCalendar(dashboard: PartnerDashboard) {
  if (dashboard.sharingPaused) return { prediction: null, periodDates: [] };
  const prediction = dashboard.calendarAccess?.fertileWindow === false && dashboard.prediction
    ? { ...dashboard.prediction, fertileWindowFrom: null, fertileWindowTo: null, fertilityEstimate: false }
    : dashboard.prediction ?? null;
  return { prediction, periodDates: dashboard.calendarAccess?.periodDates === false ? [] : dashboard.periodDates ?? [] };
}

/** Never carry a dashboard (including its permission metadata) across accounts. */
export class PartnerCalendarSnapshot {
  private identityKey: string | null = null;
  private snapshot: PartnerDashboard | null = null;

  get current(): PartnerDashboard | null {
    return this.snapshot;
  }

  selectIdentity(identityKey: string | null): void {
    this.identityKey = identityKey;
    this.snapshot = null;
  }

  update(identityKey: string, dashboard: PartnerDashboard | null): boolean {
    if (identityKey !== this.identityKey) return false;
    this.snapshot = dashboard;
    return true;
  }
}

export function partnerCalendarNotices(dashboard: PartnerDashboard | null): MessageKey[] {
  if (dashboard?.sharingPaused === true) return ['calendarSharingPaused'];
  const access = dashboard?.calendarAccess;
  const hasPeriods = Boolean(dashboard?.periodDates?.length);
  const prediction = dashboard?.prediction;
  const hasPeriodEstimate = Boolean(
    prediction?.available && prediction.predictedStartFrom && prediction.predictedStartTo,
  );
  const hasFertileEstimate = isFertilityVisible(prediction);
  // Older servers do not report permissions. Missing records cannot prove denial.
  if (!access) {
    return hasPeriods || hasPeriodEstimate || hasFertileEstimate ? [] : ['calendarDataUnavailable'];
  }
  const notices: MessageKey[] = [];
  if (access.periodDates === false) notices.push('calendarPeriodsNotShared');
  else if (access.periodDates === true && !hasPeriods) notices.push('calendarPeriodsUnavailable');
  if (access.fertileWindow === false) notices.push('calendarFertilityNotShared');
  else if (access.fertileWindow === true && !hasFertileEstimate) notices.push('calendarFertilityUnavailable');
  return notices;
}

export function createPartnerCalendarNotice(
  dashboard: PartnerDashboard | null,
  language: Language,
): HTMLElement | null {
  const notices = partnerCalendarNotices(dashboard);
  if (!notices.length) return null;
  const card = document.createElement('section');
  card.className = 'notice calendar-sharing-notice';
  card.setAttribute('role', 'status');
  for (const key of notices) {
    const paragraph = document.createElement('p');
    paragraph.textContent = translate(language, key);
    card.append(paragraph);
  }
  return card;
}

export function createPartnerCalendarAccessCard(
  dashboard: PartnerDashboard | null,
  language: Language,
): HTMLElement {
  const card = document.createElement('section');
  card.className = 'info-card setting-card calendar-access';
  const title = document.createElement('h2');
  title.textContent = translate(language, 'calendarAccessTitle');
  const description = document.createElement('p');
  description.className = 'subtle';
  description.textContent = translate(language, 'calendarAccessDescription');
  const statuses = document.createElement('dl');
  statuses.className = 'calendar-access-list';
  for (const [field, label] of [
    ['periodDates', 'calendarAccessPeriods'],
    ['fertileWindow', 'calendarAccessFertility'],
  ] as const) {
    const row = document.createElement('div');
    const term = document.createElement('dt');
    term.textContent = translate(language, label);
    const value = document.createElement('dd');
    const allowed = dashboard?.sharingPaused === true ? false : dashboard?.calendarAccess?.[field];
    value.textContent = translate(language, allowed === true
      ? 'calendarAccessAllowed'
      : allowed === false ? 'calendarAccessDenied' : 'calendarAccessUnknown');
    row.append(term, value);
    statuses.append(row);
  }
  card.append(title, description, statuses);
  if (dashboard?.sharingPaused === true) {
    const paused = document.createElement('p');
    paused.className = 'notice';
    paused.textContent = translate(language, 'calendarSharingPaused');
    card.append(paused);
  }
  return card;
}
