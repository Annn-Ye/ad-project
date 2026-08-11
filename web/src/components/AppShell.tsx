import {useState, useSyncExternalStore} from 'react';
import {Navigate, NavLink, Outlet, useNavigate} from 'react-router-dom';
import {authClient, type AuthClient} from '../api/authClient';
import {authSession, type AuthSessionStore} from '../api/authSession';

type LogoutClient = Pick<AuthClient, 'logout'>;

export function AppShell({client = authClient, sessions = authSession}: {
  client?: LogoutClient;
  sessions?: AuthSessionStore;
}) {
  const nav = useNavigate();
  const session = useSyncExternalStore(sessions.subscribe, sessions.getSnapshot);
  const [loggingOut, setLoggingOut] = useState(false);

  if (!session || session.user.role !== 'RECRUITER' || !session.user.company) {
    return <Navigate to="/recruiter/sign-in" replace/>;
  }

  const logout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await client.logout();
    } catch {
      // AuthClient always clears local state; the server error is intentionally not exposed raw.
    } finally {
      sessions.clear();
      nav('/recruiter/sign-in', {replace: true});
    }
  };

  const recruiter = session.user;
  const company = recruiter.company!;
  return <main className="app-shell">
    <header className="topnav">
      <NavLink className="brand" to="/recruiter/dashboard">AD Recruiter</NavLink>
      <nav>
        <NavLink to="/recruiter/dashboard">Dashboard</NavLink>
        <NavLink to="/recruiter/jobs">Jobs</NavLink>
        <NavLink to="/recruiter/applications">Applications</NavLink>
        <NavLink to="/recruiter/messages">Messages</NavLink>
      </nav>
      <div className="account">
        <span className="avatar">{recruiter.fullName.slice(0, 1).toUpperCase()}</span>
        <span><b>{recruiter.fullName}</b><small>{company.name}</small></span>
        <button className="text-button" disabled={loggingOut} onClick={logout}>
          {loggingOut ? 'Signing out…' : 'Sign out'}
        </button>
      </div>
    </header>
    <Outlet/>
  </main>;
}
