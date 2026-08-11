import {API_BASE_URL, apiPaths} from './contract';
import {
  authSession,
  type AuthenticatedUser,
  type AuthenticatedRecruiter,
  type AuthSession,
  type AuthSessionStore,
} from './authSession';

export const CURRENT_TERMS_VERSION = '2026-08';

const ACCESS_TOKEN_SECONDS = 7200;
const REFRESH_TOKEN_SECONDS = 2592000;

interface TokenData {
  accessToken: string;
  refreshToken: string;
  expiresIn: 7200;
  refreshExpiresIn: 2592000;
}

interface AuthIdentity {
  userId: string;
  role: string;
  fullName: string;
  email: string;
  avatarUrl: string | null;
  permissions: string[];
  company: {companyId: string; name: string} | null;
  createdAt: string;
  updatedAt: string;
}

interface AuthData extends TokenData {
  user: AuthIdentity;
}

export interface SignInInput {
  email: string;
  password: string;
  remember: boolean;
}

export interface RegisterRecruiterInput {
  fullName: string;
  companyName: string;
  email: string;
  password: string;
  acceptedTermsVersion: string;
}

export interface ApiErrorDetail {
  code: string;
  message: string;
  fieldErrors: Record<string, string>;
  requestId: string;
}

export class AuthApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly fieldErrors: Record<string, string> = {},
    public readonly requestId = '',
  ) {
    super(message);
    this.name = 'AuthApiError';
  }
}

export class AuthClient {
  private refreshInFlight: Promise<AuthSession> | null = null;

  constructor(
    private readonly sessions: AuthSessionStore,
    private readonly fetcher: typeof fetch = globalThis.fetch.bind(globalThis),
    private readonly baseUrl = API_BASE_URL,
    private readonly now: () => number = Date.now,
  ) {}

  async signIn(input: SignInInput): Promise<AuthenticatedRecruiter> {
    this.sessions.clear();
    const response = await this.send(apiPaths.login, {
      method: 'POST',
      body: JSON.stringify({email: input.email, password: input.password}),
    });
    const auth = await this.readAuthResponse(response);
    return this.acceptRecruiter(auth, input.remember);
  }

  async signInAdmin(input: SignInInput): Promise<AuthenticatedUser> {
    this.sessions.clear();
    const response = await this.send(apiPaths.login, {
      method: 'POST',
      body: JSON.stringify({email: input.email, password: input.password}),
    });
    const auth = await this.readAuthResponse(response);
    if (!auth.user.permissions.includes('PLATFORM_ADMIN')) {
      await this.revokeIssuedTokens(auth);
      this.sessions.clear();
      throw new AuthApiError(403, 'WRONG_ROLE', 'This account cannot access the admin workspace.');
    }
    const user = this.toAuthenticatedUser(auth.user);
    this.sessions.save({...this.toStoredTokens(auth), user, remember: input.remember});
    return user;
  }

  async register(input: RegisterRecruiterInput): Promise<AuthenticatedRecruiter> {
    this.sessions.clear();
    const response = await this.send(apiPaths.register, {
      method: 'POST',
      body: JSON.stringify({
        role: 'RECRUITER',
        fullName: input.fullName,
        companyName: input.companyName,
        email: input.email,
        password: input.password,
        acceptedTermsVersion: input.acceptedTermsVersion,
      }),
    });
    const auth = await this.readAuthResponse(response);
    return this.acceptRecruiter(auth, false);
  }

  async logout(): Promise<void> {
    const session = this.sessions.getSnapshot();
    try {
      if (!session) return;
      const response = await this.send(apiPaths.logout, {
        method: 'POST',
        headers: {Authorization: `Bearer ${session.accessToken}`},
        body: JSON.stringify({refreshToken: session.refreshToken}),
      });
      if (!response.ok) await this.throwApiError(response);
    } finally {
      this.sessions.clear();
    }
  }

