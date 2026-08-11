import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {AuthApiError} from './authClient';
import {adminClient} from './adminClient';
import type {CompanyListParams, ModerationListParams, UserListParams} from './adminClient';
import type {AdminUser, CompanyReview, ModerationCase, UserStatus} from '../models/admin';

export const adminKeys = {
  me: ['admin', 'me'] as const,
  users: (params: UserListParams) => ['admin', 'users', params] as const,
  companies: (params: CompanyListParams) => ['admin', 'companies', params] as const,
  moderation: (params: ModerationListParams) => ['admin', 'moderation', params] as const,
  audit: (params: Record<string, unknown>) => ['admin', 'audit', params] as const,
};

export const useAdminMe = (enabled = true) => useQuery({
  queryKey: adminKeys.me, queryFn: () => adminClient.me(), retry: false, enabled,
});
export const useAdminUsers = (params: UserListParams) => useQuery({
  queryKey: adminKeys.users(params), queryFn: () => adminClient.listUsers(params),
});
export const useAdminCompanies = (params: CompanyListParams) => useQuery({
  queryKey: adminKeys.companies(params), queryFn: () => adminClient.listCompanies(params),
});
export const useModerationCases = (params: ModerationListParams) => useQuery({
  queryKey: adminKeys.moderation(params), queryFn: () => adminClient.listModerationCases(params),
});
export const useAuditEvents = (params: Record<string, unknown>) => useQuery({
  queryKey: adminKeys.audit(params), queryFn: () => adminClient.listAuditEvents(params),
});

export const useChangeUserStatus = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({user, status, reason}: {user: AdminUser; status: UserStatus; reason: string}) =>
      adminClient.changeUserStatus(user, status, reason),
    onSuccess: () => queryClient.invalidateQueries({queryKey: ['admin']}),
    onError: error => refreshAfterConflict(error, queryClient, ['admin', 'users']),
  });
};

export const useChangeAdminAccess = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({user, enabled, reason}: {user: AdminUser; enabled: boolean; reason: string}) =>
      adminClient.changeAdminAccess(user, enabled, reason),
    onSuccess: () => queryClient.invalidateQueries({queryKey: ['admin']}),
    onError: error => refreshAfterConflict(error, queryClient, ['admin', 'users']),
  });
};

export const useReviewCompany = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({company, decision, reason}: {
      company: CompanyReview; decision: 'APPROVE' | 'REJECT' | 'REQUEST_CHANGES'; reason: string;
    }) => adminClient.reviewCompany(company, decision, reason),
    onSuccess: () => queryClient.invalidateQueries({queryKey: ['admin']}),
    onError: error => refreshAfterConflict(error, queryClient, ['admin', 'companies']),
  });
};

export const useModerationDecision = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({moderationCase, decision, reason}: {
      moderationCase: ModerationCase; decision: 'KEEP' | 'REMOVE'; reason: string;
    }) => adminClient.decideModeration(moderationCase, decision, reason),
    onSuccess: () => queryClient.invalidateQueries({queryKey: ['admin']}),
    onError: error => refreshAfterConflict(error, queryClient, ['admin', 'moderation']),
  });
};

function refreshAfterConflict(error: Error, queryClient: ReturnType<typeof useQueryClient>, queryKey: string[]) {
  if (error instanceof AuthApiError && error.code === 'VERSION_CONFLICT') {
    void queryClient.invalidateQueries({queryKey});
  }
}
