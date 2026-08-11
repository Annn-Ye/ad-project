import {useState, type FormEvent} from 'react';
import {AuthApiError} from '../api/authClient';
import {useAdminUsers, useChangeAdminAccess, useChangeUserStatus} from '../api/adminQueries';
import {AdminActionDialog} from '../components/AdminActionDialog';
import {AdminStatusBadge} from '../components/AdminStatusBadge';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';
import type {AdminUser, BusinessRole, UserStatus} from '../models/admin';

type UserAction = {kind: 'STATUS' | 'ACCESS'; user: AdminUser} | null;

export function AdminUsersPage() {
  const [searchInput, setSearchInput] = useState('');
  const [q, setQ] = useState('');
  const [role, setRole] = useState<BusinessRole | ''>('');
  const [status, setStatus] = useState<UserStatus | ''>('');
  const [adminOnly, setAdminOnly] = useState(false);
  const [page, setPage] = useState(1);
  const [selectedId, setSelectedId] = useState('');
  const [action, setAction] = useState<UserAction>(null);
  const [notice, setNotice] = useState('');
  const users = useAdminUsers({q, role, status, adminAccess: adminOnly ? true : undefined, page, pageSize: 20});
  const statusMutation = useChangeUserStatus();
  const accessMutation = useChangeAdminAccess();
  const data = users.data?.data ?? [];
  const selected = data.find(user => user.userId === selectedId) ?? data[0] ?? null;

  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    setPage(1);
    setQ(searchInput.trim());
  };
  const mutation = action?.kind === 'STATUS' ? statusMutation : accessMutation;
  const mutationError = mutation.error instanceof AuthApiError
    ? `${mutation.error.message}${mutation.error.requestId ? ` Request ID: ${mutation.error.requestId}` : ''}` : undefined;
  const confirm = (reason: string) => {
    if (!action) return;
    if (action.kind === 'STATUS') {
      statusMutation.mutate({
        user: action.user,
        status: action.user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
        reason,
      }, {onSuccess: () => {setAction(null); setNotice(`${action.user.fullName} was ${action.user.status === 'ACTIVE' ? 'disabled' : 'enabled'}.`)}, onError: handleMutationError});
    } else {
      accessMutation.mutate({user: action.user, enabled: !action.user.adminAccess, reason},
        {onSuccess: () => {setAction(null); setNotice(`Admin access for ${action.user.fullName} was ${action.user.adminAccess ? 'revoked' : 'granted'}.`)}, onError: handleMutationError});
    }
  };
  const handleMutationError = (caught: Error) => {
    if (caught instanceof AuthApiError && caught.code === 'VERSION_CONFLICT') {
      setAction(null);
      setNotice('This account changed while the dialog was open. The latest version is loading; review it and confirm the action again.');
    }
  };

  return <section className="admin-page">
    <header className="admin-page-header">
      <div><span className="admin-eyebrow">USERS & ACCESS</span><h1>Users and company control</h1>
        <p>Manage platform access without changing each user’s business identity.</p></div>
    </header>
    {notice && <div className="admin-page-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="Dismiss notification">×</button></div>}
    <div className="admin-metrics">
      <Metric label="Matching accounts" value={users.data?.meta.total ?? '—'} note="Current filters"/>
      <Metric label="Recruiter seats" value={data.filter(user => user.role === 'RECRUITER').length} note="On this page"/>
      <Metric label="Platform admins" value={data.filter(user => user.adminAccess).length} note="On this page"/>
      <Metric label="Disabled users" value={data.filter(user => user.status === 'DISABLED').length} note="On this page"/>
    </div>
    <div className="admin-directory-layout">
      <div className="admin-table-card">
        <div className="admin-card-title"><div><h2>Account directory</h2><p>Search and select an account to review.</p></div></div>
        <form className="admin-filters" onSubmit={submitSearch}>
          <input value={searchInput} onChange={event => setSearchInput(event.target.value)}
            placeholder="Search name or email" aria-label="Search users"/>
          <select value={role} onChange={event => {setRole(event.target.value as BusinessRole | ''); setPage(1)}}>
            <option value="">All roles</option><option value="CANDIDATE">Candidate</option><option value="RECRUITER">Recruiter</option>
          </select>
          <select value={status} onChange={event => {setStatus(event.target.value as UserStatus | ''); setPage(1)}}>
            <option value="">All statuses</option><option value="ACTIVE">Active</option><option value="DISABLED">Disabled</option>
          </select>
          <label className="admin-check"><input type="checkbox" checked={adminOnly}
            onChange={event => {setAdminOnly(event.target.checked); setPage(1)}}/>Admin access</label>
          <button className="button secondary" type="submit">Search</button>
        </form>
        {users.isPending ? <LoadingState label="Loading accounts…"/> : users.isError ?
          <ErrorState error={users.error} onRetry={() => users.refetch()}/> : data.length === 0 ?
            <EmptyState title="No accounts found" description="Try clearing one or more filters."/> : <>
              <div className="admin-table user-admin-table">
                <div className="admin-table-head"><span>Account</span><span>Role</span><span>Company</span><span>Status</span><span>Access</span></div>
                {data.map(user => <button key={user.userId} className={`admin-table-row ${selected?.userId === user.userId ? 'selected' : ''}`}
                  onClick={() => setSelectedId(user.userId)}>
                  <span className="admin-person"><i className="avatar">{user.fullName.slice(0, 1).toUpperCase()}</i>
                    <span><b>{user.fullName}</b><small>{user.email}</small></span></span>
                  <span>{titleCase(user.role)}</span><span>{user.company?.name ?? '—'}</span>
                  <span><AdminStatusBadge status={user.status}/></span>
                  <span>{user.adminAccess ? <span className="admin-badge admin">Admin</span> : 'Standard'}</span>
                </button>)}
              </div>
              <Pagination page={page} total={users.data.meta.total} pageSize={users.data.meta.pageSize}
                hasNext={users.data.meta.hasNext} onPage={setPage}/>
            </>}
      </div>
      <aside className="admin-detail-card">
        {selected ? <>
          <div className="admin-detail-heading"><span className="avatar xl">{selected.fullName.slice(0, 1).toUpperCase()}</span>
            <div><span className="admin-eyebrow">SELECTED ACCOUNT</span><h2>{selected.fullName}</h2><p>{selected.email}</p></div></div>
          <div className="admin-detail-grid">
            <Detail label="Business role" value={titleCase(selected.role)}/><Detail label="Account status" value={titleCase(selected.status)}/>
            <Detail label="Company" value={selected.company?.name ?? 'No company'}/><Detail label="Admin access" value={selected.adminAccess ? 'Granted' : 'Not granted'}/>
            <Detail label="Created" value={formatDate(selected.createdAt)}/><Detail label="Version" value={`v${selected.version}`}/>
          </div>
          {selected.company && <div className="admin-signal"><span>Company verification</span><AdminStatusBadge status={selected.company.verificationStatus}/></div>}
          <div className="admin-notice"><b>Protected action</b><p>Every change requires a reason and is written to the audit log.</p></div>
          <div className="admin-detail-actions">
            <button className={`button ${selected.status === 'ACTIVE' ? 'danger' : 'soft'}`} onClick={() => {statusMutation.reset(); setAction({kind: 'STATUS', user: selected})}}>
              {selected.status === 'ACTIVE' ? 'Disable account' : 'Enable account'}
            </button>
            <button className="button secondary" onClick={() => {accessMutation.reset(); setAction({kind: 'ACCESS', user: selected})}}>
              {selected.adminAccess ? 'Revoke admin access' : 'Grant admin access'}
            </button>
          </div>
        </> : <EmptyState title="Select an account" description="Account details and actions will appear here."/>}
      </aside>
    </div>
    <AdminActionDialog open={Boolean(action)}
      title={action?.kind === 'STATUS' ? `${action.user.status === 'ACTIVE' ? 'Disable' : 'Enable'} ${action.user.fullName}?`
        : `${action?.user.adminAccess ? 'Revoke' : 'Grant'} admin access?`}
      description="This change takes effect immediately and will be recorded with your identity."
      confirmLabel={action?.kind === 'STATUS' ? (action.user.status === 'ACTIVE' ? 'Disable account' : 'Enable account')
        : (action?.user.adminAccess ? 'Revoke access' : 'Grant access')}
      danger={action?.kind === 'STATUS' ? action.user.status === 'ACTIVE' : Boolean(action?.user.adminAccess)}
      submitting={mutation.isPending} error={mutationError} onCancel={() => {mutation.reset(); setAction(null)}} onConfirm={confirm}/>
  </section>;
}

function Metric({label, value, note}: {label: string; value: number | string; note: string}) {
  return <article className="admin-metric"><span>{label}</span><strong>{value}</strong><small>{note}</small></article>;
}
function Detail({label, value}: {label: string; value: string}) { return <div><small>{label}</small><b>{value}</b></div> }
function titleCase(value: string) { return value.toLowerCase().replaceAll('_', ' ').replace(/^./, char => char.toUpperCase()) }
function formatDate(value: string) { return new Intl.DateTimeFormat('en-GB', {dateStyle: 'medium'}).format(new Date(value)) }
function Pagination({page, total, pageSize, hasNext, onPage}: {page: number; total: number; pageSize: number; hasNext: boolean; onPage: (page: number) => void}) {
  return <div className="admin-pagination"><span>{total} total</span><button disabled={page === 1} onClick={() => onPage(page - 1)}>Previous</button>
    <b>{page}</b><button disabled={!hasNext} onClick={() => onPage(page + 1)}>Next</button><small>{pageSize} per page</small></div>;
}