  async requestWithAuth<T>(path: string, init: RequestInit = {}): Promise<T> {
    let session = this.requireSession();
    let response = await this.send(path, this.withAuthorization(init, session.accessToken));
    if (response.status === 401) {
      try {
        session = await this.refreshSession();
      } catch {
        this.sessions.clear();
        throw new AuthApiError(401, 'SESSION_EXPIRED', 'Your session has expired. Please sign in again.');
      }
      response = await this.send(path, this.withAuthorization(init, session.accessToken));
      if (response.status === 401) {
        this.sessions.clear();
        throw new AuthApiError(401, 'SESSION_EXPIRED', 'Your session has expired. Please sign in again.');
      }
    }
    if (response.status === 403 && (path === '/admin' || path.startsWith('/admin/'))) {
      this.sessions.clear();
    }
    if (!response.ok) await this.throwApiError(response);
    if (response.status === 204) return undefined as T;
    return this.readJson<T>(response);
  }

  private async refreshSession(): Promise<AuthSession> {
    if (this.refreshInFlight) return this.refreshInFlight;
    this.refreshInFlight = this.performRefresh();
    try {
      return await this.refreshInFlight;
    } finally {
      this.refreshInFlight = null;
    }
  }

  private async performRefresh(): Promise<AuthSession> {
    const current = this.requireSession();
    const response = await this.send(apiPaths.refresh, {
      method: 'POST',
      body: JSON.stringify({refreshToken: current.refreshToken}),
    });
    if (!response.ok) await this.throwApiError(response);
    const tokens = parseTokenEnvelope(await this.readJson<unknown>(response));
    return this.sessions.replaceTokens(current.refreshToken, this.toStoredTokens(tokens));
  }

  private async acceptRecruiter(auth: AuthData, remember: boolean): Promise<AuthenticatedRecruiter> {
    if (auth.user.role !== 'RECRUITER') {
      await this.revokeIssuedTokens(auth);
      this.sessions.clear();
      throw new AuthApiError(403, 'WRONG_ROLE', 'This account cannot access the recruiter workspace.');
    }
    if (!auth.user.company) {
      await this.revokeIssuedTokens(auth);
      this.sessions.clear();
      throw unexpectedResponse();
    }
    const user: AuthenticatedRecruiter = {
      ...auth.user,
      role: 'RECRUITER',
      permissions: auth.user.permissions.filter(permission => permission === 'PLATFORM_ADMIN') as ['PLATFORM_ADMIN'] | [],
      company: auth.user.company,
    };
    this.sessions.save({...this.toStoredTokens(auth), user, remember});
    return user;
  }

  private toAuthenticatedUser(identity: AuthIdentity): AuthenticatedUser {
    if (identity.role !== 'CANDIDATE' && identity.role !== 'RECRUITER') throw unexpectedResponse();
    return {
      ...identity,
      role: identity.role,
      permissions: identity.permissions.filter(permission => permission === 'PLATFORM_ADMIN') as ['PLATFORM_ADMIN'] | [],
    };
  }

  private async revokeIssuedTokens(auth: AuthData): Promise<void> {
    try {
      await this.send(apiPaths.logout, {
        method: 'POST',
        headers: {Authorization: `Bearer ${auth.accessToken}`},
        body: JSON.stringify({refreshToken: auth.refreshToken}),
      });
    } catch {
      // Best-effort server revocation; tokens are never persisted for a rejected role.
    }
  }

  private toStoredTokens(tokens: TokenData) {
    const now = this.now();
    return {
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      accessTokenExpiresAt: now + tokens.expiresIn * 1000,
      refreshTokenExpiresAt: now + tokens.refreshExpiresIn * 1000,
    };
  }

  private requireSession(): AuthSession {
    const session = this.sessions.getSnapshot();
    if (!session) throw new AuthApiError(401, 'SESSION_EXPIRED', 'Your session has expired. Please sign in again.');
    return session;
  }

  private withAuthorization(init: RequestInit, accessToken: string): RequestInit {
    const headers = new Headers(init.headers);
    headers.set('Authorization', `Bearer ${accessToken}`);
    return {...init, headers};
  }

