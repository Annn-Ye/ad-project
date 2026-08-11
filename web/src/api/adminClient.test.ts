import {describe, expect, it, vi} from 'vitest';
import type {AuthClient} from './authClient';
import {AdminClient} from './adminClient';
import type {AdminUser, CompanyReview, ModerationCase} from '../models/admin';

const user: AdminUser = {
  userId: '11111111-1111-1111-1111-111111111111', fullName: 'Ada Admin', email: 'ada@example.com',
  role: 'CANDIDATE', status: 'ACTIVE', adminAccess: false, company: null, version: 7,
  createdAt: '2026-08-11T08:00:00Z', updatedAt: '2026-08-11T08:00:00Z',
};

describe('AdminClient', () => {
  it('serializes user filters and optimistic versions exactly once', async () => {
    const requestWithAuth = vi.fn()
      .mockResolvedValueOnce({data: [], meta: {page: 2, pageSize: 20, total: 0, hasNext: false}})
      .mockResolvedValueOnce({data: {...user, status: 'DISABLED', version: 8}})
      .mockResolvedValueOnce({data: {...user, adminAccess: true, version: 8}});
    const client = new AdminClient({requestWithAuth} as unknown as Pick<AuthClient, 'requestWithAuth'>);

    await client.listUsers({q: 'Ada Admin', role: 'CANDIDATE', adminAccess: false, page: 2, pageSize: 20});
    await client.changeUserStatus(user, 'DISABLED', 'Security review');
    await client.changeAdminAccess(user, true, 'Operations coverage');

    expect(requestWithAuth.mock.calls[0][0]).toBe('/admin/users?q=Ada+Admin&role=CANDIDATE&adminAccess=false&page=2&pageSize=20');
    expect(requestWithAuth.mock.calls[1]).toEqual(['/admin/users/11111111-1111-1111-1111-111111111111/status', {
      method: 'POST', body: JSON.stringify({status: 'DISABLED', reason: 'Security review', expectedVersion: 7}),
    }]);
    expect(requestWithAuth.mock.calls[2][1]).toEqual({
      method: 'POST', body: JSON.stringify({enabled: true, reason: 'Operations coverage', expectedVersion: 7}),
    });
  });

  it('uses the declared company and moderation decision contracts', async () => {
    const company = {companyId: 'company-1', version: 3} as CompanyReview;
    const moderationCase = {caseId: 'case-1', version: 5} as ModerationCase;
    const requestWithAuth = vi.fn().mockResolvedValue({data: {}});
    const client = new AdminClient({requestWithAuth} as unknown as Pick<AuthClient, 'requestWithAuth'>);

    await client.reviewCompany(company, 'REQUEST_CHANGES', 'Add website evidence');
    await client.decideModeration(moderationCase, 'REMOVE', 'Community rule violation');

    expect(requestWithAuth.mock.calls[0]).toEqual(['/admin/companies/company-1/request-changes', {
      method: 'POST', body: JSON.stringify({reason: 'Add website evidence', expectedVersion: 3}),
    }]);
    expect(requestWithAuth.mock.calls[1]).toEqual(['/admin/moderation/cases/case-1/decision', {
      method: 'POST', body: JSON.stringify({decision: 'REMOVE', reason: 'Community rule violation', expectedVersion: 5}),
    }]);
  });
});
