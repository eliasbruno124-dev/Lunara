import 'dotenv/config';
import { PrismaPg } from '@prisma/adapter-pg';
import { AuditEventType, PrismaClient } from '@prisma/client';
import { isServerBootstrapped } from '../common/database/database-coordination.js';
import {
  isSupportedCommand,
  parseArguments,
  requiredUuid,
  requireConfirmation,
} from './admin-cli.arguments.js';
import { lockUserAuthentication } from '../common/database/database-coordination.js';
import {
  oidcLinkConfirmation,
  optionalBoolean,
  validateOidcIssuer,
  validateOidcSubject,
} from './oidc-link.js';

async function main(): Promise<void> {
  const parsed = parseArguments(process.argv.slice(2));
  if (!isSupportedCommand(parsed.command)) {
    usage();
    process.exitCode = 2;
    return;
  }
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) throw new Error('DATABASE_URL is required');
  const prisma = new PrismaClient({ adapter: new PrismaPg({ connectionString: databaseUrl }) });
  try {
    const [area, action] = parsed.command;
    if (area === 'users' && action === 'list') {
      await listUsers(prisma);
    } else if (area === 'users' && action === 'disable') {
      await disableUser(prisma, parsed.options);
    } else if (area === 'users' && action === 'link-oidc') {
      await linkOidcIdentity(prisma, parsed.options);
    } else if (area === 'sessions' && action === 'revoke-all') {
      await revokeAllSessions(prisma, parsed.options);
    } else if (area === 'bootstrap' && action === 'status') {
      await bootstrapStatus(prisma);
    } else if (area === 'invitations' && action === 'list') {
      await listInvitations(prisma);
    } else if (area === 'invitations' && action === 'revoke') {
      await revokeInvitation(prisma, parsed.options);
    }
  } finally {
    await prisma.$disconnect();
  }
}

async function listUsers(prisma: PrismaClient): Promise<void> {
  const users = await prisma.user.findMany({
    orderBy: { createdAt: 'asc' },
    select: {
      id: true,
      username: true,
      displayName: true,
      preferredLanguage: true,
      disabledAt: true,
      createdAt: true,
      passwordHash: true,
      memberships: {
        select: { householdId: true, role: true, customRoleLabel: true },
      },
      _count: { select: { sessions: true, externalIdentities: true } },
    },
  });
  output({
    users: users.map(({ passwordHash, ...user }) => ({
      ...user,
      passwordLoginEnabled: passwordHash !== null,
    })),
  });
}

async function linkOidcIdentity(
  prisma: PrismaClient,
  options: Map<string, string>,
): Promise<void> {
  const userId = requiredUuid(options, 'user-id');
  if (process.env.OIDC_ENABLED?.toLowerCase() !== 'true') {
    throw new Error('OIDC_ENABLED=true is required before linking identities');
  }
  const configuredIssuer = process.env.OIDC_ISSUER?.trim() ?? '';
  const issuer = validateOidcIssuer(options.get('issuer') ?? '', configuredIssuer);
  const subject = validateOidcSubject(options.get('subject'));
  const disablePassword = optionalBoolean(
    options,
    'disable-password',
    false,
  );
  requireConfirmation(
    options,
    oidcLinkConfirmation(userId, issuer, subject),
  );

  const result = await prisma.$transaction(async (tx) => {
    await lockUserAuthentication(tx, userId);
    const account = await tx.user.findUnique({
      where: { id: userId },
      select: {
        id: true,
        passwordHash: true,
        disabledAt: true,
        memberships: { take: 1, select: { householdId: true } },
      },
    });
    if (!account) throw new Error(`User not found: ${userId}`);
    if (account.disabledAt) throw new Error(`User is disabled: ${userId}`);

    const [bySubject, forUser] = await Promise.all([
      tx.externalIdentity.findUnique({
        where: { issuer_subject: { issuer, subject } },
      }),
      tx.externalIdentity.findUnique({
        where: { userId_issuer: { userId, issuer } },
      }),
    ]);
    if (bySubject && bySubject.userId !== userId) {
      throw new Error('OIDC identity is already linked to another user');
    }
    if (forUser && forUser.subject !== subject) {
      throw new Error('User is already linked to a different subject for this issuer');
    }
    const identity =
      bySubject ??
      (await tx.externalIdentity.create({
        data: { userId, issuer, subject },
      }));

    let sessionsRevoked = 0;
    let passwordLoginDisabled = false;
    if (disablePassword && account.passwordHash !== null) {
      await tx.user.update({
        where: { id: userId },
        data: { passwordHash: null },
      });
      const revoked = await tx.deviceSession.updateMany({
        where: { userId, revokedAt: null },
        data: { revokedAt: new Date() },
      });
      sessionsRevoked = revoked.count;
      passwordLoginDisabled = true;
    }
    const identityCreated = !bySubject;
    if (identityCreated || passwordLoginDisabled) {
      await tx.auditEvent.create({
        data: {
          householdId: account.memberships[0]?.householdId,
          type: AuditEventType.OIDC_IDENTITY_LINKED,
          metadata: {
            targetUserId: userId,
            identityId: identity.id,
            administrative: true,
            identityCreated,
            passwordLoginDisabled,
            sessionsRevoked,
          },
        },
      });
    }
    return {
      identityId: identity.id,
      identityCreated,
      passwordLoginEnabled: disablePassword
        ? false
        : account.passwordHash !== null,
      sessionsRevoked,
    };
  });
  output({ userId, ...result });
}