  private async readAuthResponse(response: Response): Promise<AuthData> {
    if (!response.ok) await this.throwApiError(response);
    return parseAuthEnvelope(await this.readJson<unknown>(response));
  }

  private async send(path: string, init: RequestInit): Promise<Response> {
    const headers = new Headers(init.headers);
    headers.set('Accept', 'application/json');
    if (init.body !== undefined) headers.set('Content-Type', 'application/json');
    try {
      return await this.fetcher(`${this.baseUrl}${path}`, {...init, headers});
    } catch {
      throw new AuthApiError(0, 'NETWORK_ERROR', 'Unable to reach the server. Check your connection and try again.');
    }
  }

  private async throwApiError(response: Response): Promise<never> {
    let payload: unknown;
    try {
      payload = await response.json();
    } catch {
      throw unexpectedResponse(response.status);
    }
    if (!isRecord(payload) || !isRecord(payload.error)) throw unexpectedResponse(response.status);
    const error = payload.error;
    if (typeof error.code !== 'string' || typeof error.message !== 'string' ||
        typeof error.requestId !== 'string' || !isStringRecord(error.fieldErrors)) {
      throw unexpectedResponse(response.status);
    }
    throw new AuthApiError(response.status, error.code, error.message, error.fieldErrors, error.requestId);
  }

  private async readJson<T>(response: Response): Promise<T> {
    try {
      return await response.json() as T;
    } catch {
      throw unexpectedResponse(response.status);
    }
  }
}

function parseAuthEnvelope(payload: unknown): AuthData {
  if (!isRecord(payload) || !isRecord(payload.data)) throw unexpectedResponse();
  const tokens = parseTokenData(payload.data);
  const user = payload.data.user;
  if (!isRecord(user) || typeof user.userId !== 'string' || typeof user.role !== 'string' ||
      typeof user.fullName !== 'string' || typeof user.email !== 'string' ||
      !(typeof user.avatarUrl === 'string' || user.avatarUrl === null) ||
      !(user.permissions === undefined || (Array.isArray(user.permissions) &&
        user.permissions.every(permission => typeof permission === 'string'))) ||
      typeof user.createdAt !== 'string' || typeof user.updatedAt !== 'string') {
    throw unexpectedResponse();
  }
  let company: AuthIdentity['company'] = null;
  if (user.company !== null && user.company !== undefined) {
    if (!isRecord(user.company) || typeof user.company.companyId !== 'string' || typeof user.company.name !== 'string') {
      throw unexpectedResponse();
    }
    company = {companyId: user.company.companyId, name: user.company.name};
  }
  return {...tokens, user: {
    userId: user.userId,
    role: user.role,
    fullName: user.fullName,
    email: user.email,
    avatarUrl: user.avatarUrl,
    permissions: (user.permissions ?? []) as string[],
    company,
    createdAt: user.createdAt,
    updatedAt: user.updatedAt,
  }};
}

function parseTokenEnvelope(payload: unknown): TokenData {
  if (!isRecord(payload) || !isRecord(payload.data)) throw unexpectedResponse();
  return parseTokenData(payload.data);
}

function parseTokenData(data: Record<string, unknown>): TokenData {
  if (typeof data.accessToken !== 'string' || typeof data.refreshToken !== 'string' ||
      data.expiresIn !== ACCESS_TOKEN_SECONDS || data.refreshExpiresIn !== REFRESH_TOKEN_SECONDS) {
    throw unexpectedResponse();
  }
  return data as unknown as TokenData;
}

function unexpectedResponse(status = 0): AuthApiError {
  return new AuthApiError(status, 'UNEXPECTED_RESPONSE', 'The server returned an unexpected response.');
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isStringRecord(value: unknown): value is Record<string, string> {
  return isRecord(value) && Object.values(value).every(item => typeof item === 'string');
}

export const authClient = new AuthClient(authSession);
