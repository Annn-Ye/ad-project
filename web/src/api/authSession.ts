export interface AuthCompany {
  companyId: string;
  name: string;
}

export type BusinessRole = 'CANDIDATE' | 'RECRUITER';
export type Permission = 'PLATFORM_ADMIN';

export interface AuthenticatedUser {
  userId: string;
  role: BusinessRole;
  fullName: string;
  email: string;
  avatarUrl: string | null;
  permissions: Permission[];
  company: AuthCompany | null;
  createdAt: string;
  updatedAt: string;
}

export interface AuthenticatedRecruiter extends AuthenticatedUser {
  role: 'RECRUITER';
  company: AuthCompany;
}

export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: number;
  refreshTokenExpiresAt: number;
  user: AuthenticatedUser;
  remember: boolean;
}

const SESSION_KEY = 'ad_recruiter_auth_v1';

export class AuthSessionStore {
  private snapshot: AuthSession | null;
  private readonly listeners = new Set<() => void>();

  constructor(
    private readonly sessionStorage: Storage,
    private readonly persistentStorage: Storage,
  ) {
    this.snapshot = this.readStoredSession();
  }

  getSnapshot = (): AuthSession | null => this.snapshot;

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  save(session: AuthSession): void {
    const target = session.remember ? this.persistentStorage : this.sessionStorage;
    const stale = session.remember ? this.sessionStorage : this.persistentStorage;
    target.setItem(SESSION_KEY, JSON.stringify(session));
    stale.removeItem(SESSION_KEY);
    this.snapshot = session;
    this.emit();
  }

  replaceTokens(
    expectedRefreshToken: string,
    tokens: Pick<AuthSession, 'accessToken' | 'refreshToken' | 'accessTokenExpiresAt' | 'refreshTokenExpiresAt'>,
  ): AuthSession {
    const current = this.snapshot;
    if (!current || current.refreshToken !== expectedRefreshToken) {
      throw new Error('The authentication session changed during token rotation.');
    }
    const replacement = {...current, ...tokens};
    this.save(replacement);
    return replacement;
  }

  clear(): void {
    this.sessionStorage.removeItem(SESSION_KEY);
    this.persistentStorage.removeItem(SESSION_KEY);
    if (this.snapshot !== null) {
      this.snapshot = null;
      this.emit();
    }
  }

  private readStoredSession(): AuthSession | null {
    const sessionValue = this.sessionStorage.getItem(SESSION_KEY);
    const persistentValue = this.persistentStorage.getItem(SESSION_KEY);
    const value = sessionValue ?? persistentValue;
    if (!value) return null;
    try {
      const parsed: unknown = JSON.parse(value);
      if (!isAuthSession(parsed)) throw new Error('Invalid stored session');
      return {...parsed, user: {
        ...parsed.user,
        permissions: Array.isArray(parsed.user.permissions) ? parsed.user.permissions : [],
      }};
    } catch {
      this.sessionStorage.removeItem(SESSION_KEY);
      this.persistentStorage.removeItem(SESSION_KEY);
      return null;
    }
  }

  private emit(): void {
    this.listeners.forEach(listener => listener());
  }
}

function isAuthSession(value: unknown): value is AuthSession {
  if (!isRecord(value) || typeof value.accessToken !== 'string' || typeof value.refreshToken !== 'string') return false;
  if (typeof value.accessTokenExpiresAt !== 'number' || typeof value.refreshTokenExpiresAt !== 'number') return false;
  if (typeof value.remember !== 'boolean' || !isRecord(value.user)) return false;
  return (value.user.role === 'RECRUITER' || value.user.role === 'CANDIDATE') &&
    typeof value.user.userId === 'string' &&
    typeof value.user.fullName === 'string' && typeof value.user.email === 'string' &&
    (value.user.permissions === undefined || (Array.isArray(value.user.permissions) &&
      value.user.permissions.every(permission => permission === 'PLATFORM_ADMIN'))) &&
    (value.user.company === null || (isRecord(value.user.company) &&
      typeof value.user.company.companyId === 'string' && typeof value.user.company.name === 'string'));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export const authSession = new AuthSessionStore(window.sessionStorage, window.localStorage);
