import { registerSW } from 'virtual:pwa-register';
import './styles.css';
import { LunaraApi, ApiError } from './api';
import { purgeBeforeIdentitySwitch, serverIdentityKey } from './account-isolation';
import {
  assertImportSize,
  defaultPortableExtras,
  decryptPortableBackup,
  encryptPortableBackup,
  mergeDailyLogs,
  mergePeriods,
  type PortableBackupExtras,
} from './backup';
import { createCalendarLegend, SwipeCalendar } from './calendar';
import { createBrandMark, createIcon, type IconName } from './ui-icons';
import { createApkDownloadCard } from './apk-download';
import { hasCurrentFertileEstimate, hasFertileEstimate } from './fertility-presentation';
import { periodEndState, periodForDate, periodRangesOverlap } from './period-end';
import { parsePeriodFile, periodImportPlan, serializePeriods, PeriodTransferError, PERIOD_FILE_LIMIT, type PeriodRange, type PeriodTransferFormat } from './period-transfer';
import {
  createPartnerCalendarAccessCard,
  createPartnerCalendarNotice,
  PartnerCalendarSnapshot,
  visiblePartnerCalendar,
} from './partner-calendar';
import { tipSourcePublisher } from './tip-source';
import { formatDate, monthKey, parseIsoDate, todayIso } from './date';
import {
  clearLocalPrivateData,
  clearServerHealthData,
  clearServerPrivateData,
  getLocalVaultEnvelope,
  periodStore,
  persistLocalVaultEnvelope,
  queueStorage,
  settingsStore,
} from './db';
import { translate, type MessageKey } from './i18n';
import { IdentityEpochCoordinator, StaleIdentityError } from './identity-epoch';
import { LatestRequestCoordinator, SupersededRequestError } from './latest-request';
import {
  captureInvitationHash,
  clearPendingInvitation,
  hasUnresolvedInvitationMutations,
  pendingInvitation,
  storeInvitation,
} from './invitation-link';
import { calculateLocalPrediction } from './local-prediction';
import { millisecondsUntilNextPredictionDay, predictionReferenceDate, predictionSummary } from './prediction-freshness';
import {
  LocalVaultSession,
  type LocalVaultEnvelope,
  type LocalVaultState,
} from './local-vault';
import { LocalVaultCoordinator } from './local-vault-coordinator';
import {
  canStartOidc,
  finishOidcCallback,
  normalizeInvitationCode,
  startOidc,
  supportsOidcEnrollment,
} from './oidc';
import { PeriodSyncEngine, queuePeriodDelete, queuePeriodEnd, queuePeriodStart, type SyncState } from './sync';
import type {
  CycleProfile,
  SharingSettings,
  SpecialMode,
  Language,
  InvitationResponse,
  MetaResponse,
  OidcEnrollmentIntent,
  PartnerDashboard,
  PartnerTip,
  PeriodEvent,
  Prediction,
  UserIdentity,
} from './types';

type Screen = 'today' | 'calendar' | 'settings';
type ThemePreference = 'system' | 'light' | 'dark';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

function element<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  className?: string,
  text?: string,
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function button(className: string, text: string, action: () => void | Promise<void>): HTMLButtonElement {
  const node = element('button', className, text);
  node.type = 'button';
  node.addEventListener('click', () => void action());
  return node;
}

class LunaraWebApp {
  private readonly root: HTMLElement;
  private readonly api = new LunaraApi(window.location.origin);
  private readonly sync = new PeriodSyncEngine(this.api);
  private readonly serverIdentities = new IdentityEpochCoordinator();
  private readonly refreshRequests = new LatestRequestCoordinator();
  private meta: MetaResponse | null = null;
  private user: UserIdentity | null = null;
  private periods: PeriodEvent[] = [];
  private prediction: Prediction | null = null;
  private partnerTip: PartnerTip | null = null;
  private readonly partnerCalendar = new PartnerCalendarSnapshot();
  private screen: Screen = 'today';
  private selectedDate: string | null = null;
  private calendarMonth = monthKey();
  // New browsers start in English; an explicit choice is kept in localStorage.
  private language: Language = localStorage.getItem('lunara-language') === 'de' ? 'de' : 'en';
  private theme: ThemePreference = this.loadTheme();
  private readonly expandedSettings = new Set(['preferences']);
  private syncState: SyncState = { pending: 0, failed: 0, lastSuccessfulAt: null };
  private installPrompt: BeforeInstallPromptEvent | null = null;
  private syncing = false;
  private initError: string | null = null;
  private joinExpanded = false;
  // Period whose deletion waits for the second, confirming tap.
  private confirmDeletePeriodId: string | null = null;
  // Sharing switches and cycle profile of the signed-in tracker, loaded on first view.
  private sharingState: { userId: string; settings: SharingSettings | null; failed: boolean } | null = null;
  private profileState: { userId: string; profile: CycleProfile | null; failed: boolean } | null = null;
  private settingsMessage: { area: 'sharing' | 'profile'; text: string; error: boolean } | null = null;
  // Provider token held only in memory until the password confirms the connection.
  private pendingOidcLink: { token: string; userId: string; receivedAt: number } | null = null;
  private oidcStatus: { linked: boolean } | null = null;
  // Account the status above belongs to; another account loads its own.
  private oidcStatusUserId: string | null = null;
  private oidcLinkMessage: { text: string; error: boolean } | null = null;
  private localMode = false;
  private localVaultAvailable = false;
  private readonly localVault = new LocalVaultSession();
  private localExtras: PortableBackupExtras = defaultPortableExtras();
  private importReport: string | null = null;
  private vaultLockTimer: number | null = null;
  private predictionDayTimer: number | null = null;
  private readonly localVaultCoordinator = new LocalVaultCoordinator();
  private loginChoice: 'server' | 'local' | null = null;
  private invitationCode: string | null = null;
  private invitationError: 'invalid' | 'conflict' | 'failed' | null = null;
  private acceptingInvitation = false;
  private partnerConnected: boolean | null = null;
  private invitationOverviewLoading = false;
  private invitationOverviewFailed = false;
  private invitationCreationBusy = false;
  private createdInvitation: InvitationResponse | null = null;
  private createdInvitationLink: string | null = null;
  private invitationCreationError = false;
  private invitationLinkRejected = false;