async function disableUser(
  prisma: PrismaClient,
  options: Map<string, string>,
): Promise<void> {
  const userId = requiredUuid(options, 'user-id');
  requireConfirmation(options, `DISABLE:${userId}`);
  const account = await prisma.user.findUnique({
    where: { id: userId },
    include: { memberships: { select: { householdId: true } } },
  });
  if (!account) throw new Error(`User not found: ${userId}`);
  const now = new Date();
  const result = await prisma.$transaction(async (tx) => {
    const disabled = await tx.user.updateMany({
      where: { id: userId, disabledAt: null },
      data: { disabledAt: now },
    });
    const sessions = await tx.deviceSession.updateMany({
      where: { userId, revokedAt: null },
      data: { revokedAt: now },
    });
    if (disabled.count > 0) {
      await tx.auditEvent.create({
        data: {
          householdId: account.memberships[0]?.householdId,
          type: AuditEventType.ACCOUNT_DISABLED,
          metadata: { targetUserId: userId, sessionsRevoked: sessions.count },
        },
      });
    }
    return { disabled: disabled.count === 1, sessionsRevoked: sessions.count };
  });
  output({ userId, ...result });
}

async function revokeAllSessions(
  prisma: PrismaClient,
  options: Map<string, string>,
): Promise<void> {
  const userId = requiredUuid(options, 'user-id');
  requireConfirmation(options, `REVOKE-SESSIONS:${userId}`);
  const account = await prisma.user.findUnique({
    where: { id: userId },
    select: { id: true, memberships: { select: { householdId: true } } },
  });
  if (!account) throw new Error(`User not found: ${userId}`);
  const count = await prisma.$transaction(async (tx) => {
    const revoked = await tx.deviceSession.updateMany({
      where: { userId, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    if (revoked.count > 0) {
      await tx.auditEvent.create({
        data: {
          householdId: account.memberships[0]?.householdId,
          type: AuditEventType.SESSION_REVOKED,
          metadata: { targetUserId: userId, allSessions: true, sessionsRevoked: revoked.count },
        },
      });
    }
    return revoked.count;
  });
  output({ userId, sessionsRevoked: count });
}

async function bootstrapStatus(prisma: PrismaClient): Promise<void> {
  const [bootstrapped, state, users, households] = await Promise.all([
    isServerBootstrapped(prisma),
    prisma.serverState.findUniqueOrThrow({
      where: { id: 1 },
      select: { bootstrappedAt: true },
    }),
    prisma.user.count(),
    prisma.household.count(),
  ]);
  output({
    setupRequired: !bootstrapped,
    bootstrappedAt: state.bootstrappedAt,
    users,
    households,
  });
}

async function listInvitations(prisma: PrismaClient): Promise<void> {
  const invitations = await prisma.invitation.findMany({
    orderBy: { createdAt: 'desc' },
    take: 100,
    select: {
      id: true,
      householdId: true,
      assignedRole: true,
      createdAt: true,
      expiresAt: true,
      usedAt: true,
      revokedAt: true,
      createdBy: { select: { id: true, username: true } },
    },
  });
  // tokenHash is deliberately absent from the projection.
  output({ invitations });
}

async function revokeInvitation(
  prisma: PrismaClient,
  options: Map<string, string>,
): Promise<void> {
  const invitationId = requiredUuid(options, 'invitation-id');
  requireConfirmation(options, `REVOKE-INVITATION:${invitationId}`);
  const invitation = await prisma.invitation.findUnique({
    where: { id: invitationId },
    select: { id: true, householdId: true },
  });
  if (!invitation) throw new Error(`Invitation not found: ${invitationId}`);
  const revoked = await prisma.$transaction(async (tx) => {
    const result = await tx.invitation.updateMany({
      where: { id: invitationId, usedAt: null, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    if (result.count > 0) {
      await tx.auditEvent.create({
        data: {
          householdId: invitation.householdId,
          type: AuditEventType.INVITATION_REVOKED,
          metadata: { invitationId, administrative: true },
        },
      });
    }
    return result.count === 1;
  });
  output({ invitationId, revoked });
}

function output(value: unknown): void {
  process.stdout.write(`${JSON.stringify(value, null, 2)}\n`);
}

function usage(): void {
  process.stderr.write(`Lunara administrative CLI

Read-only:
  admin users list
  admin bootstrap status
  admin invitations list

Targeted writes (exact UUID and confirmation required):
  admin users disable --user-id UUID --confirm DISABLE:UUID
  admin users link-oidc --user-id UUID --issuer HTTPS_URL --subject EXACT_SUBJECT --disable-password true|false --confirm LINK-OIDC:UUID:FINGERPRINT
  admin sessions revoke-all --user-id UUID --confirm REVOKE-SESSIONS:UUID
  admin invitations revoke --invitation-id UUID --confirm REVOKE-INVITATION:UUID
`);
}

void main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : 'Unknown administrative error';
  process.stderr.write(`Admin command failed: ${message}\n`);
  process.exitCode = 1;
});
