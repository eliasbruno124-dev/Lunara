import { PreferredLanguage, UserRole } from '@prisma/client';

export interface AuthenticatedUser {
  userId: string;
  sessionId: string;
  householdId: string;
  membershipId: string;
  role: UserRole;
  username: string;
  displayName: string;
  preferredLanguage: PreferredLanguage;
}

export interface AccessTokenPayload {
  sub: string;
  sid: string;
  typ: 'access';
  iat?: number;
  exp?: number;
}