  constructor() {
    const root = document.querySelector<HTMLElement>('#app');
    if (!root) throw new Error('Application root is missing');
    this.root = root;
    this.applyTheme();
    window.addEventListener('online', () => {
      this.render();
      if (this.user?.role === 'TRACKER') void this.performSync(false);
      else void this.refreshData();
    });
    window.addEventListener('offline', () => this.render());
    window.addEventListener('beforeinstallprompt', (event) => {
      event.preventDefault();
      this.installPrompt = event as BeforeInstallPromptEvent;
      this.render();
    });
    this.sync.addEventListener('synced', (event) => {
      this.syncState = (event as CustomEvent<SyncState>).detail;
    });
    for (const eventName of ['pointerdown', 'keydown', 'touchstart'] as const) {
      window.addEventListener(eventName, () => this.armVaultLock(), { passive: true });
    }
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') {
        this.clearPredictionDayTimer();
        this.lockLocalVault();
      } else this.refreshPredictionDate();
    });
    window.addEventListener('pagehide', () => {
      this.clearPredictionDayTimer();
      this.lockLocalVault();
    });
    window.addEventListener('pageshow', () => this.refreshPredictionDate());
  }

  async start(): Promise<void> {
    this.renderLoading();
    this.localMode = (await settingsStore.get<boolean>('local-mode')) ?? false;
    await this.keepLegacyLanguage();
    const captured = captureInvitationHash(window.location.hash, sessionStorage);
    if (captured !== 'absent') history.replaceState({}, '', `${window.location.pathname}${window.location.search}`);
    if (captured === 'invalid') this.invitationError = 'invalid';
    this.invitationCode = pendingInvitation(sessionStorage)?.invitationCode ?? null;
    // An invitation is an explicit request to use the server. Keep a local
    // vault intact and do not persist this mode switch until acceptance wins.
    if (this.invitationCode) {
      this.localMode = false;
      this.loginChoice = 'server';
      this.meta = (await this.fetchMeta()) ?? (await settingsStore.get<MetaResponse>('meta')) ?? null;
      if (this.meta) await settingsStore.put('meta', this.meta);
    }
    if (this.localMode) {
      this.localVaultAvailable = Boolean(await getLocalVaultEnvelope<LocalVaultEnvelope>());
      this.loginChoice = 'local';
      this.render();
      return;
    }

    try {
      const oidcResult = await finishOidcCallback(this.api);
      if (oidcResult && 'linkAccessToken' in oidcResult) {
        this.pendingOidcLink = {
          token: oidcResult.linkAccessToken,
          userId: oidcResult.linkUserId,
          receivedAt: oidcResult.receivedAt,
        };
        // Show the waiting password confirmation right away.
        this.screen = 'settings';
        this.expandedSettings.add('app');
      } else if (oidcResult) {
        await this.rememberUser(oidcResult.user);
      }
    } catch {
      this.initError = this.t('oidcLoginFailed');
    }

    const cachedUser = (await settingsStore.get<UserIdentity>('user')) ?? null;
    if (!this.user && (await this.api.restoreSession())) {
      let restoredUser: UserIdentity | null = null;
      try {
        restoredUser = navigator.onLine ? await this.api.me() : cachedUser;
      } catch (error) {
        if (error instanceof ApiError && error.status === 0) restoredUser = cachedUser;
      }
      if (restoredUser) await this.rememberUser(restoredUser);
    }
    // A provider result is only valid for the account that started connecting it.
    if (this.pendingOidcLink && this.pendingOidcLink.userId !== this.user?.id) this.pendingOidcLink = null;
    if (this.user) {
      // Settings need the server's capabilities (e.g. Authentik) after a reload too.
      if (!this.meta) this.meta = (await this.fetchMeta()) ?? (await settingsStore.get<MetaResponse>('meta')) ?? null;
      await this.loadCachedData();
      if (navigator.onLine) await this.refreshData();
      if (this.user.role === 'TRACKER' && navigator.onLine) void this.performSync(false);
    }
    this.render();
    window.setInterval(() => {
      if (!this.user || !navigator.onLine || document.visibilityState !== 'visible') return;
      if (this.user.role === 'TRACKER') void this.performSync(false);
      else void this.refreshData();
    }, 60_000);
  }

  private t(key: MessageKey, values?: Record<string, string | number>): string {
    return translate(this.language, key, values);
  }

  private async fetchMeta(): Promise<MetaResponse | null> {
    try {
      return await this.api.meta();
    } catch {
      return null;
    }
  }

  private rememberUser(user: UserIdentity): Promise<boolean> {
    const identityKey = serverIdentityKey(window.location.origin, user);
    return this.serverIdentities.transition(identityKey, async () => {
      const previous = (await settingsStore.get<UserIdentity>('user')) ?? null;
      await purgeBeforeIdentitySwitch(
        window.location.origin,
        previous,
        user,
        clearServerHealthData,
      );
      // An explicit choice in this browser wins; otherwise follow the account.
      if (localStorage.getItem('lunara-language') === null) {
        this.language = user.preferredLanguage === 'EN' ? 'en' : user.preferredLanguage === 'DE' ? 'de' : this.language;
      }
      document.documentElement.lang = this.language;
      await Promise.all([
        settingsStore.put('user', user),
        settingsStore.put('language', this.language),
      ]);
      this.periods = [];
      this.prediction = null;
      this.partnerTip = null;
      this.partnerCalendar.selectIdentity(identityKey);
      this.syncState = { pending: 0, failed: 0, lastSuccessfulAt: null };
      this.partnerConnected = null;
      this.invitationOverviewFailed = false;
      this.createdInvitation = null;
      this.createdInvitationLink = null;
      this.invitationCreationError = false;
      this.invitationLinkRejected = false;
      this.user = user;
    });
  }

  private async loadCachedData(): Promise<void> {
    if (this.localMode) {
      if (!this.localVault.unlocked) return;
      this.prediction = calculateLocalPrediction(this.periods, predictionReferenceDate(), this.localExtras.profile);
      this.partnerTip = null;
      this.partnerCalendar.selectIdentity(null);
      this.syncState = { pending: 0, failed: 0, lastSuccessfulAt: null };
      return;
    }
    const user = this.user;
    if (!user) return;
    const identityKey = serverIdentityKey(window.location.origin, user);
    try {
      await this.serverIdentities.run(identityKey, async (assertCurrent) => {
        let periods = await periodStore.all('server');
        let prediction = (await settingsStore.get<Prediction>('prediction')) ?? null;
        const cachedPartnerDashboard =
          (await settingsStore.get<PartnerDashboard>('partner-dashboard')) ?? null;
        if (user.role === 'PARTNER' && cachedPartnerDashboard) {
          const visible = visiblePartnerCalendar(cachedPartnerDashboard);
          prediction = visible.prediction;
          periods = visible.periodDates.map((period, index) => ({
            id: `shared-${index}-${period.startDate}`,
            startDate: period.startDate,
            endDate: period.endDate,
          }));
        }
        const partnerTip = (await settingsStore.get<PartnerTip>('partner-tip')) ?? null;
        const mutations = await queueStorage.getMutations();
        const lastSuccessfulAt = (await settingsStore.get<string>('last-sync')) ?? null;
        assertCurrent();
        this.periods = periods;
        this.prediction = prediction;
        this.partnerTip = partnerTip;
        this.partnerCalendar.update(identityKey, user.role === 'PARTNER' ? cachedPartnerDashboard : null);
        this.syncState = {
          pending: mutations.filter((item) => item.state === 'PENDING').length,
          failed: mutations.filter((item) => item.state === 'FAILED').length,
          lastSuccessfulAt,
        };
      });
    } catch (error) {
      if (!(error instanceof StaleIdentityError)) throw error;
    }
  }

  private async refreshData(): Promise<void> {
    const user = this.user;
    if (!user || this.localMode || !navigator.onLine) return;
    const identityKey = serverIdentityKey(window.location.origin, user);
    const request = this.refreshRequests.begin();
    try {
      await this.serverIdentities.run(identityKey, async (assertIdentity) => {
        const assertCurrent = () => { assertIdentity(); request.assertCurrent(); };
        if (user.role === 'TRACKER') {
          const [periods, prediction] = await Promise.all([
            this.api.periods(),
            this.api.prediction(),
          ]);
          await request.commit(async () => {
            assertCurrent();
            await Promise.all([
              periodStore.replace(periods),
              settingsStore.put('prediction', prediction),
            ]);
            assertCurrent();
            this.periods = periods;
            this.prediction = prediction;
          });
        } else {
          const [dashboard, tip] = await Promise.all([
            this.api.partnerDashboard(),
            this.api.todayTip().catch(() => null),
          ]);
          await request.commit(async () => {
            assertCurrent();
            const visible = visiblePartnerCalendar(dashboard);
            const prediction = visible.prediction;
            const periods = visible.periodDates.map((period, index) => ({
              id: `shared-${index}-${period.startDate}`,
              startDate: period.startDate,
              endDate: period.endDate,
            }));
            await Promise.all([
              settingsStore.put('partner-dashboard', dashboard),
              settingsStore.put('partner-tip', tip),
              settingsStore.put('prediction', prediction),
            ]);
            assertCurrent();
            this.partnerTip = tip;
            this.partnerCalendar.update(identityKey, dashboard);
            this.prediction = prediction;
            this.periods = periods;
          });
        }
      });
      if (request.isCurrent()) this.render();
    } catch (error) {
      if (!request.isCurrent() || error instanceof SupersededRequestError || error instanceof StaleIdentityError) return;
      if (error instanceof ApiError && error.status === 401) {
        await this.handleUnauthorized(identityKey);
      }
      return;
    }
  }

  private async performSync(force: boolean): Promise<void> {
    const user = this.user;
    if (
      !user ||
      this.localMode ||
      user.role !== 'TRACKER' ||
      !navigator.onLine ||
      this.syncing
    ) return;
    const identityKey = serverIdentityKey(window.location.origin, user);
    let stale = false;
    this.syncing = true;
    this.render();
    try {
      await this.serverIdentities.run(identityKey, async (assertCurrent) => {
        const state = await this.sync.flush(force, assertCurrent);
        assertCurrent();
        this.syncState = state;
      });
      await this.refreshData();
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        await this.handleUnauthorized(identityKey);
      }
      stale = error instanceof StaleIdentityError;
    } finally {
      this.syncing = false;
      if (!stale) this.render();
    }
  }

  private async handleUnauthorized(expectedIdentityKey: string): Promise<void> {
    const cleared = await this.serverIdentities.transitionIfCurrent(
      expectedIdentityKey,
      null,
      async () => {
        await Promise.all([this.api.forgetSession(), clearServerPrivateData()]);
        this.user = null;
        this.periods = [];
        this.prediction = null;
        this.partnerTip = null;
        this.partnerCalendar.selectIdentity(null);
        this.syncState = { pending: 0, failed: 0, lastSuccessfulAt: null };
        this.screen = 'today';
        this.loginChoice = null;
      },
    );
    if (cleared) this.render();
  }

  private renderLoading(): void {
    this.root.replaceChildren();
    const splash = element('main', 'splash');
    const logo = element('img', 'splash-logo') as HTMLImageElement;
    logo.src = '/icons/lunara.svg';
    logo.alt = '';
    splash.append(logo, element('p', '', this.t('loading')));
    this.root.append(splash);
  }

  private render(): void {
    const skipLink = document.querySelector('.skip-link');
    if (skipLink) skipLink.textContent = this.language === 'de' ? 'Zum Inhalt' : 'Skip to content';
    // Keep only a same-record, same-identity manual draft across clock/sync redraws.
    // It never enters a cache and disappears on navigation or account lock.
    const previousEditor = this.root.querySelector<HTMLFormElement>('.period-end-editor');
    const previousInput = previousEditor?.querySelector<HTMLInputElement>('input');
    const draft = previousInput ? {
      owner: previousEditor!.dataset.owner, periodId: previousEditor!.dataset.periodId,
      value: previousInput.value, focused: document.activeElement === previousInput,
    } : null;
    this.schedulePredictionDayRefresh();
    document.documentElement.lang = this.language;
    this.root.replaceChildren();
    if (this.invitationCode && this.user && !this.localMode) {
      this.root.append(this.invitationAcceptanceView());
      return;
    }
    if (!this.user) {
      this.root.append(this.loginView());
      return;
    }
    const frame = element('div', 'app-frame');
    frame.append(this.appHeader());
    const main = element('main', 'content');
    main.id = 'main-content';
    if (this.screen === 'today') main.append(this.todayView());
    if (this.screen === 'calendar') main.append(this.calendarView());
    if (this.screen === 'settings') main.append(this.settingsView());
    frame.append(main, this.bottomNavigation());
    this.root.append(frame);
    const editor = this.root.querySelector<HTMLFormElement>('.period-end-editor');
    if (draft && editor && editor.dataset.owner === draft.owner && editor.dataset.periodId === draft.periodId) {
      const input = editor.querySelector<HTMLInputElement>('input');
      if (input) {
        input.value = draft.value;
        input.dispatchEvent(new Event('input'));
        if (draft.focused) input.focus({ preventScroll: true });
      }
    }
  }

  private clearPredictionDayTimer(): void {
    if (this.predictionDayTimer !== null) window.clearTimeout(this.predictionDayTimer);
    this.predictionDayTimer = null;
  }

  private schedulePredictionDayRefresh(): void {
    this.clearPredictionDayTimer();
    if (!this.user || document.visibilityState !== 'visible') return;
    this.predictionDayTimer = window.setTimeout(() => this.refreshPredictionDate(), millisecondsUntilNextPredictionDay());
  }

  private refreshPredictionDate(): void {
    if (!this.user || document.visibilityState !== 'visible') return;
    // Reclassify cached summaries even offline; do not erase their historical calendar bands.
    // Calendar month, selection and identity-bound end drafts survive this redraw.
    if (this.screen === 'today' || this.screen === 'calendar') this.render();
    else this.schedulePredictionDayRefresh();
    if (!this.localMode && navigator.onLine) {
      if (this.user.role === 'TRACKER') void this.performSync(false);
      else void this.refreshData();
    }
  }

  private invitationAcceptanceView(): HTMLElement {
    const main = element('main', 'login-page');
    main.id = 'main-content';
    const card = element('section', 'login-card invitation-acceptance');
    const code = this.invitationCode;
    if (!code || !this.user) return main;
    card.append(
      element('h1', '', this.t('invitationAcceptTitle')),
      element('p', 'notice', this.t('invitationAcceptSignedIn', { name: this.user.displayName })),
      element('p', '', this.t('invitationAcceptExplanation')),
      element('p', 'invitation-code', code),
    );
    if (this.invitationError) {
      const error = element(
        'p',
        'form-error',
        this.t(
          this.invitationError === 'conflict'
            ? 'invitationAcceptConflict'
            : this.invitationError === 'invalid'
              ? 'invitationInvalid'
              : 'invitationAcceptFailed',
        ),
      );
      error.setAttribute('role', 'alert');
      card.append(error);
    }
    const accept = button(
      'primary-button',
      this.acceptingInvitation ? this.t('invitationAccepting') : this.t('invitationAcceptConfirm'),
      () => this.acceptPendingInvitation(),
    );
    accept.disabled = this.acceptingInvitation || !navigator.onLine;
    const discard = button('text-button', this.t('invitationDiscard'), () => {
      clearPendingInvitation(sessionStorage);
      this.invitationCode = null;
      this.invitationError = null;
      this.render();
    });
    card.append(accept, discard);
    main.append(this.loginIntroduction(), card);
    return main;
  }

  private async acceptPendingInvitation(): Promise<void> {
    const code = this.invitationCode;
    const current = this.user;
    if (!code || !current || this.acceptingInvitation || this.localMode) return;
    this.acceptingInvitation = true;
    this.invitationError = null;
    this.render();
    try {
      const unresolvedMutations = await queueStorage.getMutations();
      if (hasUnresolvedInvitationMutations(unresolvedMutations)) {
        this.invitationError = 'conflict';
        return;
      }
      await this.api.acceptInvitation(code);
      const next = await this.api.me();
      if (!(await this.rememberUser(next))) throw new ApiError(409, 'Invitation acceptance was superseded');
      await settingsStore.put('local-mode', false);
      clearPendingInvitation(sessionStorage);
      this.invitationCode = null;
      await this.loadCachedData();
      if (navigator.onLine) await this.refreshData();
    } catch (error) {
      this.invitationError = error instanceof ApiError && error.status === 409 ? 'conflict' : 'failed';
    } finally {
      this.acceptingInvitation = false;
      this.render();
    }
  }

  private loginView(): HTMLElement {
    if (!this.loginChoice) return this.modeChoiceView();
    const main = element('main', 'login-page auth-page');
    main.id = 'main-content';
    const card = element('section', 'login-card');
    const brand = element('div', 'brand-lockup');
    const logo = createBrandMark();
    brand.append(logo, element('div'));
    brand.lastElementChild?.append(
      element('h1', '', this.meta?.name ?? 'Lunara'),
      element('p', '', this.t('appTagline')),
    );
    const notice = element('p', 'notice', this.t('medicalNotice'));
    if (this.loginChoice === 'local') {
      const localPanel = this.localVaultPanel();
      card.append(brand, notice, localPanel, button('text-button language-toggle', this.language === 'de' ? 'English' : 'Deutsch', () => {
        this.setLanguage(this.language === 'de' ? 'en' : 'de');
      }));
      main.append(this.loginIntroduction(), card);
      return main;
    }
    // A new server cannot have invitations yet, so setup always wins.
    if (this.meta?.setupRequired) {
      card.append(brand, notice, this.setupForm(), button('text-button language-toggle', this.language === 'de' ? 'English' : 'Deutsch', () => {
        this.setLanguage(this.language === 'de' ? 'en' : 'de');
      }));
      main.append(this.loginIntroduction(), card);
      return main;
    }
    const form = element('form', 'login-form');
    const usernameLabel = element('label', '', this.t('username'));
    const username = element('input') as HTMLInputElement;
    username.name = 'username';
    username.autocomplete = 'username';
    username.required = true;
    username.minLength = 3;
    username.maxLength = 64;
    usernameLabel.append(username);
    const passwordLabel = element('label', '', this.t('password'));
    const password = element('input') as HTMLInputElement;
    password.name = 'password';
    password.type = 'password';
    password.autocomplete = 'current-password';
    password.required = true;
    password.maxLength = 256;
    passwordLabel.append(password);
    const submit = element('button', 'primary-button', this.t('localLogin'));
    submit.type = 'submit';
    form.append(usernameLabel, passwordLabel, submit);
    const error = element('p', 'form-error');
    error.setAttribute('role', 'alert');
    if (this.initError) error.textContent = this.initError;
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      submit.disabled = true;
      error.textContent = '';
      try {
        const session = await this.api.login(username.value.trim(), password.value);
        password.value = '';
        if (!(await this.rememberUser(session.user))) throw new ApiError(409, 'Login superseded');
        await this.loadCachedData();
        await this.refreshData();
        this.render();
      } catch (loginError) {
        error.textContent =
          loginError instanceof ApiError && loginError.status === 0
            ? this.t('serverUnavailable')
            : this.t('loginFailed');
      } finally {
        submit.disabled = false;
      }
    });
    card.append(brand, notice);
    if (this.invitationCode) {
      card.append(
        element('p', 'notice', this.t('invitationPendingSignIn')),
        element('p', 'invitation-code', this.invitationCode),
      );
    }
    card.append(form, error);
    if (!this.meta?.setupRequired && navigator.onLine !== false) card.append(this.joinSection());
    if (this.meta?.authenticationMethods?.includes('OIDC')) {
      const divider = element('div', 'divider', this.t('or'));
      card.append(divider);
      const beginOidc = async (intent: OidcEnrollmentIntent): Promise<void> => {
        if (!this.meta || !canStartOidc(this.meta, intent.kind)) {
          error.textContent = this.t('oidcUnavailable');
          return;
        }
        error.textContent = '';
        try {
          await startOidc(this.meta, intent);
        } catch {
          error.textContent = this.t('oidcUnavailable');
        }
      };
      const provider = this.meta.oidc?.providerName ?? 'Authentik';
      const preferredLanguage = this.language === 'de' ? 'DE' : 'EN';
      const enrollmentAvailable = supportsOidcEnrollment(this.meta);
      const oidcButton = button(
        'secondary-button',
        this.t(enrollmentAvailable ? 'oidcExistingLogin' : 'oidcLogin', { provider }),
        () => beginOidc({ kind: 'login', preferredLanguage }),
      );
      const oidcActions = element('div', 'oidc-actions');
      oidcActions.append(oidcButton);
      if (canStartOidc(this.meta, 'new-tracker')) {
        oidcActions.append(
          element('p', 'subtle oidc-hint', this.t('oidcCreateTrackerHint')),
          button(
            'secondary-button',
            this.t('oidcCreateTracker', { provider }),
            () => beginOidc({ kind: 'new-tracker', preferredLanguage }),
          ),
        );
      }
      if (canStartOidc(this.meta, 'partner')) {
        const partnerForm = element('form', 'login-form oidc-partner-form');
        const invitationLabel = element('label', '', this.t('oidcInvitationCode'));
        const invitationCode = element('input') as HTMLInputElement;
        invitationCode.name = 'invitationCode';
        invitationCode.autocomplete = 'one-time-code';
        invitationCode.spellcheck = false;
        invitationCode.required = true;
        invitationCode.minLength = 8;
        invitationCode.maxLength = 12;
        invitationCode.placeholder = this.t('oidcInvitationPlaceholder');
        invitationCode.value = this.invitationCode ?? '';
        invitationLabel.append(invitationCode);
        const partnerSubmit = element(
          'button',
          'secondary-button',
          this.t('oidcJoinPartner', { provider }),
        );
        partnerSubmit.type = 'submit';
        partnerForm.append(
          element('p', 'subtle oidc-hint', this.t('oidcJoinPartnerHint')),
          invitationLabel,
          partnerSubmit,
        );
        partnerForm.addEventListener('submit', async (event) => {
          event.preventDefault();
          const normalizedCode = normalizeInvitationCode(invitationCode.value);
          if (!normalizedCode) {
            error.textContent = this.t('oidcInvitationInvalid');
            invitationCode.focus();
            return;
          }
          invitationCode.value = normalizedCode;
          // Keep the separately confirmed, existing-account path available
          // after the browser returns from OIDC. The PKCE record carries the
          // new-identity enrollment intent; this hand-off covers an already
          // linked identity, for which the API deliberately performs normal
          // sign-in first.
          if (!storeInvitation(sessionStorage, normalizedCode)) {
            error.textContent = this.t('invitationInvalid');
            return;
          }
          this.invitationCode = normalizedCode;
          partnerSubmit.disabled = true;
          try {
            await beginOidc({
              kind: 'partner',
              invitationCode: normalizedCode,
              preferredLanguage,
            });
          } finally {
            partnerSubmit.disabled = false;
          }
        });
        oidcActions.append(partnerForm);
      }
      card.append(oidcActions);
    }
    const languageToggle = button('text-button language-toggle', this.language === 'de' ? 'English' : 'Deutsch', () => {
      this.setLanguage(this.language === 'de' ? 'en' : 'de');
    });
    card.append(
      languageToggle,
      button('text-button', this.t('backToChoice'), () => {
        this.loginChoice = null;
        this.render();
      }),
    );
    main.append(this.loginIntroduction(), card);
    return main;
  }

  private modeChoiceView(): HTMLElement {
    const main = element('main', 'login-page mode-choice-page');
    main.id = 'main-content';
    const card = element('section', 'login-card mode-choice');
    card.append(
      element('p', 'eyebrow', this.t('getStarted')),
      element('h1', '', this.t('chooseMode')),
      element('p', 'subtle', this.t('modeChoiceHint')),
    );
    const choices = element('div', 'mode-grid');
    const server = button('mode-button', this.t('ownServer'), async () => {
      this.loginChoice = 'server';
      this.renderLoading();
      this.meta = (await this.fetchMeta()) ?? (await settingsStore.get<MetaResponse>('meta')) ?? null;
      if (this.meta) await settingsStore.put('meta', this.meta);
      this.render();
    });
    server.append(element('span', '', this.t('ownServerDescription')));
    const local = button('mode-button mode-button-local', this.t('localOnly'), async () => {
      this.loginChoice = 'local';
      this.localVaultAvailable = Boolean(await getLocalVaultEnvelope<LocalVaultEnvelope>());
      this.render();
    });
    local.append(element('span', '', this.t('localOnlyDescription')));
    choices.append(local, server);
    card.append(
      choices,
      element('p', 'medical-footnote', this.t('medicalNotice')),
      button(
        'text-button language-toggle',
        this.language === 'de' ? 'English' : 'Deutsch',
        () => this.setLanguage(this.language === 'de' ? 'en' : 'de'),
      ),
    );
    main.append(this.loginIntroduction(), card);
    return main;
  }

  private loginIntroduction(): HTMLElement {
    const introduction = element('section', 'login-introduction');
    const brand = element('div', 'compact-brand');
    const logo = createBrandMark();
    brand.append(logo, element('strong', '', 'Lunara'));
    introduction.append(
      brand,
      element('p', 'eyebrow', this.t('welcomeEyebrow')),
      element('p', 'editorial-title', this.t('welcomeHeadline')),
      element('p', 'introduction-copy', this.t('welcomeDescription')),
      element('p', 'eyebrow introduction-note', this.t('welcomeNote')),
    );
    return introduction;
  }

  private localVaultPanel(): HTMLElement {
    const panel = element('div', 'mode-panel');
    const form = element('form', 'login-form');
    const passphraseLabel = element(
      'label',
      '',
      this.t(this.localVaultAvailable ? 'vaultPassphrase' : 'vaultNewPassphrase'),
    );
    const passphrase = element('input') as HTMLInputElement;
    passphrase.type = 'password';
    passphrase.autocomplete = this.localVaultAvailable ? 'current-password' : 'new-password';
    passphrase.required = true;
    passphrase.minLength = 12;
    passphrase.maxLength = 128;
    passphraseLabel.append(passphrase);
    form.append(passphraseLabel);

    let confirmation: HTMLInputElement | null = null;
    if (!this.localVaultAvailable) {
      const confirmationLabel = element('label', '', this.t('vaultConfirmPassphrase'));
      confirmation = element('input') as HTMLInputElement;
      confirmation.type = 'password';
      confirmation.autocomplete = 'new-password';
      confirmation.required = true;
      confirmation.minLength = 12;
      confirmation.maxLength = 128;
      confirmationLabel.append(confirmation);
      form.append(confirmationLabel);
    }

    const submit = element(
      'button',
      'primary-button',
      this.t(this.localVaultAvailable ? 'unlockVault' : 'createVault'),
    );
    submit.type = 'submit';
    const error = element('p', 'form-error');
    error.setAttribute('role', 'alert');
    form.append(submit, error);
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      error.textContent = '';
      if (confirmation && passphrase.value !== confirmation.value) {
        error.textContent = this.t('vaultPassphraseMismatch');
        confirmation.value = '';
        return;
      }
      submit.disabled = true;
      try {
        if (this.localVaultAvailable) await this.unlockLocalMode(passphrase.value);
        else await this.createLocalMode(passphrase.value);
        passphrase.value = '';
        if (confirmation) confirmation.value = '';
      } catch {
        this.localVault.lock();
        error.textContent = this.t('vaultUnlockFailed');
      } finally {
        submit.disabled = false;
      }
    });

    panel.append(
      element('h2', '', this.t(this.localVaultAvailable ? 'unlockVaultTitle' : 'createVaultTitle')),
      element('p', 'subtle', this.t('vaultSecurityNotice')),
      form,
      element('p', 'medical-footnote', this.t('vaultRecoveryWarning')),
    );
    if (!this.localMode) {
      panel.append(button('text-button', this.t('backToChoice'), () => {
        this.loginChoice = null;
        this.render();
      }));
    }
    return panel;
  }

  private localIdentity(displayName?: string): UserIdentity {
    return {
      id: 'local-solo',
      username: 'local',
      displayName: displayName?.trim() || (this.language === 'de' ? 'Du' : 'You'),
      preferredLanguage: this.language === 'de' ? 'DE' : 'EN',
      role: 'TRACKER',
      householdId: 'local-device',
    };
  }

  private async createLocalMode(passphrase: string): Promise<void> {
    if (this.localVaultCoordinator.busy) throw new Error('Local vault is busy');
    const generation = this.localVaultCoordinator.invalidate();
    // Read legacy Solo data only for this one-time migration. The atomic vault
    // commit below removes every clear-text local record before it completes.
    const legacyPeriods = await periodStore.all('local');
    const legacyExtras =
      (await settingsStore.get<PortableBackupExtras>('portable-backup-extras')) ??
      defaultPortableExtras();
    const legacyUser = await settingsStore.get<UserIdentity>('local-user');
    if (legacyUser?.displayName) {
      legacyExtras.settings = { ...legacyExtras.settings, displayName: legacyUser.displayName };
    }
    const state = { periods: legacyPeriods, extras: legacyExtras } satisfies LocalVaultState;
    const envelope = await this.localVault.create(passphrase, state);
    if (
      document.visibilityState === 'hidden' ||
      !this.localVaultCoordinator.isCurrent(generation)
    ) {
      this.localVault.lock();
      throw new Error('Local vault creation was interrupted');
    }
    await persistLocalVaultEnvelope(envelope);
    this.localMode = true;
    this.localVaultAvailable = true;
    if (!this.localVaultCoordinator.isCurrent(generation) || !this.localVault.unlocked) {
      this.render();
      throw new Error('Local vault was locked during creation');
    }
    this.applyLocalState(state);
    await navigator.storage?.persist?.().catch(() => false);
    this.screen = 'today';
    this.armVaultLock();
    this.render();
  }

  private async unlockLocalMode(passphrase: string): Promise<void> {
    if (this.localVaultCoordinator.busy) throw new Error('Local vault is busy');
    const generation = this.localVaultCoordinator.invalidate();
    const envelope = await getLocalVaultEnvelope<LocalVaultEnvelope>();
    if (!envelope) throw new Error('Local vault is missing');
    const state = await this.localVault.unlock(passphrase, envelope);
    if (
      document.visibilityState === 'hidden' ||
      !this.localVaultCoordinator.isCurrent(generation)
    ) {
      this.localVault.lock();
      throw new Error('Local vault unlock was interrupted');
    }
    await persistLocalVaultEnvelope(envelope);
    if (!this.localVaultCoordinator.isCurrent(generation) || !this.localVault.unlocked) {
      throw new Error('Local vault was locked while opening');
    }
    this.localMode = true;
    this.localVaultAvailable = true;
    this.applyLocalState(state);
    this.screen = 'today';
    this.armVaultLock();
    this.render();
  }

  private applyLocalState(state: LocalVaultState): void {
    this.periods = [...state.periods].sort((left, right) =>
      right.startDate.localeCompare(left.startDate),
    );
    this.localExtras = state.extras;
    this.user = this.localIdentity(state.extras.settings.displayName);
    this.prediction = calculateLocalPrediction(this.periods, predictionReferenceDate(), state.extras.profile);
    this.partnerTip = null;
    this.partnerCalendar.selectIdentity(null);
    this.syncState = { pending: 0, failed: 0, lastSuccessfulAt: null };
    this.applyImportedTheme(state.extras.settings);
  }

  private async persistLocalState(
    periods: PeriodEvent[],
    extras: PortableBackupExtras = this.localExtras,
  ): Promise<void> {
    if (!this.localMode || !this.localVault.unlocked) throw new Error('Local vault is locked');
    try {
      await this.localVaultCoordinator.write(async (assertCurrent) => {
        const state = { periods, extras } satisfies LocalVaultState;
        const envelope = await this.localVault.seal(state);
        assertCurrent();
        if (!this.localVault.unlocked) throw new Error('Local vault is locked');
        await persistLocalVaultEnvelope(envelope);
        assertCurrent();
        if (!this.localVault.unlocked) throw new Error('Local vault is locked');
        this.applyLocalState(state);
      });
    } finally {
      this.armVaultLock();
    }
  }

  private applyImportedTheme(settings: PortableBackupExtras['settings']): void {
    this.theme = settings.useSystemTheme ? 'system' : settings.darkMode ? 'dark' : 'light';
    localStorage.setItem('lunara-theme', this.theme);
    this.applyTheme();
  }

  private armVaultLock(): void {
    if (!this.localMode || !this.localVault.unlocked) return;
    if (this.vaultLockTimer !== null) window.clearTimeout(this.vaultLockTimer);
    this.vaultLockTimer = window.setTimeout(() => this.lockLocalVault(), 15 * 60_000);
  }

  private lockLocalVault(): void {
    if (!this.localMode && !this.localVault.unlocked) return;
    this.localVaultCoordinator.invalidate();
    this.clearLocalVaultRuntime();
    this.loginChoice = 'local';
    this.render();
  }

  private clearLocalVaultRuntime(): void {
    if (this.vaultLockTimer !== null) window.clearTimeout(this.vaultLockTimer);
    this.vaultLockTimer = null;
    this.localVault.lock();
    this.user = null;
    this.periods = [];
    this.localExtras = defaultPortableExtras();
    this.prediction = null;
    this.importReport = null;
    this.partnerCalendar.selectIdentity(null);
  }

  private appHeader(): HTMLElement {
    const header = element('header', 'top-bar');
    const brand = element('div', 'compact-brand');
    const icon = createBrandMark();
    brand.append(icon, element('strong', '', this.meta?.name ?? 'Lunara'));
    const status = button('sync-pill', this.syncLabel(), () => this.performSync(true));
    if (!navigator.onLine && !this.localMode) status.classList.add('offline');
    if (this.syncState.failed > 0) status.classList.add('error');
    status.disabled = this.localMode || this.syncing || this.user?.role !== 'TRACKER';
    header.append(brand, status);
    return header;
  }

  private syncLabel(): string {
    if (this.localMode) return this.t('localMode');
    if (!navigator.onLine) return this.t('offline');
    if (this.syncing) return this.t('syncing');
    if (this.syncState.failed > 0) return this.t('syncFailed');
    if (this.syncState.pending > 0) return this.t('pendingChanges', { count: this.syncState.pending });
    return this.t('synced');
  }

  private todayView(): HTMLElement {
    const isTracker = this.user?.role === 'TRACKER';
    const view = element('div', `view-stack today-view${isTracker ? '' : ' partner-view'}`);
    const lead = element('div', 'journal-lead');
    const rail = element('aside', 'journal-rail');
    rail.setAttribute('aria-label', this.t('overview'));
    const greeting = element('section', 'hero-card');
    const date = new Intl.DateTimeFormat(this.language === 'de' ? 'de-DE' : 'en-GB', {
      weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
    }).format(new Date());
    greeting.append(
      element('p', 'eyebrow', date),
      element('h1', '', this.t('hello', { name: this.user?.displayName ?? '' })),
    );
    lead.append(greeting, this.predictionCard());
    if (isTracker) {
      const active = this.activePeriod();
      const controls = element('section', 'period-controls');
      const action = button('period-action', active ? this.t('endPeriod') : this.t('startPeriod'), async () => {
        if (active) await this.endPeriod(active, todayIso());
        else await this.startPeriod(todayIso());
      });
      action.disabled = this.syncing || this.localVaultCoordinator.busy;
      const icon = element('span', 'action-icon', active ? '✓' : '+');
      icon.setAttribute('aria-hidden', 'true');
      action.prepend(icon);
      controls.append(action, element('p', `period-state${active ? ' is-active' : ''}`, active
        ? this.t('periodActiveSince', { date: formatDate(active.startDate, this.language) })
        : this.t('noActivePeriod')));
      const calendarAction = button('secondary-button calendar-shortcut', this.t('recordAnotherDate'), () => {
        this.screen = 'calendar';
        this.render();
      });
      lead.append(controls);
      controls.append(calendarAction);
    } else {
      greeting.append(element('p', 'hero-description', this.t('todayPartnerHint')));
    }
    if (isTracker) {
      rail.append(this.recentPeriodsCard());
    } else {
      lead.append(this.tipCard());
      const relationship = element('section', 'relationship-note');
      relationship.append(
        element('p', 'eyebrow', this.t('rolePartner')),
        element('h2', '', this.t('sharedWithYou')),
        element('p', 'subtle', this.t('partnerPrivacyHint')),
      );
      rail.append(relationship);
    }
    view.append(lead, rail);
    view.append(element('p', 'medical-footnote', this.t('predictionDisclaimer')));
    return view;
  }

  private predictionCard(): HTMLElement {
    const card = element('section', 'prediction-summary');
    card.append(element('h2', 'prediction-label', this.t('nextPeriod')));
    const referenceDate = predictionReferenceDate();
    const pendingChanges = !this.localMode && this.user?.role === 'TRACKER' && this.syncState.pending + this.syncState.failed > 0;
    const summary = predictionSummary(this.prediction, referenceDate, pendingChanges);
    if (summary.kind === 'current' && this.prediction) {
      card.append(
        element(
          'p',
          'prediction-range',
          this.t('predictedRange', {
            from: formatDate(summary.from, this.language, { day: 'numeric', year: undefined }),
            to: formatDate(summary.to, this.language, { day: 'numeric', year: undefined }),
          }),
        ),
      );
      const confidenceKey = this.prediction.confidence === 'HIGH'
        ? 'confidenceHigh'
        : this.prediction.confidence === 'MEDIUM'
          ? 'confidenceMedium'
          : 'confidenceLow';
      card.append(element('p', 'prediction-explanation', this.t('estimatedStart')));
      const basis = element('div', 'prediction-basis');
      if (this.prediction.basedOnCycleCount !== undefined) {
        basis.append(element('strong', '', this.t('cyclesBasis', { count: this.prediction.basedOnCycleCount })));
      }
      basis.append(element('p', 'subtle', this.t('confidence', { value: this.t(confidenceKey) })));
      card.append(basis);
    } else if (this.prediction?.reason === 'UNRELIABLE_RANGE') {
      card.append(element('p', 'prediction-range', this.t('predictionUnavailable')));
      card.append(element('p', 'subtle', this.language === 'de'
        ? 'Deine Zyklen schwanken noch zu stark für eine hilfreiche Schätzung.'
        : 'Your cycles vary too much for a useful estimate yet'));
    } else if (summary.kind === 'stale' || summary.kind === 'pending') {
      card.append(element('p', 'prediction-range', this.t('predictionNotCurrent')));
      card.append(element('p', 'subtle', this.t(summary.kind === 'stale' ? 'predictionStale' : 'predictionPending')));
    } else {
      card.append(element('p', 'prediction-range', this.t(this.user?.role === 'PARTNER' ? 'predictionUnavailable' : 'predictionEmpty')));
      card.append(element('p', 'subtle', this.t(this.user?.role === 'PARTNER' ? 'noPartnerPrediction' : 'noPrediction')));
    }
    if (hasCurrentFertileEstimate(this.prediction, referenceDate, pendingChanges)) {
      const fertility = element('section', 'current-fertility');
      fertility.append(
        element('h3', '', this.t('fertileEstimate')),
        element('p', '', `${formatDate(this.prediction!.fertileWindowFrom!, this.language)} – ${formatDate(this.prediction!.fertileWindowTo!, this.language)}`),
        element('p', 'subtle', this.t('fertilityDisclaimer')),
      );
      card.append(fertility);
    }
    return card;
  }

  private tipCard(): HTMLElement {
    const tip = this.partnerTip;
    const card = element('section', 'tip-card');
    card.append(
      element('p', 'eyebrow', this.t('partnerSuggestion')),
      element('h2', '', tip?.title ?? this.t('support')),
      element('p', '', tip?.body ?? this.t('defaultTip')),
    );
    if (tip?.callToAction) {
      const action = element('div', 'tip-action');
      action.append(element('p', 'eyebrow', this.t('tryToday')), element('p', '', tip.callToAction));
      card.append(action);
    }
    const publisher = tipSourcePublisher(tip?.source?.url);
    if (publisher && tip?.source) {
      const reference = element('a', 'text-button tip-reference', this.t('tipBackground', { publisher }));
      reference.href = tip.source.url;
      reference.target = '_blank';
      reference.rel = 'noopener noreferrer';
      card.append(reference);
    }
    return card;
  }

  private recentPeriodsCard(): HTMLElement {
    const card = element('section', 'history-summary');
    card.append(element('h2', '', this.t('history')));
    const list = element('ul', 'period-list');
    for (const period of this.periods.filter((item) => periodEndState(item, todayIso())).slice(0, 4)) {
      const state = periodEndState(period, todayIso())!;
      const range = period.endDate
        ? `${formatDate(period.startDate, this.language)} – ${formatDate(period.endDate, this.language)}`
        : formatDate(period.startDate, this.language);
      const item = element('li');
      item.append(element('span', 'period-list-dot'), element('span', '', range));
      if (state.assumedComplete) item.append(element('small', '', this.t('periodAssumedEnd', { date: formatDate(state.endDate, this.language) })));
      else if (state.ongoing) item.append(element('small', '', this.t('periodActiveSince', { date: formatDate(period.startDate, this.language) })));
      item.append(button('text-button', this.t('periodEditEnd'), () => {
        this.calendarMonth = period.startDate.slice(0, 7);
        this.selectedDate = period.startDate;
        this.screen = 'calendar';
        this.render();
      }));
      if (period.pending) item.append(element('small', '', this.t('syncing')));
      list.append(item);
    }
    if (!this.periods.length) list.append(element('li', 'subtle', this.t('noData')));
    card.append(list);
    return card;
  }

  private calendarView(): HTMLElement {
    const view = element('div', 'view-stack calendar-view');
    const heading = element('header', 'page-heading');
    heading.append(
      element('p', 'eyebrow', this.t('yourEntries')),
      element('h1', '', this.t('calendar')),
      element('p', 'subtle', this.t(this.user?.role === 'TRACKER' ? 'calendarTrackerHint' : 'calendarPartnerHint')),
    );
    view.append(heading);
    if (this.user?.role === 'PARTNER') {
      const notice = createPartnerCalendarNotice(this.partnerCalendar.current, this.language);
      if (notice) view.append(notice);
    }
    const referenceDate = todayIso();
    const calendar = new SwipeCalendar({
      language: this.language,
      role: this.user?.role ?? 'PARTNER',
      periods: this.periods,
      referenceDate,
      prediction: this.prediction,
      initialMonth: this.calendarMonth,
      selectedDate: this.selectedDate,
      onMonthChange: (month) => {
        this.calendarMonth = month;
      },
      onSelect: (date) => {
        this.selectedDate = date;
        this.render();
        this.root.querySelector<HTMLButtonElement>(`.calendar-day[data-date="${date}"]`)?.focus({ preventScroll: true });
      },
    });
    view.append(calendar.element, this.calendarLegend());
    if (this.user?.role === 'TRACKER' && this.selectedDate) {
      const selected = element('section', 'selection-card');
      selected.append(element('strong', '', this.t('selectedDate', {
        date: formatDate(this.selectedDate, this.language),
      })));
      const actions = element('div', 'button-row');
      const selectedPeriod = periodForDate(this.periods, this.selectedDate, referenceDate);
      const active = this.activePeriod(referenceDate);
      if (selectedPeriod) {
        selected.append(this.periodEndEditor(selectedPeriod, referenceDate), this.periodDeleteControl(selectedPeriod));
      } else if (!active) {
        const start = button('primary-button', this.t('startOnDate'), () => this.startPeriod(this.selectedDate!));
        start.disabled =
          this.syncing ||
          this.localVaultCoordinator.busy ||
          this.selectedDate > todayIso() ||
          this.overlapsExisting(this.selectedDate, null);
        actions.append(start);
      } else {
        const end = button('primary-button', this.t('endOnDate'), () => this.endPeriod(active, this.selectedDate!));
        end.disabled =
          this.syncing ||
          this.localVaultCoordinator.busy ||
          this.selectedDate < active.startDate ||
          this.selectedDate > todayIso() ||
          this.overlapsExisting(active.startDate, this.selectedDate, active.id);
        actions.append(end);
      }
      selected.append(actions);
      view.append(selected);
    }
    view.append(element('p', 'medical-footnote', this.t('predictionDisclaimer')));
    return view;
  }

  private calendarLegend(): HTMLElement {
    return createCalendarLegend(this.language, hasFertileEstimate(this.prediction));
  }

  private periodEndEditor(period: PeriodEvent, referenceDate: string): HTMLFormElement {
    const form = element('form', 'period-end-editor');
    form.dataset.periodId = period.id;
    form.dataset.owner = this.localMode ? 'local-vault' : serverIdentityKey(window.location.origin, this.user!);
    const state = periodEndState(period, referenceDate)!;
    form.append(element('p', '', this.t('periodStartedOn', { date: formatDate(period.startDate, this.language) })));
    if (state.assumedComplete) form.append(element('p', 'notice', this.t('periodAssumedEnd', { date: formatDate(state.endDate, this.language) })));
    form.append(element('p', 'subtle', this.t('periodEndHelp')));
    const label = element('label', '', this.t('periodConfirmedEnd'));
    const input = element('input');
    input.type = 'date'; input.name = 'periodEnd'; input.required = true;
    input.min = period.startDate; input.max = referenceDate;
    input.value = period.endDate ?? '';
    label.append(input);
    const error = element('p', 'form-error');
    error.setAttribute('role', 'status');
    const save = element('button', 'primary-button', this.t('periodSaveEnd'));
    save.type = 'submit';
    const validate = () => {
      const invalid = !input.value || input.value < period.startDate || input.value > todayIso() ||
        this.overlapsExisting(period.startDate, input.value, period.id);
      save.disabled = invalid || this.syncing || this.localVaultCoordinator.busy;
      error.textContent = input.value && invalid ? this.t('periodEndInvalid') : '';
    };
    input.addEventListener('input', validate);
    form.addEventListener('submit', (event) => {
      event.preventDefault(); validate();
      if (save.disabled) return;
      const owner = this.localMode ? 'local-vault' : this.user && serverIdentityKey(window.location.origin, this.user);
      const current = this.periods.find((item) => item.id === period.id && !item.deletedAt);
      if (owner === form.dataset.owner && this.user?.role === 'TRACKER' && current) void this.endPeriod(current, input.value);
    });
    validate();
    form.append(label, error, save);
    return form;
  }

  private settingsView(): HTMLElement {
    const view = element('div', 'view-stack settings-view');
    const heading = element('header', 'page-heading');
    heading.append(element('p', 'eyebrow', this.t('yourLunara')), element('h1', '', this.t('settings')));
    view.append(heading);
    const account = element('section', 'account-summary');
    account.append(
      element('h2', '', this.user?.displayName ?? ''),
      element('p', 'subtle', `@${this.user?.username ?? ''} · ${this.user?.role === 'TRACKER' ? this.t('roleTracker') : this.t('rolePartner')}`),
    );

    const language = element('section', 'info-card setting-card');
    language.append(element('h2', '', this.t('language')));
    const languageButtons = element('div', 'segmented');
    for (const [code, label] of [['de', 'Deutsch'], ['en', 'English']] as const) {
      const choice = button(this.language === code ? 'active' : '', label, () => this.setLanguage(code));
      choice.setAttribute('aria-pressed', String(this.language === code));
      languageButtons.append(choice);
    }
    language.append(languageButtons);

    const appearance = element('section', 'info-card setting-card');
    appearance.append(element('h2', '', this.t('appearance')));
    const themeButtons = element('div', 'segmented');
    for (const choice of ['system', 'light', 'dark'] as const) {
      const label = this.t(choice);
      const choiceButton = button(this.theme === choice ? 'active' : '', label, () => this.setTheme(choice));
      choiceButton.setAttribute('aria-pressed', String(this.theme === choice));
      themeButtons.append(choiceButton);
    }
    appearance.append(themeButtons);

    const server = element('section', 'info-card setting-card');
    if (this.localMode) {
      server.append(
        element('h2', '', this.t('localMode')),
        element('p', 'subtle', this.t('localOnlyDescription')),
      );
    } else {
      server.append(
        element('h2', '', this.t('server')),
        element('p', '', window.location.host),
        element('p', 'subtle', this.t('version', { version: this.meta?.version ?? '–' })),
      );
      if (this.user?.role === 'TRACKER') {
        server.append(button('secondary-button', this.t('syncNow'), () => this.performSync(true)));
      }
    }

    const install = this.installCard();
    const logout = button(
      'danger-button',
      this.localMode ? this.t('deleteLocalData') : this.t('logout'),
      () => this.logout(),
    );
    if (this.localMode) logout.disabled = this.localVaultCoordinator.busy;
    view.append(account, this.settingsGroup('preferences', 'preferencesGroup', 'preferencesHint', language, appearance));
    if (!this.localMode && this.user?.role === 'TRACKER') {
      view.append(this.settingsGroup('sharing', 'sharingGroup', 'sharingHint', this.sharingSettingsCard(), this.partnerInvitationCard(), this.manualInvitationAcceptanceCard()));
      view.append(this.settingsGroup('cycle', 'cycleGroup', 'cycleHint', this.cycleProfileCard()));
    } else if (!this.localMode && this.user?.role === 'PARTNER') {
      view.append(this.settingsGroup('sharing', 'sharingGroup', 'calendarAccessTitle',
        createPartnerCalendarAccessCard(this.partnerCalendar.current, this.language)));
    }
    if (this.localMode) {
      view.append(
        this.settingsGroup('data', 'dataGroup', 'dataHint', this.periodTransferCard(), this.backupCard(),
          button('secondary-button', this.t('lockVault'), () => this.lockLocalVault())),
      );
    } else if (this.user?.role === 'TRACKER') {
      view.append(this.settingsGroup('data', 'dataGroup', 'dataHint', this.periodTransferCard()));
    }
    const appItems = [server, createApkDownloadCard(this.language), ...(install ? [install] : [])];
    const oidcCard = this.oidcLinkCard();
    if (oidcCard) appItems.splice(1, 0, oidcCard);
    view.append(this.settingsGroup('app', 'appGroup', 'appHint', ...appItems));
    const accountActions = element('div', 'account-actions');
    accountActions.append(logout);
    view.append(accountActions);
    return view;
  }

  private settingsGroup(id: string, title: MessageKey, description: MessageKey, ...children: HTMLElement[]): HTMLElement {
    const group = element('details', 'settings-group');
    group.dataset.group = id;
    group.open = this.expandedSettings.has(id);
    const summary = element('summary');
    const labels = element('span', 'settings-group-label');
    labels.append(element('strong', '', this.t(title)), element('span', 'subtle', this.t(description)));
    const icon: IconName = id === 'preferences' || id === 'sharing' || id === 'data' ? id : 'app';
    summary.append(createIcon(icon, 'settings-icon'), labels);
    const content = element('div', 'settings-group-content');
    content.append(...children);
    group.append(summary, content);
    group.addEventListener('toggle', () => {
      if (group.open) this.expandedSettings.add(id);
      else this.expandedSettings.delete(id);
    });
    return group;
  }

  private partnerInvitationCard(): HTMLElement {
    const card = element('section', 'info-card setting-card partner-invitation');
    card.append(
      element('h2', '', this.t('partnerInviteTitle')),
      element('p', 'subtle', this.t('partnerInviteExplanation')),
    );
    if (this.partnerConnected === null && !this.invitationOverviewLoading && !this.invitationOverviewFailed) {
      void this.loadInvitationOverview();
    }
    if (this.invitationOverviewLoading) {
      card.append(element('p', 'subtle', this.t('partnerInviteChecking')));
      return card;
    }
    if (this.partnerConnected === null) {
      const error = element('p', 'form-error', this.t('partnerInviteStatusFailed'));
      error.setAttribute('role', 'alert');
      card.append(error, button('secondary-button', this.t('retry'), () => {
        this.invitationOverviewFailed = false;
        void this.loadInvitationOverview();
        this.render();
      }));
      return card;
    }
    if (this.partnerConnected) {
      card.append(element('p', 'notice', this.t('partnerInviteConnected')));
      return card;
    }
    if (this.invitationCreationError) {
      const error = element(
        'p',
        'form-error',
        this.t(this.invitationLinkRejected ? 'partnerInviteLinkRejected' : 'partnerInviteFailed'),
      );
      error.setAttribute('role', 'alert');
      card.append(error);
    }
    if (this.createdInvitation) {
      const invitation = this.createdInvitation;
      const code = element('p', 'invitation-code', invitation.code);
      if (this.createdInvitationLink) {
        const linkLabel = element('label', '', this.t('partnerInviteLink'));
        const link = element('input') as HTMLInputElement;
        link.type = 'url';
        link.readOnly = true;
        link.value = this.createdInvitationLink;
        linkLabel.append(link);
        const actions = element('div', 'button-row');
        actions.append(
          button('secondary-button', this.t('partnerInviteCopy'), () => this.copyInvitationLink(this.createdInvitationLink!)),
        );
        if ('share' in navigator) {
          actions.append(button('secondary-button', this.t('partnerInviteShare'), () => this.shareInvitationLink(this.createdInvitationLink!)));
        }
        card.append(
          element('p', 'subtle', this.t('partnerInviteLinkExplanation')),
          linkLabel,
          actions,
        );
      }
      card.append(
        element('p', 'subtle', this.t('partnerInviteCodeFallback')),
        code,
      );
      return card;
    }
    const create = button(
      'primary-button',
      this.invitationCreationBusy ? this.t('partnerInviteCreating') : this.t('partnerInviteCreate'),
      () => this.createPartnerInvitation(),
    );
    create.disabled = this.invitationCreationBusy || !navigator.onLine;
    card.append(create);
    return card;
  }

  private async loadInvitationOverview(): Promise<void> {
    const user = this.user;
    if (!user || user.role !== 'TRACKER' || this.localMode || this.invitationOverviewLoading) return;
    const identityKey = serverIdentityKey(window.location.origin, user);
    this.invitationOverviewLoading = true;
    try {
      await this.serverIdentities.run(identityKey, async (assertCurrent) => {
        const household = await this.api.household();
        assertCurrent();
        this.partnerConnected = household.members.some((member) => member.role === 'PARTNER');
        this.invitationOverviewFailed = false;
      });
    } catch {
      // Fail closed: no invitation can be minted until the current relationship
      // is known, avoiding a misleading second invitation after a stale view.
      this.partnerConnected = null;
      this.invitationOverviewFailed = true;
    } finally {
      this.invitationOverviewLoading = false;
      this.render();
    }
  }

  private async createPartnerInvitation(): Promise<void> {
    const user = this.user;
    if (
      !user || user.role !== 'TRACKER' || this.localMode || this.partnerConnected !== false ||
      this.invitationCreationBusy
    ) return;
    this.invitationCreationBusy = true;
    this.invitationCreationError = false;
    this.invitationLinkRejected = false;
    this.render();
    try {
      const invitation = await this.api.createInvitation();
      const link = new URL(invitation.acceptanceUrl);
      if (
        link.origin !== window.location.origin || link.username || link.password ||
        link.pathname !== '/invite' || !link.hash.startsWith('#invite=')
      ) {
        this.createdInvitation = invitation;
        this.createdInvitationLink = null;
        this.invitationCreationError = true;
        this.invitationLinkRejected = true;
        return;
      }
      this.createdInvitation = invitation;
      this.createdInvitationLink = link.href;
    } catch {
      this.invitationCreationError = true;
    } finally {
      this.invitationCreationBusy = false;
      this.render();
    }
  }

  private async copyInvitationLink(value: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(value);
      this.toast(this.t('partnerInviteCopied'));
    } catch {
      this.toast(this.t('partnerInviteCopyFailed'));
    }
  }

  private async shareInvitationLink(value: string): Promise<void> {
    try {
      await navigator.share({ title: this.t('partnerInviteTitle'), url: value });
    } catch {
      // Dismissing the native share sheet is a normal, non-error outcome.
    }
  }

  private manualInvitationAcceptanceCard(): HTMLElement {
    const card = element('section', 'info-card setting-card invitation-manual');
    card.append(
      element('h2', '', this.t('invitationManualTitle')),
      element('p', 'subtle', this.t('invitationManualExplanation')),
    );
    const form = element('form', 'login-form');
    const label = element('label', '', this.t('oidcInvitationCode'));
    const input = element('input') as HTMLInputElement;
    input.autocomplete = 'one-time-code';
    input.spellcheck = false;
    input.required = true;
    input.minLength = 8;
    input.maxLength = 12;
    input.placeholder = this.t('oidcInvitationPlaceholder');
    label.append(input);
    const submit = element('button', 'secondary-button', this.t('invitationManualContinue'));
    submit.type = 'submit';
    const error = element('p', 'form-error');
    error.setAttribute('role', 'alert');
    form.append(label, submit, error);
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      const code = normalizeInvitationCode(input.value);
      if (!code || !storeInvitation(sessionStorage, code)) {
        error.textContent = this.t('invitationInvalid');
        input.focus();
        return;
      }
      this.invitationCode = code;
      this.invitationError = null;
      this.render();
    });
    card.append(form);
    return card;
  }

  private periodTransferCard(): HTMLElement {
    const card = element('section', 'info-card setting-card period-transfer');
    const owner = this.user;
    const local = this.localMode;
    const vaultGeneration = this.localVaultCoordinator.capture();
    const assertOwner = () => {
      if (!owner || owner.role !== 'TRACKER' || this.user !== owner || this.localMode !== local ||
          (local && (!this.localVault.unlocked || !this.localVaultCoordinator.isCurrent(vaultGeneration)))) throw new StaleIdentityError();
    };
    card.append(element('h2', '', this.t('periodTransfer')), element('p', 'subtle', this.t('periodTransferHint')),
      element('p', 'warning-card', this.t('periodPlaintextWarning')));
    const acknowledgment = element('label', 'backup-label');
    const consent = element('input'); consent.type = 'checkbox';
    acknowledgment.append(consent, document.createTextNode(this.t('periodPlaintextConfirm')));
    const status = element('p', 'subtle'); status.setAttribute('role', 'status');
    const actions = element('div', 'backup-actions');
    const exportButtons = (['csv', 'json'] as const).map((format: PeriodTransferFormat) => {
      const control = button('secondary-button', this.t(format === 'csv' ? 'periodCsvExport' : 'periodJsonExport'), () => {
        try {
          assertOwner(); if (!consent.checked) return;
          const body = serializePeriods(this.periods, format);
          const blob = new Blob([body], { type: format === 'csv' ? 'text/csv;charset=utf-8' : 'application/json' });
          const url = URL.createObjectURL(blob);
          const link = element('a'); link.href = url; link.download = `lunara-periods-${todayIso()}.${format}`;
          card.append(link); link.click(); link.remove();
          window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
        } catch { status.textContent = this.t('periodTransferFailed'); }
      });
      control.disabled = true; return control;
    });
    consent.addEventListener('change', () => exportButtons.forEach((control) => { control.disabled = !consent.checked; }));
    actions.append(...exportButtons);
    const file = element('input'); file.type = 'file'; file.accept = '.csv,.tsv,.json,text/csv,text/tab-separated-values,application/json'; file.hidden = true;
    let pending: PeriodRange[] | null = null;
    let busy = false;
    const assertServerReady = () => {
      if (!local && (!navigator.onLine || this.syncing || this.syncState.pending || this.syncState.failed)) throw new Error('sync');
    };
    const showError = (error: unknown) => {
      const keys: Record<string, MessageKey> = { format: 'periodTransferFormat', date: 'periodTransferDate', ambiguous: 'periodTransferAmbiguous', size: 'periodTransferSize', empty: 'periodTransferEmpty' };
      status.textContent = this.t(error instanceof PeriodTransferError ? keys[error.code]! : error instanceof Error && error.message === 'sync' ? 'periodTransferSync' : 'periodTransferFailed');
    };
    const confirm = button('primary-button', this.t('periodImportConfirm'), async () => {
      if (!pending || busy) return;
      busy = true; confirm.disabled = true;
      try {
        assertOwner(); assertServerReady();
        const records = pending;
        if (local) {
          const plan = periodImportPlan(this.periods.filter((period) => !period.deletedAt), records);
          if (plan.conflicts) { status.textContent = this.t('periodTransferConflict'); return; }
          const additions = plan.added.map((period) => ({ ...period, id: crypto.randomUUID(), source: 'IMPORT' }));
          await this.persistLocalState([...this.periods, ...additions]);
        } else {
          await this.serverIdentities.run(serverIdentityKey(window.location.origin, owner!), async (assertCurrent) => {
            assertCurrent(); assertOwner();
            const queue = await queueStorage.getMutations();
            assertCurrent(); assertOwner();
            if (queue.length) throw new Error('sync');
            await this.api.importPeriods(records, false);
            assertCurrent();
          });
          await this.refreshData();
        }
        pending = null;
        if (this.localMode === local && this.user?.id === owner?.id) {
          this.render(); this.toast(this.t('periodImportComplete'));
        }
      } catch (error) { showError(error); }
      finally { busy = false; }
    });
    confirm.hidden = true; confirm.disabled = true;
    file.addEventListener('change', async () => {
      if (busy) return;
      const selected = file.files?.[0]; if (!selected) return;
      busy = true; pending = null; confirm.hidden = true; confirm.disabled = true;
      status.textContent = this.t('periodTransferBusy');
      try {
        assertOwner(); assertServerReady();
        if (selected.size > PERIOD_FILE_LIMIT) throw new PeriodTransferError('size');
        const bytes = new Uint8Array(await selected.arrayBuffer());
        assertOwner();
        const encoding = bytes[0] === 255 && bytes[1] === 254 ? 'utf-16le' : bytes[0] === 254 && bytes[1] === 255 ? 'utf-16be' : 'utf-8';
        const records = parsePeriodFile(new TextDecoder(encoding, { fatal: true }).decode(bytes));
        const plan = periodImportPlan(this.periods.filter((period) => !period.deletedAt), records);
        let counts = { create: plan.added.length, duplicates: plan.duplicates, conflicts: plan.conflicts };
        if (!local) {
          counts = (await this.serverIdentities.run(serverIdentityKey(window.location.origin, owner!), async (assertCurrent) => {
            assertCurrent(); assertOwner(); return this.api.importPeriods(records, true);
          })).periods;
        }
        assertOwner();
        pending = records;
        status.textContent = this.t('periodImportPreview', { new: counts.create, duplicates: counts.duplicates, conflicts: counts.conflicts });
        if (counts.conflicts) status.textContent += ` · ${this.t('periodTransferConflict')}`;
        confirm.hidden = false; confirm.disabled = counts.conflicts > 0 || counts.create === 0;
      } catch (error) { showError(error); }
      finally { busy = false; file.value = ''; }
    });
    card.append(acknowledgment, actions, element('p', 'subtle', this.t('periodFormatHint')),
      button('secondary-button', this.t('periodImport'), () => { if (!busy) file.click(); }), file, status, confirm);
    return card;
  }

  private backupCard(): HTMLElement {
    const card = element('section', 'info-card setting-card backup-card');
    card.append(
      element('h2', '', this.t('backup')),
      element('p', 'warning-card', this.t('localStorageWarning')),
      element('p', 'subtle', this.t('storedDailyLogs', { count: this.localExtras.dailyLogs.length })),
    );
    const label = element('label', 'backup-label', this.t('backupPassphrase'));
    const passphrase = element('input') as HTMLInputElement;
    passphrase.type = 'password';
    passphrase.autocomplete = 'new-password';
    passphrase.minLength = 12;
    passphrase.maxLength = 128;
    label.append(passphrase);
    const file = element('input', 'file-input') as HTMLInputElement;
    file.type = 'file';
    file.accept = '.lunarabackup,application/json';
    const actions = element('div', 'backup-actions');
    actions.append(
      button('secondary-button', this.t('exportBackup'), async () => {
        try {
          const preserved = this.localExtras;
          const encrypted = await encryptPortableBackup(this.periods, passphrase.value, {
            ...preserved,
            settings: {
              ...preserved.settings,
              displayName: this.user?.displayName ?? preserved.settings.displayName,
              useSystemTheme: this.theme === 'system',
              darkMode: this.theme === 'dark',
            },
          });
          const blob = new Blob([JSON.stringify(encrypted)], { type: 'application/json' });
          const url = URL.createObjectURL(blob);
          const download = element('a') as HTMLAnchorElement;
          download.href = url;
          download.download = `lunara-${todayIso()}.lunarabackup`;
          download.click();
          window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
          passphrase.value = '';
          this.toast(this.t('backupCreated'));
        } catch {
          this.toast(this.t('backupError'));
        }
      }),
      button('secondary-button', this.t('importBackup'), () => file.click()),
    );
    file.addEventListener('change', async () => {
      const selected = file.files?.[0];
      if (!selected) return;
      try {
        assertImportSize(selected);
        const imported = await decryptPortableBackup(await selected.text(), passphrase.value);
        const merged = mergePeriods(this.periods, imported.periods);
        const mergedDailyLogs = mergeDailyLogs(this.localExtras.dailyLogs, imported.dailyLogs);
        const importedExtras = {
          profile: imported.profile,
          settings: imported.settings,
          dailyLogs: mergedDailyLogs,
        } satisfies PortableBackupExtras;
        await this.persistLocalState(merged, importedExtras);
        passphrase.value = '';
        file.value = '';
        this.importReport = this.t('backupImportedDetailed', {
          periods: merged.length,
          logs: mergedDailyLogs.length,
        });
        this.toast(this.t('backupImported'));
        this.render();
      } catch {
        file.value = '';
        this.toast(this.t('backupError'));
      }
    });
    card.append(label, actions, file);
    if (this.importReport) {
      const report = element('p', 'notice', this.importReport);
      report.setAttribute('role', 'status');
      card.append(report);
    }
    return card;
  }

  private installCard(): HTMLElement | null {
    if (window.matchMedia('(display-mode: standalone)').matches) return null;
    const card = element('section', 'info-card setting-card');
    card.append(element('h2', '', this.t('install')));
    if (this.installPrompt) {
      card.append(button('primary-button', this.t('install'), async () => {
        await this.installPrompt?.prompt();
        await this.installPrompt?.userChoice;
        this.installPrompt = null;
        this.render();
      }));
    } else {
      card.append(element('p', 'subtle', this.t('installIos')));
    }
    return card;
  }

  private bottomNavigation(): HTMLElement {
    const navigation = element('nav', 'bottom-nav');
    navigation.setAttribute('aria-label', this.t('mainNavigation'));
    const items: Array<[Screen, string]> = [
      ['today', this.t('today')],
      ['calendar', this.t('calendar')],
      ['settings', this.t('settings')],
    ];
    for (const [screen, label] of items) {
      const item = button(screen === this.screen ? 'active' : '', label, () => {
        this.screen = screen;
        this.selectedDate = null;
        this.render();
      });
      const symbol = createIcon(screen, 'nav-icon');
      item.prepend(symbol);
      item.setAttribute('aria-current', screen === this.screen ? 'page' : 'false');
      navigation.append(item);
    }
    return navigation;
  }

  private activePeriod(referenceDate = todayIso()): PeriodEvent | null {
    return this.periods
      .filter((period) => periodEndState(period, referenceDate)?.ongoing)
      .sort((left, right) => right.startDate.localeCompare(left.startDate))[0] ?? null;
  }

  private overlapsExisting(startDate: string, endDate: string | null, excludedId?: string): boolean {
    return this.periods.some((period) => {
      if (period.id === excludedId || period.deletedAt) return false;
      return periodRangesOverlap({ startDate, endDate }, period);
    });
  }

  private async startPeriod(date: string): Promise<void> {
    try { parseIsoDate(date); } catch { return; }
    if (
      this.user?.role !== 'TRACKER' ||
      this.syncing ||
      this.localVaultCoordinator.busy ||
      date > todayIso() ||
      this.activePeriod() ||
      this.overlapsExisting(date, null)
    ) return;
    const period = this.localMode
      ? {
          id: crypto.randomUUID(),
          startDate: date,
          endDate: null,
          source: 'MANUAL',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          version: 1,
        }
      : await this.serverIdentities.run(
          serverIdentityKey(window.location.origin, this.user!),
          async (assertCurrent) => {
            const queued = await queuePeriodStart(date);
            assertCurrent();
            return queued;
          },
        );
    if (this.localMode) {
      try {
        await this.persistLocalState([period, ...this.periods]);
      } catch {
        this.toast(this.t('vaultSaveFailed'));
        return;
      }
      this.toast(this.t('savedLocallyEncrypted'));
    } else {
      this.periods = [period, ...this.periods];
      await this.loadCachedData();
      this.toast(this.t('savedOffline'));
    }
    this.render();
    if (!this.localMode && navigator.onLine) await this.performSync(false);
  }

  private async endPeriod(period: PeriodEvent, date: string): Promise<void> {
    try { parseIsoDate(date); } catch { return; }
    if (
      this.user?.role !== 'TRACKER' ||
      !this.periods.some((item) => item.id === period.id && !item.deletedAt) ||
      this.syncing ||
      this.localVaultCoordinator.busy ||
      date < period.startDate ||
      date > todayIso() ||
      this.overlapsExisting(period.startDate, date, period.id)
    ) return;
    const updated = this.localMode
      ? {
          ...period,
          endDate: date,
          updatedAt: new Date().toISOString(),
          version: (period.version ?? 0) + 1,
        }
      : await this.serverIdentities.run(
          serverIdentityKey(window.location.origin, this.user!),
          async (assertCurrent) => {
            const queued = await queuePeriodEnd(period, date);
            assertCurrent();
            return queued;
          },
        );
    const updatedPeriods = this.periods.map((item) => item.id === updated.id ? updated : item);
    if (this.localMode) {
      try {
        await this.persistLocalState(updatedPeriods);
      } catch {
        this.toast(this.t('vaultSaveFailed'));
        return;
      }
    }
    else {
      this.periods = updatedPeriods;
      await this.loadCachedData();
    }
    this.toast(this.t('periodEnded'));
    this.render();
    if (!this.localMode && navigator.onLine) await this.performSync(false);
  }

  private ensureTrackerSettingsLoaded(): void {
    const userId = this.user?.id;
    if (!userId || this.localMode) return;
    if (this.sharingState?.userId !== userId) {
      this.sharingState = { userId, settings: null, failed: false };
      void this.api.sharingSettings()
        .then((settings) => { if (this.sharingState?.userId === userId) { this.sharingState.settings = settings; this.render(); } })
        .catch(() => { if (this.sharingState?.userId === userId) { this.sharingState.failed = true; this.render(); } });
    }
    if (this.profileState?.userId !== userId) {
      this.profileState = { userId, profile: null, failed: false };
      void this.api.cycleProfile()
        .then((profile) => { if (this.profileState?.userId === userId) { this.profileState.profile = profile; this.render(); } })
        .catch(() => { if (this.profileState?.userId === userId) { this.profileState.failed = true; this.render(); } });
    }
  }

  private settingsNotice(area: 'sharing' | 'profile', card: HTMLElement): void {
    if (this.settingsMessage?.area !== area) return;
    const message = element('p', this.settingsMessage.error ? 'form-error' : 'notice', this.settingsMessage.text);
    if (this.settingsMessage.error) message.setAttribute('role', 'alert');
    card.append(message);
  }

  private toggleRow(label: string, checked: boolean, disabled: boolean, onChange: (value: boolean) => void, hint?: string): HTMLElement {
    const row = element('label', 'toggle-row');
    const input = element('input') as HTMLInputElement;
    input.type = 'checkbox';
    input.checked = checked;
    input.disabled = disabled;
    input.addEventListener('change', () => onChange(input.checked));
    const text = element('span', 'toggle-text');
    text.append(element('span', '', label));
    if (hint) text.append(element('small', 'subtle', hint));
    row.append(text, input);
    return row;
  }

  /** What the partner may see; the tracker decides, also from the browser (e.g. iPhone). */
  private sharingSettingsCard(): HTMLElement {
    this.ensureTrackerSettingsLoaded();
    const card = element('section', 'info-card setting-card sharing-settings');
    card.append(element('h2', '', this.t('sharingSettingsTitle')), element('p', 'subtle', this.t('sharingSettingsIntro')));
    const state = this.sharingState;
    if (!state?.settings) {
      card.append(element('p', state?.failed ? 'form-error' : 'subtle', this.t(state?.failed ? 'settingsLoadFailed' : 'loading')));
      return card;
    }
    const settings = state.settings;
    const offline = !navigator.onLine;
    const fertilityOn = this.profileState?.profile?.fertilityPredictionEnabled === true;
    const save = (patch: Partial<SharingSettings>): void => {
      const before = { ...settings };
      Object.assign(settings, patch);
      this.settingsMessage = null;
      this.render();
      void this.api.updateSharingSettings(patch)
        .then((saved) => {
          if (this.sharingState?.userId === state.userId) this.sharingState.settings = saved;
          this.settingsMessage = { area: 'sharing', text: this.t('settingsSaved'), error: false };
        })
        .catch(() => {
          if (this.sharingState?.userId === state.userId) this.sharingState.settings = before;
          this.settingsMessage = { area: 'sharing', text: this.t('settingsSaveFailed'), error: true };
        })
        .finally(() => this.render());
    };
    card.append(
      this.toggleRow(this.t('sharingPause'), settings.sharingPaused, offline, (value) => save({ sharingPaused: value }), this.t('sharingPauseHint')),
      this.toggleRow(this.t('sharingPrediction'), settings.sharePrediction, offline, (value) => save({ sharePrediction: value })),
      this.toggleRow(this.t('sharingPeriodDates'), settings.sharePeriodDates, offline, (value) => save({ sharePeriodDates: value })),
      this.toggleRow(this.t('sharingPhase'), settings.sharePhase, offline, (value) => save({ sharePhase: value })),
      this.toggleRow(this.t('sharingFertileWindow'), settings.shareFertileWindow, offline || (!fertilityOn && !settings.shareFertileWindow),
        (value) => save({ shareFertileWindow: value }), fertilityOn ? undefined : this.t('sharingFertileNeedsEstimate')),
      this.toggleRow(this.t('sharingTips'), settings.tipsEnabled, offline, (value) => save({ tipsEnabled: value })),
    );
    if (offline) card.append(element('p', 'subtle', this.t('settingsOffline')));
    this.settingsNotice('sharing', card);
    return card;
  }

  /** Life phase, known cycle length and optional estimates for the tracker's own predictions. */
  private cycleProfileCard(): HTMLElement {
    this.ensureTrackerSettingsLoaded();
    const card = element('section', 'info-card setting-card cycle-profile');
    card.append(element('h2', '', this.t('cycleProfileTitle')), element('p', 'subtle', this.t('cycleProfileIntro')));
    const state = this.profileState;
    if (!state?.profile) {
      card.append(element('p', state?.failed ? 'form-error' : 'subtle', this.t(state?.failed ? 'settingsLoadFailed' : 'loading')));
      return card;
    }
    const profile = state.profile;
    const form = element('form', 'login-form cycle-profile-form');
    const phaseLabel = element('label', '', this.t('lifePhase'));
    const phase = element('select') as HTMLSelectElement;
    phase.name = 'specialMode';
    const phases: SpecialMode[] = ['NONE', 'HORMONAL_CONTRACEPTION', 'PREGNANCY', 'POSTPARTUM', 'MENOPAUSE_TRANSITION', 'PAUSED'];
    for (const mode of phases) {
      const option = element('option', '', this.t(`lifePhase_${mode}` as MessageKey)) as HTMLOptionElement;
      option.value = mode;
      option.selected = profile.specialMode === mode;
      phase.append(option);
    }
    phaseLabel.append(phase, element('small', 'subtle', this.t('lifePhaseHint')));
    const number = (name: string, label: MessageKey, hint: MessageKey, value: number | null, min: number, max: number): [HTMLElement, HTMLInputElement] => {
      const wrapper = element('label', '', this.t(label));
      const input = element('input') as HTMLInputElement;
      input.type = 'number';
      input.name = name;
      input.min = String(min);
      input.max = String(max);
      input.inputMode = 'numeric';
      input.placeholder = this.t('automatic');
      input.value = value === null ? '' : String(value);
      wrapper.append(input, element('small', 'subtle', this.t(hint)));
      return [wrapper, input];
    };
    const [cycleLabel, cycleLength] = number('cycleLength', 'typicalCycleLength', 'typicalCycleLengthHint', profile.typicalCycleLengthOptional, 15, 90);
    const [periodLabel, periodLength] = number('periodLength', 'typicalPeriodLength', 'typicalPeriodLengthHint', profile.typicalPeriodLengthOptional, 1, 14);
    let paused = profile.predictionPaused;
    let fertility = profile.fertilityPredictionEnabled;
    const submit = element('button', 'primary-button', this.t('save'));
    submit.type = 'submit';
    submit.disabled = !navigator.onLine;
    form.append(
      phaseLabel,
      cycleLabel,
      periodLabel,
      this.toggleRow(this.t('predictionPauseToggle'), paused, false, (value) => { paused = value; }),
      this.toggleRow(this.t('fertilityEstimateToggle'), fertility, false, (value) => { fertility = value; }, this.t('fertilityEstimateHint')),
      submit,
    );
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      const toNumber = (input: HTMLInputElement): number | null => input.value.trim() === '' ? null : Number(input.value);
      const patch: Partial<CycleProfile> = {
        specialMode: phase.value as SpecialMode,
        typicalCycleLengthOptional: toNumber(cycleLength),
        typicalPeriodLengthOptional: toNumber(periodLength),
        predictionPaused: paused,
        fertilityPredictionEnabled: fertility,
      };
      submit.disabled = true;
      this.settingsMessage = null;
      void this.api.updateCycleProfile(patch)
        .then(async (saved) => {
          if (this.profileState?.userId === state.userId) this.profileState.profile = saved;
          // Turning the estimate off also revokes its sharing on the server.
          if (!saved.fertilityPredictionEnabled && this.sharingState?.settings) this.sharingState.settings.shareFertileWindow = false;
          this.settingsMessage = { area: 'profile', text: this.t('settingsSaved'), error: false };
          await this.refreshData();
        })
        .catch(() => {
          this.settingsMessage = { area: 'profile', text: this.t('settingsSaveFailed'), error: true };
        })
        .finally(() => this.render());
    });
    card.append(form);
    if (!navigator.onLine) card.append(element('p', 'subtle', this.t('settingsOffline')));
    this.settingsNotice('profile', card);
    return card;
  }

  /** Two-step delete, so a wrong start can be removed without risking a slip of the finger. */
  private periodDeleteControl(period: PeriodEvent): HTMLElement {
    const box = element('div', 'period-delete');
    const busy = this.syncing || this.localVaultCoordinator.busy;
    if (this.confirmDeletePeriodId !== period.id) {
      const ask = button('text-button danger-text', this.t('periodDelete'), () => {
        this.confirmDeletePeriodId = period.id;
        this.render();
      });
      ask.disabled = busy;
      box.append(ask);
      return box;
    }
    const confirm = button('secondary-button danger-button', this.t('periodDeleteConfirm'), () => void this.deletePeriod(period));
    confirm.disabled = busy;
    box.append(
      element('p', 'subtle', this.t('periodDeleteQuestion')),
      confirm,
      button('text-button', this.t('cancel'), () => {
        this.confirmDeletePeriodId = null;
        this.render();
      }),
    );
    return box;
  }

  private async deletePeriod(period: PeriodEvent): Promise<void> {
    this.confirmDeletePeriodId = null;
    if (
      this.user?.role !== 'TRACKER' ||
      !this.periods.some((item) => item.id === period.id && !item.deletedAt) ||
      this.syncing ||
      this.localVaultCoordinator.busy
    ) return;
    const remaining = this.periods.filter((item) => item.id !== period.id);
    try {
      if (this.localMode) {
        await this.persistLocalState(remaining);
      } else {
        await this.serverIdentities.run(
          serverIdentityKey(window.location.origin, this.user!),
          async (assertCurrent) => {
            await queuePeriodDelete(period);
            assertCurrent();
          },
        );
        this.periods = remaining;
        await this.loadCachedData();
      }
    } catch {
      this.toast(this.t(this.localMode ? 'vaultSaveFailed' : 'periodDeleteFailed'));
      this.render();
      return;
    }
    this.selectedDate = null;
    this.toast(this.t('periodDeleted'));
    this.render();
    if (!this.localMode && navigator.onLine) await this.performSync(false);
  }

  private toast(message: string): void {
    const toast = element('div', 'toast', message);
    toast.setAttribute('role', 'status');
    document.body.append(toast);
    window.setTimeout(() => toast.remove(), 3_000);
  }

  /** Lets a signed-in account also sign in with the configured provider (e.g. Authentik). */
  private oidcLinkCard(): HTMLElement | null {
    if (this.localMode || !this.user || !this.meta || !canStartOidc(this.meta, 'link')) return null;
    const provider = this.meta.oidc?.providerName ?? 'Authentik';
    const card = element('section', 'info-card setting-card oidc-link-card');
    card.append(element('h2', '', this.t('oidcLinkTitle', { provider })));
    if (this.oidcStatusUserId !== this.user.id) {
      const userId = this.user.id;
      this.oidcStatusUserId = userId;
      this.oidcStatus = null;
      this.oidcLinkMessage = null;
      void this.api.oidcStatus()
        .then((status) => { if (this.user?.id === userId) { this.oidcStatus = status; this.render(); } })
        .catch(() => undefined);
    }
    if (this.oidcLinkMessage) {
      const message = element('p', this.oidcLinkMessage.error ? 'form-error' : 'notice', this.oidcLinkMessage.text);
      if (this.oidcLinkMessage.error) message.setAttribute('role', 'alert');
      card.append(message);
    }
    // Discard results that belong to another account or are too old to be accepted.
    if (this.pendingOidcLink && (this.pendingOidcLink.userId !== this.user.id
      || Date.now() - this.pendingOidcLink.receivedAt > 5 * 60_000)) {
      this.pendingOidcLink = null;
    }
    if (this.pendingOidcLink) {
      const form = element('form', 'login-form');
      const label = element('label', '', this.t('oidcLinkPassword'));
      const password = element('input') as HTMLInputElement;
      password.type = 'password';
      password.autocomplete = 'current-password';
      password.required = true;
      password.maxLength = 256;
      label.append(password);
      const submit = element('button', 'primary-button', this.t('oidcLinkConfirm'));
      submit.type = 'submit';
      form.append(element('p', 'subtle', this.t('oidcLinkPasswordHint', { provider })), label, submit);
      form.addEventListener('submit', async (event) => {
        event.preventDefault();
        submit.disabled = true;
        try {
          const pending = this.pendingOidcLink;
          if (!pending || pending.userId !== this.user?.id || Date.now() - pending.receivedAt > 5 * 60_000) {
            this.pendingOidcLink = null;
            throw new Error('Connection expired or belongs to another account');
          }
          await this.api.linkOidc(pending.token, password.value);
          this.pendingOidcLink = null;
          this.oidcStatus = { linked: true };
          this.oidcLinkMessage = { text: this.t('oidcLinked', { provider }), error: false };
        } catch {
          this.oidcLinkMessage = { text: this.t('oidcLinkFailed', { provider }), error: true };
        } finally {
          password.value = '';
          submit.disabled = false;
          this.render();
        }
      });
      card.append(form, button('text-button', this.t('oidcLinkCancel'), () => {
        this.pendingOidcLink = null;
        this.oidcLinkMessage = null;
        this.render();
      }));
      return card;
    }
    if (this.oidcStatus?.linked) {
      if (!this.oidcLinkMessage) card.append(element('p', 'notice', this.t('oidcLinked', { provider })));
      return card;
    }
    card.append(
      element('p', 'subtle', this.t('oidcLinkIntro', { provider })),
      button('secondary-button', this.t('oidcLinkButton', { provider }), () => {
        this.oidcLinkMessage = null;
        void startOidc(this.meta!, { kind: 'link', userId: this.user!.id }).catch(() => {
          this.oidcLinkMessage = { text: this.t('oidcUnavailable'), error: true };
          this.render();
        });
      }),
    );
    return card;
  }

  /** Partner sign-up with an invitation, so couples can use Lunara entirely in the browser. */
  private joinSection(): HTMLElement {
    const section = element('section', 'join-section');
    if (!this.invitationCode && !this.joinExpanded) {
      section.append(button('text-button', this.t('joinOpen'), () => {
        this.joinExpanded = true;
        this.render();
      }));
      return section;
    }
    const form = element('form', 'login-form join-form');
    const field = (label: string, input: HTMLInputElement, hint?: string): HTMLElement => {
      const wrapper = element('label', '', label);
      wrapper.append(input);
      if (hint) wrapper.append(element('span', 'subtle', hint));
      return wrapper;
    };
    const input = (name: string, autocomplete: AutoFill, type = 'text'): HTMLInputElement => {
      const value = element('input') as HTMLInputElement;
      value.name = name;
      value.type = type;
      value.autocomplete = autocomplete;
      value.required = true;
      return value;
    };
    const displayName = input('joinDisplayName', 'name');
    displayName.maxLength = 120;
    const username = input('joinUsername', 'username');
    username.minLength = 3;
    username.maxLength = 64;
    const password = input('joinPassword', 'new-password', 'password');
    password.minLength = 8;
    password.maxLength = 256;
    const code = input('joinCode', 'one-time-code');
    code.spellcheck = false;
    code.minLength = 8;
    code.maxLength = 12;
    code.value = this.invitationCode ?? '';
    const submit = element('button', 'secondary-button', this.t('joinSubmit'));
    submit.type = 'submit';
    const error = element('p', 'form-error');
    error.setAttribute('role', 'alert');
    form.append(
      element('h2', '', this.t('joinTitle')),
      element('p', 'subtle', this.t('joinIntro')),
      field(this.t('setupName'), displayName),
      field(this.t('username'), username),
      field(this.t('password'), password, this.t('setupPasswordHint')),
      field(this.t('joinCode'), code),
      submit,
      error,
    );
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      submit.disabled = true;
      error.textContent = '';
      try {
        const session = await this.api.join({
          username: username.value.trim(),
          password: password.value,
          displayName: displayName.value.trim(),
          invitationCode: code.value.trim(),
          preferredLanguage: this.language === 'de' ? 'DE' : 'EN',
        });
        password.value = '';
        clearPendingInvitation(sessionStorage);
        this.invitationCode = null;
        this.joinExpanded = false;
        if (!(await this.rememberUser(session.user))) throw new ApiError(409, 'Join superseded');
        await this.loadCachedData();
        await this.refreshData();
        this.render();
      } catch (joinError) {
        const status = joinError instanceof ApiError ? joinError.status : -1;
        const message = joinError instanceof Error ? joinError.message : '';
        error.textContent = status === 0 ? this.t('serverUnavailable')
          : status === 400 ? this.t('setupInvalid')
            : status === 409 && /username/i.test(message) ? this.t('joinUsernameTaken')
              : this.t('joinFailed');
      } finally {
        submit.disabled = false;
      }
    });
    section.append(form);
    return section;
  }

  /** First account on a new server, created with the setup code from the server log. */
  private setupForm(): HTMLElement {
    const form = element('form', 'login-form setup-form');
    const field = (label: string, input: HTMLInputElement, hint?: string): HTMLElement => {
      const wrapper = element('label', '', label);
      wrapper.append(input);
      if (hint) wrapper.append(element('span', 'subtle', hint));
      return wrapper;
    };
    const input = (name: string, autocomplete: AutoFill, type = 'text'): HTMLInputElement => {
      const value = element('input') as HTMLInputElement;
      value.name = name;
      value.type = type;
      value.autocomplete = autocomplete;
      value.required = true;
      return value;
    };
    const displayName = input('displayName', 'name');
    displayName.maxLength = 120;
    const username = input('username', 'username');
    username.minLength = 3;
    username.maxLength = 64;
    const password = input('password', 'new-password', 'password');
    password.minLength = 8;
    password.maxLength = 256;
    const code = input('setupCode', 'one-time-code');
    code.spellcheck = false;
    code.minLength = 20;
    code.maxLength = 256;
    const submit = element('button', 'primary-button', this.t('setupSubmit'));
    submit.type = 'submit';
    const error = element('p', 'form-error');
    error.setAttribute('role', 'alert');
    form.append(
      element('h2', '', this.t('setupTitle')),
      element('p', 'notice', this.t('setupRequired')),
      field(this.t('setupName'), displayName),
      field(this.t('username'), username),
      field(this.t('password'), password, this.t('setupPasswordHint')),
      field(this.t('setupCode'), code, this.t('setupCodeHint')),
      submit,
      error,
    );
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      submit.disabled = true;
      error.textContent = '';
      try {
        const session = await this.api.setup({
          username: username.value.trim(),
          password: password.value,
          displayName: displayName.value.trim(),
          bootstrapToken: code.value.trim(),
          preferredLanguage: this.language === 'de' ? 'DE' : 'EN',
        });
        password.value = '';
        code.value = '';
        if (this.meta) this.meta = { ...this.meta, setupRequired: false };
        if (!(await this.rememberUser(session.user))) throw new ApiError(409, 'Setup superseded');
        await this.loadCachedData();
        await this.refreshData();
        this.render();
      } catch (setupError) {
        const status = setupError instanceof ApiError ? setupError.status : -1;
        if (status === 409 && this.meta) {
          this.meta = { ...this.meta, setupRequired: false };
          this.initError = this.t('setupAlreadyDone');
          this.render();
          return;
        }
        error.textContent = status === 401 ? this.t('setupWrongCode')
          : status === 400 ? this.t('setupInvalid')
            : status === 0 ? this.t('serverUnavailable')
              : this.t('loginFailed');
      } finally {
        submit.disabled = false;
      }
    });
    return form;
  }

  /**
   * Before 1.3.5 the web app defaulted to German. Browsers that already held
   * Lunara data at their first 1.3.5 start but never stored an explicit choice
   * keep German. The decision is recorded once, so data created later in a new
   * English installation is never mistaken for an old setup.
   */
  private async keepLegacyLanguage(): Promise<void> {
    if (localStorage.getItem('lunara-language') !== null) return;
    const decided = localStorage.getItem('lunara-language-default');
    if (decided === null) {
      const existing = this.localMode
        || (await settingsStore.get<UserIdentity>('user')) != null
        || Boolean(await getLocalVaultEnvelope<LocalVaultEnvelope>());
      localStorage.setItem('lunara-language-default', existing ? 'legacy-de' : 'en');
    }
    if (localStorage.getItem('lunara-language-default') === 'legacy-de') {
      this.language = 'de';
      document.documentElement.lang = 'de';
    }
  }

  private setLanguage(language: Language): void {
    this.language = language;
    localStorage.setItem('lunara-language', language);
    void settingsStore.put('language', language);
    this.render();
  }

  private loadTheme(): ThemePreference {
    const stored = localStorage.getItem('lunara-theme');
    return stored === 'light' || stored === 'dark' ? stored : 'system';
  }

  private async setTheme(theme: ThemePreference): Promise<void> {
    this.theme = theme;
    localStorage.setItem('lunara-theme', theme);
    this.applyTheme();
    if (this.localMode && this.localVault.unlocked) {
      const extras = {
        ...this.localExtras,
        settings: {
          ...this.localExtras.settings,
          useSystemTheme: theme === 'system',
          darkMode: theme === 'dark',
        },
      } satisfies PortableBackupExtras;
      try {
        await this.persistLocalState(this.periods, extras);
      } catch {
        this.toast(this.t('vaultSaveFailed'));
      }
    }
    this.render();
  }

  private applyTheme(): void {
    document.documentElement.dataset.theme = this.theme;
  }

  private async logout(): Promise<void> {
    if (this.localMode) {
      if (!window.confirm(this.t('deleteLocalConfirm'))) return;
      try {
        await this.localVaultCoordinator.delete(
          clearLocalPrivateData,
          () => {
            this.clearLocalVaultRuntime();
            this.renderLoading();
          },
        );
      } catch {
        this.localMode = (await settingsStore.get<boolean>('local-mode')) ?? false;
        this.localVaultAvailable = Boolean(await getLocalVaultEnvelope<LocalVaultEnvelope>());
        this.loginChoice = this.localMode ? 'local' : null;
        this.render();
        this.toast(this.t('deleteLocalFailed'));
        return;
      }
      this.localMode = false;
      this.localVaultAvailable = false;
      this.user = null;
      this.periods = [];
      this.localExtras = defaultPortableExtras();
      this.prediction = null;
      this.screen = 'today';
      this.loginChoice = null;
      this.render();
      return;
    }
    await this.serverIdentities.transition(null, async () => {
      try {
        if (navigator.onLine) await this.api.logout();
        else await this.api.forgetSession();
      } finally {
        await clearServerPrivateData();
        this.user = null;
        this.periods = [];
        this.prediction = null;
        this.partnerTip = null;
        this.partnerCalendar.selectIdentity(null);
        this.syncState = { pending: 0, failed: 0, lastSuccessfulAt: null };
        this.screen = 'today';
        this.loginChoice = null;
      }
    });
    this.render();
  }
}

const updateSW = registerSW({
  onNeedRefresh() {
    const notice = element('div', 'update-notice');
    notice.setAttribute('role', 'status');
    notice.append(
      document.createTextNode(translate(document.documentElement.lang === 'en' ? 'en' : 'de', 'updateReady')),
      button('text-button', translate(document.documentElement.lang === 'en' ? 'en' : 'de', 'refresh'), () => updateSW(true)),
    );
    document.body.append(notice);
  },
});

void new LunaraWebApp().start();
