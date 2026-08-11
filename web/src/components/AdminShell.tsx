import {useEffect, useState, useSyncExternalStore} from 'react';
import {Navigate, NavLink, Outlet, useNavigate} from 'react-router-dom';
import {AuthApiError, authClient, type AuthClient} from '../api/authClient';
import {useAdminMe} from '../api/adminQueries';
import {authSession, type AuthSessionStore} from '../api/authSession';
import {ErrorState, LoadingState} from './AsyncState';

type LogoutClient = Pick<AuthClient, 'logout'>;

export function AdminShell({client = authClient, sessions = authSession}: {
  client?: LogoutClient;
  sessions?: AuthSessionStore;
}) {
  const navigate = useNavigate();
  const session = useSyncExternalStore(sessions.subscribe, sessions.getSnapshot);
  const locallyAllowed = Boolean(session?.user.permissions.includes('PLATFORM_ADMIN'));
  const admin = useAdminMe(locallyAllowed);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    if (admin.error instanceof AuthApiError && (admin.error.status === 401 || admin.error.status === 403)) {
      sessions.clear();
    }
  }, [admin.error, sessions]);

  if (!session || !locallyAllowed) return <Navigate to="/admin/sign-in" replace/>;
  if (admin.isPending) return <main className="admin-loading"><LoadingState label="Verifying admin access…"/></main>;
  if (admin.isError) {
    if (admin.error instanceof AuthApiError && (admin.error.status === 401 || admin.error.status === 403)) {
      return <Navigate to="/admin/sign-in" replace/>;
    }
    return <main className="admin-loading"><ErrorState error={admin.error} onRetry={() => admin.refetch()}/></main>;
  }

  const logout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try { await client.logout(); } catch { sessions.clear(); }
    navigate('/admin/sign-in', {replace: true});
  };

  const user = admin.data;
  return <main className="admin-shell">
    <header className="admin-topbar">
      <NavLink className="admin-brand" to="/admin/users"><span>HX</span>HireX Admin</NavLink>
      <nav aria-label="Admin navigation">
        <NavLink to="/admin/users">Accounts</NavLink>
        <NavLink to="/admin/company-reviews">Company reviews</NavLink>
        <NavLink to="/admin/moderation">Moderation</NavLink>
        <NavLink to="/admin/audit-log">Audit log</NavLink>
      </nav>
      <div className="admin-account">
        <span className="avatar">{user.fullName.slice(0, 1).toUpperCase()}</span>
        <span><b>{user.fullName}</b><small>Platform admin · {user.role.toLowerCase()}</small></span>
        <button className="text-button" onClick={logout} disabled={loggingOut}>
          {loggingOut ? 'Signing out…' : 'Sign out'}
        </button>
      </div>
    </header>
    <Outlet/>
  </main>;
}
