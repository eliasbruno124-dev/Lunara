import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createPartnerCalendarAccessCard,
  createPartnerCalendarNotice,
  partnerCalendarNotices,
  PartnerCalendarSnapshot,
} from '../partner-calendar';
import { translate } from '../i18n';
import type { Language, PartnerDashboard } from '../types';
import { MinimalElement } from './minimal-dom';

const shared: PartnerDashboard = {
  calendarAccess: { periodDates: true, fertileWindow: true },
  periodDates: [{ startDate: '2026-09-10', endDate: '2026-09-12' }],
  prediction: {
    available: true, confidence: 'LOW',
    fertilityEstimate: { enabled: true, available: true, estimated: true, notContraception: true, doesNotConfirmOvulation: true, reason: 'AVAILABLE' },
    fertileWindowFrom: '2026-09-18', fertileWindowTo: '2026-09-24',
  },
};

function permissionValues(dashboard: PartnerDashboard | null, language: Language = 'en') {
  const card = createPartnerCalendarAccessCard(dashboard, language) as unknown as MinimalElement;
  return card.querySelectorAll('dd').map((value) => value.textContent);
}

describe('partner calendar explanations', () => {
  it('distinguishes both unshared fields from missing records', () => {
    expect(partnerCalendarNotices({ calendarAccess: { periodDates: false, fertileWindow: false } })).toEqual([
      'calendarPeriodsNotShared', 'calendarFertilityNotShared',
    ]);
  });

  it('explains period and fertility permissions independently', () => {
    expect(partnerCalendarNotices({ ...shared, calendarAccess: { periodDates: false, fertileWindow: true } })).toEqual(['calendarPeriodsNotShared']);
    expect(partnerCalendarNotices({ ...shared, calendarAccess: { periodDates: true, fertileWindow: false } })).toEqual(['calendarFertilityNotShared']);
  });

  it('does not call allowed but empty data unshared or lost', () => {
    expect(partnerCalendarNotices({ calendarAccess: { periodDates: true, fertileWindow: true } })).toEqual([
      'calendarPeriodsUnavailable', 'calendarFertilityUnavailable',
    ]);
    expect(partnerCalendarNotices(shared)).toEqual([]);
  });

  it.each([null, {}, { prediction: { available: false, confidence: 'NONE' as const } }])(
    'keeps missing permission metadata unknown (%j)', (dashboard) => {
      expect(partnerCalendarNotices(dashboard)).toEqual(['calendarDataUnavailable']);
    },
  );

  it('keeps old-server shared entries visible without inventing a permission denial', () => {
    expect(partnerCalendarNotices({ periodDates: shared.periodDates! })).toEqual([]);
  });

  it('reports a pause only when explicitly revealed by the server', () => {
    expect(partnerCalendarNotices({ ...shared, sharingPaused: true })).toEqual(['calendarSharingPaused']);
    expect(partnerCalendarNotices({ sharingPaused: false, calendarAccess: { periodDates: false, fertileWindow: false } }))
      .not.toContain('calendarSharingPaused');
  });

  it('requires both fertile range endpoints and a displayable prediction', () => {
    for (const prediction of [
      { available: false, confidence: 'NONE' as const, fertileWindowFrom: '2026-09-18', fertileWindowTo: '2026-09-24' },
      { available: true, confidence: 'LOW' as const, fertileWindowFrom: '2026-09-18' },
    ]) {
      expect(partnerCalendarNotices({ ...shared, prediction })).toEqual(['calendarFertilityUnavailable']);
    }
  });
});

