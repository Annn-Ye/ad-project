import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AuthApiError, AuthClient, CURRENT_TERMS_VERSION} from './authClient';
import {AuthSessionStore, type AuthSession} from './authSession';

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>();

  get length() { return this.values.size; }
  clear() { this.values.clear(); }
  getItem(key: string) { return this.values.get(key) ?? null; }
  key(index: number) { return [...this.values.keys()][index] ?? null; }
  removeItem(key: string) { this.values.delete(key); }
  setItem(key: string, value: string) { this.values.set(key, value); }
}

const recruiter = {
  userId: '11111111-1111-1111-1111-111111111111',
  role: 'RECRUITER' as const,
  fullName: 'River Recruiter',
  email: 'river@example.com',
  avatarUrl: null,
  permissions: [],
  company: {companyId: '22222222-2222-2222-2222-222222222222', name: 'River Labs'},
  createdAt: '2026-08-10T01:00:00Z',
  updatedAt: '2026-08-10T01:00:00Z',
};

function authEnvelope(role = 'RECRUITER', permissions: string[] = []) {
  return {data: {
    accessToken: 'access-one',
    refreshToken: 'refresh-one',
    expiresIn: 7200,
    refreshExpiresIn: 2592000,
    user: {...recruiter, role, permissions, company: role === 'RECRUITER' ? recruiter.company : null},
  }};
}

function tokenEnvelope() {
  return {data: {
    accessToken: 'access-two',
    refreshToken: 'refresh-two',
    expiresIn: 7200,
    refreshExpiresIn: 2592000,
  }};
}

