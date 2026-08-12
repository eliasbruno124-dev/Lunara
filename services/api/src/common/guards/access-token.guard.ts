import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { Reflector } from '@nestjs/core';
import { UserRole } from '@prisma/client';
import { Request } from 'express';
import { IS_PUBLIC_KEY } from '../decorators/public.decorator.js';
import { ROLES_KEY } from '../decorators/roles.decorator.js';
import { AccessTokenPayload, AuthenticatedUser } from '../types/authenticated-user.js';
import { PrismaService } from '../../prisma/prisma.service.js';

type AuthenticatedRequest = Request & { user?: AuthenticatedUser };

@Injectable()
export class AccessTokenGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly jwt: JwtService,
    private readonly prisma: PrismaService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (isPublic) return true;

    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const token = this.extractBearer(request);
    if (!token) throw new UnauthorizedException('Authentication required');

    let payload: AccessTokenPayload;
    try {
      payload = await this.jwt.verifyAsync<AccessTokenPayload>(token);
    } catch {
      throw new UnauthorizedException('Invalid or expired access token');
    }
    if (payload.typ !== 'access' || !payload.sub || !payload.sid) {
      throw new UnauthorizedException('Invalid access token');
    }

    const session = await this.prisma.deviceSession.findFirst({
      where: {
        id: payload.sid,
        userId: payload.sub,
        revokedAt: null,
        expiresAt: { gt: new Date() },
        user: { disabledAt: null },
      },
      include: {
        user: {
          include: {
            memberships: {
              take: 1,
              orderBy: { createdAt: 'asc' },
            },
          },
        },
      },
    });
    const membership = session?.user.memberships[0];
    if (!session || !membership) {
      throw new UnauthorizedException('Session is no longer active');
    }

    request.user = {
      userId: session.user.id,
      sessionId: session.id,
      householdId: membership.householdId,
      membershipId: membership.id,
      role: membership.role,
      username: session.user.username,
      displayName: session.user.displayName,
      preferredLanguage: session.user.preferredLanguage,
    };

    const allowedRoles = this.reflector.getAllAndOverride<UserRole[]>(ROLES_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (allowedRoles && !allowedRoles.includes(membership.role)) {
      throw new ForbiddenException('This account does not have permission for this action');
    }
    return true;
  }

  private extractBearer(request: Request): string | undefined {
    // X-Lunara-Authorization is intentionally checked first. A reverse proxy may
    // consume Authorization: Basic while the native app puts its API token here.
    const candidates = [
      request.header('x-lunara-authorization'),
      request.header('authorization'),
    ];
    for (const value of candidates) {
      if (!value) continue;
      const match = /^Bearer\s+([^\s]+)$/i.exec(value.trim());
      if (match?.[1]) return match[1];
    }
    return undefined;
  }
}
