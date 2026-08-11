export type BusinessRole = 'CANDIDATE' | 'RECRUITER';
export type UserStatus = 'ACTIVE' | 'DISABLED';
export type CompanyVerificationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED';
export type ModerationSourceType = 'COMMUNITY_POST' | 'COMMUNITY_COMMENT';
export type ModerationStatus = 'PENDING' | 'KEPT' | 'REMOVED';

export interface PageMeta {
  page: number;
  pageSize: number;
  total: number;
  hasNext: boolean;
}

export interface AdminUser {
  userId: string;
  fullName: string;
  email: string;
  role: BusinessRole;
  status: UserStatus;
  adminAccess: boolean;
  company: null | {
    companyId: string;
    name: string;
    verificationStatus: CompanyVerificationStatus;
  };
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CompanyReview {
  companyId: string;
  name: string;
  logoUrl: string | null;
  stage: string | null;
  employeeRange: string | null;
  verificationStatus: CompanyVerificationStatus;
  website: string | null;
  description: string | null;
  location: string | null;
  version: number;
  createdByUserId: string;
  createdByName: string;
  createdByEmail: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ModerationCase {
  caseId: string;
  sourceType: ModerationSourceType;
  sourceId: string;
  authorId: string | null;
  authorName: string | null;
  contentSnapshot: string;
  reportReason: string;
  reportCount: number;
  status: ModerationStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface AuditEvent {
  auditEventId: string;
  actorId: string | null;
  actorName: string;
  action: string;
  targetType: string;
  targetId: string;
  beforeState: string | null;
  afterState: string | null;
  reason: string;
  requestId: string;
  occurredAt: string;
}

export interface ListResponse<T> {
  data: T[];
  meta: PageMeta;
}

export interface DataResponse<T> {
  data: T;
}
