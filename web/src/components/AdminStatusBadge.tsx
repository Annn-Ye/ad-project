import type {CompanyVerificationStatus, ModerationStatus, UserStatus} from '../models/admin';

type Status = CompanyVerificationStatus | ModerationStatus | UserStatus;
const labels: Record<Status, string> = {
  ACTIVE: 'Active',
  DISABLED: 'Disabled',
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CHANGES_REQUESTED: 'Changes requested',
  KEPT: 'Kept',
  REMOVED: 'Removed',
};

export function AdminStatusBadge({status}: {status: Status}) {
  return <span className={`admin-badge ${status.toLowerCase()}`}>{labels[status]}</span>;
}
