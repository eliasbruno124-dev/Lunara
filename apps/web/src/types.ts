export type Language = 'de' | 'en';
export type Role = 'TRACKER' | 'PARTNER';

export interface MetaResponse {
  name: string;
  version: string;
  apiVersion: 'v1';
  setupRequired: boolean;
  registrationEnabled: boolean;
  serverTime: string;
  gatewayHeaderName?: 'X-Lunara-Authorization';
  authenticationMethods?: Array<'PASSWORD' | 'OIDC'>;
  oidc?: {
    providerName: string;
    issuer: string;
    audience: string;
    discoveryUrl: string;
    clientId?: string;
    scopes?: string[];
  };
}

export interface UserIdentity {
  id: string;
  username: string;
  displayName: string;
  preferredLanguage: 'DE' | 'EN';
  role: Role;
  householdId: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresInSeconds: number;
  sessionId: string;
  user: UserIdentity;
}

export interface PeriodEvent {
  id: string;
  startDate: string;
  endDate: string | null;
  source?: string;
  createdAt?: string;
  updatedAt?: string;
  deletedAt?: string | null;
  version?: number;
  pending?: boolean;
}

export interface Prediction {
  available: boolean;
  predictedStartFrom?: string;
  predictedStartTo?: string;
  predictedEndFrom?: string;
  predictedEndTo?: string;
  fertileWindowFrom?: string | null;
  fertileWindowTo?: string | null;
  fertilityEstimate?: boolean;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH' | 'NONE';
  basedOnCycleCount?: number;
  variationDays?: number;
  explanation?: string;
  reason?: string;
}

export interface PartnerDashboard {
  prediction?: Prediction | null;
  phase?: string | null;
  periodDates?: Array<{ startDate: string; endDate: string | null }>;
  tip?: {
    title?: string;
    body?: string;
    callToAction?: string;
  } | null;
  todayTip?: {
    title?: string;
    body?: string;
    callToAction?: string;
  } | null;
  sharingPaused?: boolean;
}

export interface PartnerTip {
  id?: string;
  title?: string;
  body?: string;
  callToAction?: string;
  category?: string;
}

export interface SyncMutation {
  clientMutationId: string;
  entityId: string;
  entityType: 'PERIOD';
  operation: 'UPSERT' | 'DELETE';
  payload: Record<string, unknown>;
  createdAt: number;
  attemptCount: number;
  nextAttemptAt: number;
  state: 'PENDING' | 'FAILED';
  lastError?: string;
}

export interface SyncResponse {
  cursor: string;
  hasMore: boolean;
  results?: Array<{
    clientMutationId: string;
    entity?: PeriodEvent;
    id?: string;
    deleted?: boolean;
  }>;
  changes?: Array<{
    entityType: string;
    operation: string;
    entityId: string;
    entity: PeriodEvent | null;
  }>;
  partnerDashboard?: PartnerDashboard;
}