function errorEnvelope(code: string, message: string, fieldErrors: Record<string, string> = {}) {
  return {error: {code, message, fieldErrors, requestId: 'request-test-1'}};
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

function session(): AuthSession {
  return {
    accessToken: 'access-old',
    refreshToken: 'refresh-old',
    accessTokenExpiresAt: 10_000,
    refreshTokenExpiresAt: 20_000,
    user: recruiter,
    remember: false,
  };
}

function setup(fetcher = vi.fn()) {
  const temporary = new MemoryStorage();
  const persistent = new MemoryStorage();
  const sessions = new AuthSessionStore(temporary, persistent);
  const client = new AuthClient(sessions, fetcher as unknown as typeof fetch, '/api/v1', () => 1_000);
  return {client, fetcher, sessions, temporary, persistent};
}

function requestAt(fetcher: ReturnType<typeof vi.fn>, index: number) {
  const [url, init] = fetcher.mock.calls[index] as [string, RequestInit];
  return {url, init, headers: new Headers(init.headers), body: init.body ? JSON.parse(String(init.body)) : null};
}

describe('AuthClient', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('signs in a recruiter and stores the authenticated identity', async () => {
    const fetcher = vi.fn().mockResolvedValue(json(authEnvelope()));
    const {client, sessions} = setup(fetcher);

    const user = await client.signIn({email: recruiter.email, password: 'Password1!', remember: true});

    expect(user).toEqual(recruiter);
    expect(sessions.getSnapshot()).toMatchObject({accessToken: 'access-one', refreshToken: 'refresh-one', remember: true, user: recruiter});
    expect(requestAt(fetcher, 0)).toMatchObject({
      url: '/api/v1/auth/login',
      body: {email: recruiter.email, password: 'Password1!'},
    });
  });

  it('signs in a platform admin without changing the candidate business role', async () => {
    const fetcher = vi.fn().mockResolvedValue(json(authEnvelope('CANDIDATE', ['PLATFORM_ADMIN'])));
    const {client, sessions} = setup(fetcher);

    const user = await client.signInAdmin({email: recruiter.email, password: 'Password1!', remember: false});

    expect(user).toMatchObject({role: 'CANDIDATE', permissions: ['PLATFORM_ADMIN'], company: null});
    expect(sessions.getSnapshot()?.user).toEqual(user);
  });

  it('rejects the admin portal login when the database-backed permission is absent', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(json(authEnvelope('CANDIDATE')))
      .mockResolvedValueOnce(new Response(null, {status: 204}));
    const {client, sessions} = setup(fetcher);

    await expect(client.signInAdmin({email: recruiter.email, password: 'Password1!', remember: false}))
      .rejects.toMatchObject({status: 403, code: 'WRONG_ROLE'});

    expect(sessions.getSnapshot()).toBeNull();
    expect(requestAt(fetcher, 1).url).toBe('/api/v1/auth/logout');
  });

  it('registers a recruiter with the exact role, company, and terms fields', async () => {
    const fetcher = vi.fn().mockResolvedValue(json(authEnvelope(), 201));
    const {client, sessions} = setup(fetcher);

    await client.register({
      fullName: recruiter.fullName,
      companyName: recruiter.company.name,
      email: recruiter.email,
      password: 'Password1!',
      acceptedTermsVersion: CURRENT_TERMS_VERSION,
    });

    expect(requestAt(fetcher, 0)).toMatchObject({
      url: '/api/v1/auth/register',
      body: {
        role: 'RECRUITER', fullName: recruiter.fullName, companyName: recruiter.company.name,
        email: recruiter.email, password: 'Password1!', acceptedTermsVersion: CURRENT_TERMS_VERSION,
      },
    });
    expect(sessions.getSnapshot()?.user.company).toEqual(recruiter.company);
  });

  it.each([
    ['wrong password', 401, 'UNAUTHORIZED', {}],
    ['nonexistent account', 401, 'UNAUTHORIZED', {}],
    ['duplicate email', 409, 'EMAIL_ALREADY_REGISTERED', {}],
    ['field validation', 422, 'VALIDATION_ERROR', {email: 'must be a well-formed email address'}],
  ])('maps %s ErrorResponse without storing a session', async (_name, status, code, fieldErrors) => {
    const fetcher = vi.fn().mockResolvedValue(json(errorEnvelope(code, 'Contract message', fieldErrors), status));
    const {client, sessions} = setup(fetcher);

    const error = await client.signIn({email: recruiter.email, password: 'wrong-pass', remember: false})
      .catch(caught => caught);

    expect(error).toBeInstanceOf(AuthApiError);
    expect(error).toMatchObject({status, code, fieldErrors, requestId: 'request-test-1'});
    expect(sessions.getSnapshot()).toBeNull();
  });

  it.each(['CANDIDATE', 'ADMIN'])('rejects a %s login and revokes its issued refresh token', async role => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(json(authEnvelope(role)))
      .mockResolvedValueOnce(new Response(null, {status: 204}));
    const {client, sessions} = setup(fetcher);

    await expect(client.signIn({email: recruiter.email, password: 'Password1!', remember: false}))
      .rejects.toMatchObject({status: 403, code: 'WRONG_ROLE'});

    expect(sessions.getSnapshot()).toBeNull();
    expect(requestAt(fetcher, 1)).toMatchObject({
      url: '/api/v1/auth/logout',
      body: {refreshToken: 'refresh-one'},
    });
    expect(requestAt(fetcher, 1).headers.get('Authorization')).toBe('Bearer access-one');
  });

  it('presents a safe network error', async () => {
    const fetcher = vi.fn().mockRejectedValue(new TypeError('private network detail'));
    const {client} = setup(fetcher);

    await expect(client.signIn({email: recruiter.email, password: 'Password1!', remember: false}))
      .rejects.toMatchObject({status: 0, code: 'NETWORK_ERROR'});
  });

  it('refreshes once after 401, rotates both tokens, and retries with the new access token', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(json(errorEnvelope('UNAUTHORIZED', 'Expired'), 401))
      .mockResolvedValueOnce(json(tokenEnvelope()))
      .mockResolvedValueOnce(json({data: {ok: true}}));
    const {client, sessions, temporary} = setup(fetcher);
    sessions.save(session());

    await expect(client.requestWithAuth<{data: {ok: boolean}}>('/protected')).resolves.toEqual({data: {ok: true}});

    expect(fetcher).toHaveBeenCalledTimes(3);
    expect(requestAt(fetcher, 1)).toMatchObject({url: '/api/v1/auth/refresh', body: {refreshToken: 'refresh-old'}});
    expect(requestAt(fetcher, 2).headers.get('Authorization')).toBe('Bearer access-two');
    expect(sessions.getSnapshot()).toMatchObject({accessToken: 'access-two', refreshToken: 'refresh-two'});
    expect(temporary.getItem('ad_recruiter_auth_v1')).not.toContain('refresh-old');
  });

  it('clears the session when refresh fails and never retries indefinitely', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(json(errorEnvelope('UNAUTHORIZED', 'Expired'), 401))
      .mockResolvedValueOnce(json(errorEnvelope('UNAUTHORIZED', 'Invalid refresh'), 401));
    const {client, sessions} = setup(fetcher);
    sessions.save(session());

    await expect(client.requestWithAuth('/protected')).rejects.toMatchObject({code: 'SESSION_EXPIRED'});

    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(sessions.getSnapshot()).toBeNull();
  });

  it('clears the session when database-backed admin access has been revoked', async () => {
    const fetcher = vi.fn().mockResolvedValue(json(errorEnvelope('FORBIDDEN', 'Insufficient permission'), 403));
    const {client, sessions} = setup(fetcher);
    sessions.save(session());

    await expect(client.requestWithAuth('/admin/users')).rejects.toMatchObject({status: 403, code: 'FORBIDDEN'});

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(sessions.getSnapshot()).toBeNull();
  });

  it('sends access and refresh tokens on logout and always clears local state', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(null, {status: 204}));
    const {client, sessions} = setup(fetcher);
    sessions.save(session());

    await client.logout();

    const request = requestAt(fetcher, 0);
    expect(request.url).toBe('/api/v1/auth/logout');
    expect(request.headers.get('Authorization')).toBe('Bearer access-old');
    expect(request.body).toEqual({refreshToken: 'refresh-old'});
    expect(sessions.getSnapshot()).toBeNull();
  });

  it('clears local state even when logout fails', async () => {
    const fetcher = vi.fn().mockResolvedValue(json(errorEnvelope('INTERNAL_ERROR', 'Failed'), 500));
    const {client, sessions} = setup(fetcher);
    sessions.save(session());

    await expect(client.logout()).rejects.toBeInstanceOf(AuthApiError);
    expect(sessions.getSnapshot()).toBeNull();
  });
});
