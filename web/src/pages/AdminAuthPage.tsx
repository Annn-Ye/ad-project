import {useRef, useState, type FormEvent} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import {AuthApiError, authClient, type AuthClient} from '../api/authClient';

type AdminAuthClient = Pick<AuthClient, 'signInAdmin'>;

export function AdminAuthPage({client = authClient}: {client?: AdminAuthClient}) {
  const navigate = useNavigate();
  const submittingRef = useRef(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(false);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (submittingRef.current) return;
    if (!/^\S+@\S+\.\S+$/.test(email.trim()) || password.length < 8) {
      setError('Enter a valid email and password.');
      return;
    }
    submittingRef.current = true;
    setSubmitting(true);
    setError('');
    try {
      await client.signInAdmin({email: email.trim(), password, remember});
      navigate('/admin/users', {replace: true});
    } catch (caught) {
      if (caught instanceof AuthApiError) {
        const messages: Record<string, string> = {
          UNAUTHORIZED: 'Email or password is incorrect.',
          WRONG_ROLE: 'This account does not have platform admin access.',
          NETWORK_ERROR: 'Unable to reach the server. Check that the backend is running.',
        };
        setError(`${messages[caught.code] ?? caught.message}${caught.requestId ? ` Request ID: ${caught.requestId}` : ''}`);
      } else setError('Unable to sign in. Please try again.');
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  return <main className="auth-layout admin-auth-layout">
    <aside className="auth-brand admin-auth-brand">
      <b>HIREX · PLATFORM OPERATIONS</b>
      <div>
        <span className="admin-eyebrow">TRUST & SAFETY</span>
        <h1>Control access.<br/>Protect trust.</h1>
        <p>Review companies, secure user access, and resolve reported content from one accountable workspace.</p>
        <ul><li>Account status and admin access controls</li><li>Company verification decisions</li><li>Reasoned, traceable moderation actions</li></ul>
      </div>
      <small>Every high-impact action is recorded in the audit log.</small>
    </aside>
    <section className="auth-area">
      <form className="auth-card admin-auth-card" onSubmit={submit} noValidate aria-busy={submitting}>
        <span className="portal-badge">ADMIN PORTAL</span>
        <h2>Platform sign in</h2>
        <p>Use an account that has been granted platform admin access.</p>
        <label>EMAIL
          <input type="email" autoComplete="email" value={email} placeholder="admin@company.com"
            onChange={event => {setEmail(event.target.value); setError('')}}/>
        </label>
        <label>PASSWORD
          <input type="password" autoComplete="current-password" value={password} placeholder="Your password"
            onChange={event => {setPassword(event.target.value); setError('')}}/>
        </label>
        <div className="form-options">
          <label className="check"><input type="checkbox" checked={remember}
            onChange={event => setRemember(event.target.checked)}/>Remember this browser</label>
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="button primary wide" type="submit" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in to admin'}
        </button>
        <Link className="auth-link" to="/recruiter/sign-in">Go to recruiter workspace</Link>
      </form>
    </section>
  </main>;
}
