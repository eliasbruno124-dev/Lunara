import {
  Injectable,
  ServiceUnavailableException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  createRemoteJWKSet,
  errors,
  jwtVerify,
  type JWTVerifyGetKey,
  type JWTVerifyOptions,
  type KeyLike,
} from 'jose';

export interface OidcVerifierConfiguration {
  enabled: boolean;
  providerName: string;
  issuer: string;
  audience: string;
  jwksUrl: string;
  allowedAlgorithms: string[];
  maxTokenAgeSeconds: number;
  clockToleranceSeconds: number;
}

export interface VerifiedOidcIdentity {
  issuer: string;
  subject: string;
  issuedAt: number;
  expiresAt: number;
  /** Signed provider authentication time, never inferred from token issue time. */
  authenticatedAt?: number;
  displayName?: string;
  preferredUsername?: string;
}

type VerificationKey = KeyLike | Uint8Array | JWTVerifyGetKey;

const OIDC_USERNAME_PATTERN =
  /^[\p{L}\p{N}][\p{L}\p{N}._-]*$/u;

function strictProfileClaim(
  value: unknown,
  minimumLength: number,
  maximumLength: number,
  pattern?: RegExp,
): string | undefined {
  if (typeof value !== 'string') return undefined;
  const codePointLength = [...value].length;
  if (
    codePointLength < minimumLength ||
    codePointLength > maximumLength ||
    value !== value.trim() ||
    value !== value.normalize('NFKC') ||
    /\p{C}/u.test(value) ||
    (pattern && !pattern.test(value))
  ) {
    return undefined;
  }
  return value;
}

export async function verifyOidcAccessToken(
  token: string,
  configuration: OidcVerifierConfiguration,
  verificationKey: VerificationKey,
  now = new Date(),
): Promise<VerifiedOidcIdentity> {
  const options: JWTVerifyOptions = {
    issuer: configuration.issuer,
    audience: configuration.audience,
    algorithms: configuration.allowedAlgorithms,
    requiredClaims: ['iss', 'aud', 'sub', 'iat', 'exp'],
    clockTolerance: configuration.clockToleranceSeconds,
    currentDate: now,
  };
  const { payload } =
    typeof verificationKey === 'function'
      ? await jwtVerify(token, verificationKey, options)
      : await jwtVerify(token, verificationKey, options);

  const subject = payload.sub;
  if (
    typeof subject !== 'string' ||
    subject.length < 1 ||
    subject.length > 255 ||
    subject !== subject.trim() ||
    [...subject].some((character) => {
      const codePoint = character.codePointAt(0) ?? 0;
      return codePoint <= 31 || codePoint === 127;
    })
  ) {
    throw new Error('OIDC subject is invalid');
  }
  if (
    !Number.isSafeInteger(payload.iat) ||
    !Number.isSafeInteger(payload.exp)
  ) {
    throw new Error('OIDC token timestamps are invalid');
  }

  const issuedAt = payload.iat as number;
  const expiresAt = payload.exp as number;
  const nowSeconds = Math.floor(now.getTime() / 1_000);
  if (
    issuedAt > nowSeconds + configuration.clockToleranceSeconds ||
    nowSeconds - issuedAt >
      configuration.maxTokenAgeSeconds + configuration.clockToleranceSeconds ||
    expiresAt <= issuedAt ||
    expiresAt - issuedAt >
      configuration.maxTokenAgeSeconds + configuration.clockToleranceSeconds
  ) {
    throw new Error('OIDC token age is invalid');
  }

  const audiences = Array.isArray(payload.aud)
    ? payload.aud
    : typeof payload.aud === 'string'
      ? [payload.aud]
      : [];
  const authorizedParty = payload.azp;
  if (
    (authorizedParty !== undefined &&
      authorizedParty !== configuration.audience) ||
    (audiences.length > 1 && authorizedParty !== configuration.audience)
  ) {
    throw new Error('OIDC authorized party is invalid');
  }

  // Profile values are display hints for first-use provisioning only. They are
  // accepted exclusively when the signed access token says that the standard
  // profile scope was granted. Neither value is ever used to find or link an
  // existing Lunara account.
  const scopes =
    typeof payload.scope === 'string'
      ? new Set(payload.scope.split(/\s+/u).filter(Boolean))
      : new Set<string>();
  const displayName = scopes.has('profile')
    ? strictProfileClaim(payload.name, 1, 120)
    : undefined;
  const preferredUsername = scopes.has('profile')
    ? strictProfileClaim(payload.preferred_username, 3, 64, OIDC_USERNAME_PATTERN)
    : undefined;

  return {
    issuer: configuration.issuer,
    subject,
    issuedAt,
    expiresAt,
    ...(Number.isSafeInteger(payload.auth_time)
      ? { authenticatedAt: payload.auth_time as number }
      : {}),
    ...(displayName ? { displayName } : {}),
    ...(preferredUsername ? { preferredUsername } : {}),
  };
}

