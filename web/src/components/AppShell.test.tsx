import '@testing-library/jest-dom/vitest';
import {act, cleanup, render, screen, waitFor} from '@testing-library/react';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {AuthClient} from '../api/authClient';
import {AuthSessionStore} from '../api/authSession';
import {AppShell} from './AppShell';

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>();
  get length() { return this.values.size; }
  clear() { this.values.clear(); }
  getItem(key: string) { return this.values.get(key) ?? null; }
  key(index: number) { return [...this.values.keys()][index] ?? null; }
  removeItem(key: string) { this.values.delete(key); }
  setItem(key: string, value: string) { this.values.set(key, value); }
}

function error401() {
  return new Response(JSON.stringify({error: {
    code: 'UNAUTHORIZED', message: 'Expired', fieldErrors: {}, requestId: 'request-shell-1',
  }}), {status: 401, headers: {'Content-Type': 'application/json'}});
}

describe('AppShell', () => {
  afterEach(cleanup);

  it('redirects to sign-in when refresh fails and the session is cleared', async () => {
    const sessions = new AuthSessionStore(new MemoryStorage(), new MemoryStorage());
    sessions.save({
      accessToken: 'access-old', refreshToken: 'refresh-old',
      accessTokenExpiresAt: 10_000, refreshTokenExpiresAt: 20_000, remember: false,
      user: {
        userId: 'user-1', role: 'RECRUITER', fullName: 'Real Recruiter', email: 'real@example.com', avatarUrl: null,
        permissions: [],
        company: {companyId: 'company-1', name: 'Real Company'},
        createdAt: '2026-08-10T01:00:00Z', updatedAt: '2026-08-10T01:00:00Z',
      },
    });
    const fetcher = vi.fn().mockResolvedValueOnce(error401()).mockResolvedValueOnce(error401());
    const client = new AuthClient(sessions, fetcher as unknown as typeof fetch, '/api/v1');
    const router = createMemoryRouter([
      {path: '/recruiter', element: <AppShell client={client} sessions={sessions}/>, children: [
        {path: 'dashboard', element: <div>Dashboard content</div>},
      ]},
      {path: '/recruiter/sign-in', element: <div>Sign-in screen</div>},
    ], {initialEntries: ['/recruiter/dashboard']});
    render(<RouterProvider router={router}/>);

    expect(screen.getByText('Real Recruiter')).toBeInTheDocument();
    expect(screen.getByText('Real Company')).toBeInTheDocument();
    await act(async () => {
      await expect(client.requestWithAuth('/protected')).rejects.toMatchObject({code: 'SESSION_EXPIRED'});
    });

    await waitFor(() => expect(screen.getByText('Sign-in screen')).toBeInTheDocument());
    expect(sessions.getSnapshot()).toBeNull();
  });
});
