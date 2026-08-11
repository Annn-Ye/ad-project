import '@testing-library/jest-dom/vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {cleanup, fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {adminClient} from '../api/adminClient';
import {AuthApiError} from '../api/authClient';
import type {AdminUser, ListResponse} from '../models/admin';
import {AdminUsersPage} from './AdminUsersPage';

const user: AdminUser = {
  userId: '11111111-1111-1111-1111-111111111111', fullName: 'Ada Admin', email: 'ada@example.com',
  role: 'CANDIDATE', status: 'ACTIVE', adminAccess: false, company: null, version: 1,
  createdAt: '2026-08-11T08:00:00Z', updatedAt: '2026-08-11T08:00:00Z',
};

function response(data: AdminUser[]): ListResponse<AdminUser> {
  return {data, meta: {page: 1, pageSize: 20, total: data.length, hasNext: false}};
}

function renderPage() {
  const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
  return render(<QueryClientProvider client={queryClient}><AdminUsersPage/></QueryClientProvider>);
}

describe('AdminUsersPage states', () => {
  afterEach(() => {cleanup(); vi.restoreAllMocks()});

  it('covers loading, content, confirmation, submitting and success feedback', async () => {
    vi.spyOn(adminClient, 'listUsers').mockResolvedValue(response([user]));
    let finish!: (value: AdminUser) => void;
    const pending = new Promise<AdminUser>(resolve => {finish = resolve});
    const change = vi.spyOn(adminClient, 'changeUserStatus').mockReturnValue(pending);
    renderPage();

    expect(screen.getByText('Loading accounts…')).toBeInTheDocument();
    expect((await screen.findAllByText('ada@example.com')).length).toBe(2);
    fireEvent.click(screen.getByRole('button', {name: 'Disable account'}));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Disable account'}));
    expect(screen.getByRole('alert')).toHaveTextContent('A reason is required');
    fireEvent.change(screen.getByPlaceholderText('Explain why this action is necessary'), {target: {value: 'Compromised credential'}});
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Disable account'}));
    await waitFor(() => expect(change).toHaveBeenCalledWith(user, 'DISABLED', 'Compromised credential'));
    expect(screen.getByRole('button', {name: 'Saving…'})).toBeDisabled();
    finish({...user, status: 'DISABLED', version: 2});
    expect(await screen.findByText('Ada Admin was disabled.')).toBeInTheDocument();
  });

  it('renders empty and request-aware error states with retry', async () => {
    vi.spyOn(adminClient, 'listUsers').mockResolvedValue(response([]));
    renderPage();
    expect(await screen.findByText('No accounts found')).toBeInTheDocument();
    cleanup();
    vi.restoreAllMocks();

    const list = vi.spyOn(adminClient, 'listUsers').mockRejectedValue(
      new AuthApiError(500, 'INTERNAL_ERROR', 'Private server detail', {}, 'req_admin_list_1'));
    renderPage();
    expect(await screen.findByText('Request ID: req_admin_list_1')).toBeInTheDocument();
    expect(screen.queryByText('Private server detail')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Try again'}));
    await waitFor(() => expect(list.mock.calls.length).toBeGreaterThan(1));
  });

  it('refreshes a version conflict and requires a fresh confirmation', async () => {
    const latest = {...user, version: 2};
    const list = vi.spyOn(adminClient, 'listUsers').mockResolvedValueOnce(response([user])).mockResolvedValue(response([latest]));
    vi.spyOn(adminClient, 'changeUserStatus').mockRejectedValue(
      new AuthApiError(409, 'VERSION_CONFLICT', 'Changed', {}, 'req_conflict_1'));
    renderPage();
    await screen.findAllByText('ada@example.com');
    fireEvent.click(screen.getByRole('button', {name: 'Disable account'}));
    fireEvent.change(screen.getByPlaceholderText('Explain why this action is necessary'), {target: {value: 'Security review'}});
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', {name: 'Disable account'}));

    expect(await screen.findByText(/latest version is loading/)).toBeInTheDocument();
    await waitFor(() => expect(list.mock.calls.length).toBeGreaterThan(1));
    expect(await screen.findByText('v2')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