@Injectable()
export class OidcTokenVerifier {
  private readonly configuration: OidcVerifierConfiguration;
  private readonly remoteKeySet?: JWTVerifyGetKey;

  constructor(config: ConfigService) {
    this.configuration = {
      enabled: config.get<boolean>('lunara.oidc.enabled', false),
      providerName: config.get<string>(
        'lunara.oidc.providerName',
        'Authentik',
      ),
      issuer: config.get<string>('lunara.oidc.issuer', ''),
      audience: config.get<string>('lunara.oidc.audience', ''),
      jwksUrl: config.get<string>('lunara.oidc.jwksUrl', ''),
      allowedAlgorithms: config.get<string[]>(
        'lunara.oidc.allowedAlgorithms',
        ['RS256'],
      ),
      maxTokenAgeSeconds: config.get<number>(
        'lunara.oidc.maxTokenAgeSeconds',
        3_600,
      ),
      clockToleranceSeconds: config.get<number>(
        'lunara.oidc.clockToleranceSeconds',
        30,
      ),
    };
    if (this.configuration.enabled) {
      const remoteKeySet = createRemoteJWKSet(
        new URL(this.configuration.jwksUrl),
        {
          timeoutDuration: 5_000,
          cooldownDuration: 30_000,
          cacheMaxAge: 600_000,
        },
      );
      this.remoteKeySet = async (header, token) => {
        try {
          return await remoteKeySet(header, token);
        } catch (error) {
          // A successfully fetched key set that does not recognize this token
          // is an authentication failure. Transport, HTTP and malformed JWKS
          // failures instead mean that the provider cannot currently verify
          // anyone: fail closed without instructing clients to erase a session.
          if (
            error instanceof errors.JWKSNoMatchingKey ||
            error instanceof errors.JOSENotSupported
          ) {
            throw error;
          }
          throw new ServiceUnavailableException(
            'External sign-in is temporarily unavailable',
          );
        }
      };
    }
  }

  isEnabled(): boolean {
    return this.configuration.enabled;
  }

  publicConfiguration(): Pick<
    OidcVerifierConfiguration,
    'enabled' | 'providerName' | 'issuer' | 'audience'
  > {
    return {
      enabled: this.configuration.enabled,
      providerName: this.configuration.providerName,
      issuer: this.configuration.issuer,
      audience: this.configuration.audience,
    };
  }

  async verify(token: string): Promise<VerifiedOidcIdentity> {
    if (!this.configuration.enabled || !this.remoteKeySet) {
      throw new ServiceUnavailableException(
        'External sign-in is not configured',
      );
    }
    try {
      return await verifyOidcAccessToken(
        token,
        this.configuration,
        this.remoteKeySet,
      );
    } catch (error) {
      if (error instanceof ServiceUnavailableException) throw error;
      throw new UnauthorizedException('External sign-in could not be verified');
    }
  }

  async verifyFreshAuthentication(token: string): Promise<VerifiedOidcIdentity> {
    const identity = await this.verify(token);
    const now = Math.floor(Date.now() / 1_000);
    // A freshly refreshed token is not a fresh login. Requiring auth_time
    // makes prompt=login/max_age=0 meaningful even if a client is malicious.
    if (
      !Number.isSafeInteger(identity.authenticatedAt) ||
      identity.authenticatedAt! > now + 30 ||
      now - identity.authenticatedAt! > 300 ||
      identity.issuedAt + 30 < identity.authenticatedAt!
    ) {
      throw new UnauthorizedException('A fresh external sign-in is required');
    }
    return identity;
  }
}