describe('rendered calendar notices and read-only partner settings', () => {
  beforeEach(() => {
    vi.stubGlobal('document', { createElement: (tag: string) => new MinimalElement(tag) });
  });
  afterEach(() => vi.unstubAllGlobals());

  it.each(['de', 'en'] as const)('renders both localized permission statuses without toggles in %s', (language) => {
    const card = createPartnerCalendarAccessCard({ calendarAccess: { periodDates: true, fertileWindow: false } }, language) as unknown as MinimalElement;
    expect(card.querySelectorAll('dt').map((label) => label.textContent)).toEqual([
      translate(language, 'calendarAccessPeriods'), translate(language, 'calendarAccessFertility'),
    ]);
    expect(card.querySelectorAll('dd').map((value) => value.textContent)).toEqual([
      translate(language, 'calendarAccessAllowed'), translate(language, 'calendarAccessDenied'),
    ]);
    expect(card.querySelectorAll('input')).toEqual([]);
    expect(card.querySelectorAll('button')).toEqual([]);
  });

  it.each(['de', 'en'] as const)('renders localized accessible calendar notices in %s', (language) => {
    const card = createPartnerCalendarNotice({ calendarAccess: { periodDates: false, fertileWindow: false } }, language) as unknown as MinimalElement;
    expect(card.attributes.get('role')).toBe('status');
    expect(card.querySelectorAll('p').map((paragraph) => paragraph.textContent)).toEqual([
      translate(language, 'calendarPeriodsNotShared'), translate(language, 'calendarFertilityNotShared'),
    ]);
    expect(createPartnerCalendarNotice(shared, language)).toBeNull();
  });

  it('shows unknown rather than denied before loading or with an old server', () => {
    expect(permissionValues(null)).toEqual(['Status unavailable', 'Status unavailable']);
    expect(permissionValues({})).toEqual(['Status unavailable', 'Status unavailable']);
  });

  it('shows effective paused access without claiming to know underlying switch values', () => {
    const card = createPartnerCalendarAccessCard({ sharingPaused: true }, 'en') as unknown as MinimalElement;
    expect(card.querySelectorAll('dd').map((value) => value.textContent)).toEqual(['Not shared', 'Not shared']);
    expect(card.querySelectorAll('.notice')[0]?.textContent).toBe(translate('en', 'calendarSharingPaused'));
  });

  it('clears rendered permission status on identity changes and rejects a stale account response', () => {
    const snapshot = new PartnerCalendarSnapshot();
    snapshot.selectIdentity('account-A');
    expect(snapshot.update('account-A', shared)).toBe(true);
    expect(permissionValues(snapshot.current)).toEqual(['Shared', 'Shared']);
    snapshot.selectIdentity('account-B');
    expect(snapshot.current).toBeNull();
    expect(permissionValues(snapshot.current)).toEqual(['Status unavailable', 'Status unavailable']);
    expect(snapshot.update('account-A', shared)).toBe(false);
    expect(snapshot.current).toBeNull();
    snapshot.update('account-B', { calendarAccess: { periodDates: false, fertileWindow: true } });
    expect(permissionValues(snapshot.current)).toEqual(['Not shared', 'Shared']);
    snapshot.selectIdentity(null);
    expect(snapshot.update('account-B', shared)).toBe(false);
    expect(snapshot.current).toBeNull();
  });

  it('replaces revoked or missing permission metadata instead of merging old grants', () => {
    const snapshot = new PartnerCalendarSnapshot();
    snapshot.selectIdentity('account-A');
    snapshot.update('account-A', shared);
    snapshot.update('account-A', { calendarAccess: { periodDates: false, fertileWindow: false } });
    expect(permissionValues(snapshot.current)).toEqual(['Not shared', 'Not shared']);
    snapshot.update('account-A', {});
    expect(permissionValues(snapshot.current)).toEqual(['Status unavailable', 'Status unavailable']);
  });

  it('wires cached and fresh snapshots into the identity-scoped UI', () => {
    const main = readFileSync(new URL('../main.ts', import.meta.url), 'utf8');
    expect(main).toContain('this.partnerCalendar.selectIdentity(identityKey)');
    expect(main).toContain("this.partnerCalendar.update(identityKey, user.role === 'PARTNER' ? cachedPartnerDashboard : null)");
    expect(main).toContain('this.partnerCalendar.update(identityKey, dashboard)');
    expect(main).toContain('createPartnerCalendarAccessCard(this.partnerCalendar.current, this.language)');
    expect(main).toContain('createPartnerCalendarNotice(this.partnerCalendar.current, this.language)');
  });
});
