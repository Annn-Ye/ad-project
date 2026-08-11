import {useState, type FormEvent} from 'react';
import {AuthApiError} from '../api/authClient';
import {useModerationCases, useModerationDecision} from '../api/adminQueries';
import {AdminActionDialog} from '../components/AdminActionDialog';
import {AdminStatusBadge} from '../components/AdminStatusBadge';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';
import type {ModerationCase, ModerationSourceType, ModerationStatus} from '../models/admin';

export function AdminModerationPage() {
  const [searchInput, setSearchInput] = useState('');
  const [q, setQ] = useState('');
  const [sourceType, setSourceType] = useState<ModerationSourceType | ''>('');
  const [status, setStatus] = useState<ModerationStatus | ''>('PENDING');
  const [selectedId, setSelectedId] = useState('');
  const [decision, setDecision] = useState<'KEEP' | 'REMOVE' | null>(null);
  const [notice, setNotice] = useState('');
  const cases = useModerationCases({q, sourceType, status, page: 1, pageSize: 20});
  const decide = useModerationDecision();
  const data = cases.data?.data ?? [];
  const selected = data.find(item => item.caseId === selectedId) ?? data[0] ?? null;
  const error = decide.error instanceof AuthApiError
    ? `${decide.error.message}${decide.error.requestId ? ` Request ID: ${decide.error.requestId}` : ''}` : undefined;
  const search = (event: FormEvent) => {event.preventDefault(); setQ(searchInput.trim())};
  const confirm = (reason: string) => {
    if (!selected || !decision) return;
    decide.mutate({moderationCase: selected, decision, reason}, {onSuccess: () => {setDecision(null); setNotice(`Case ${selected.caseId.slice(0, 8)} was resolved.`)}, onError: caught => {
      if (caught instanceof AuthApiError && caught.code === 'VERSION_CONFLICT') {
        setDecision(null);
        setNotice('This case changed while the dialog was open. The latest evidence is loading; review it and confirm the decision again.');
      }
    }});
  };
  return <section className="admin-page">
    <header className="admin-page-header"><div><span className="admin-eyebrow">TRUST & SAFETY</span><h1>Content moderation</h1>
      <p>Review reported community content and record an accountable decision.</p></div></header>
    {notice && <div className="admin-page-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="Dismiss notification">×</button></div>}
    <div className="admin-metrics">
      <article className="admin-metric"><span>Matching cases</span><strong>{cases.data?.meta.total ?? '—'}</strong><small>Current filters</small></article>
      <article className="admin-metric"><span>Pending on page</span><strong>{data.filter(item => item.status === 'PENDING').length}</strong><small>Awaiting decision</small></article>
      <article className="admin-metric"><span>Reports on page</span><strong>{data.reduce((sum, item) => sum + item.reportCount, 0)}</strong><small>Combined reports</small></article>
      <article className="admin-metric"><span>Removed on page</span><strong>{data.filter(item => item.status === 'REMOVED').length}</strong><small>Resolved content</small></article>
    </div>
    <div className="admin-directory-layout">
      <div className="admin-table-card">
        <div className="admin-card-title"><div><h2>Moderation cases</h2><p>Reported content snapshots from the community pipeline.</p></div></div>
        <form className="admin-filters" onSubmit={search}><input value={searchInput} onChange={event => setSearchInput(event.target.value)} placeholder="Search content or reason"/>
          <select value={sourceType} onChange={event => setSourceType(event.target.value as ModerationSourceType | '')}><option value="">All sources</option>
            <option value="COMMUNITY_POST">Community post</option><option value="COMMUNITY_COMMENT">Community comment</option></select>
          <select value={status} onChange={event => setStatus(event.target.value as ModerationStatus | '')}><option value="">All statuses</option>
            <option value="PENDING">Pending</option><option value="KEPT">Kept</option><option value="REMOVED">Removed</option></select>
          <button className="button secondary">Search</button></form>
        {cases.isPending ? <LoadingState label="Loading moderation cases…"/> : cases.isError ? <ErrorState error={cases.error} onRetry={() => cases.refetch()}/> : data.length === 0 ?
          <EmptyState title="No moderation cases" description="Test and future community reports will appear here."/> : <div className="admin-table moderation-admin-table">
            <div className="admin-table-head"><span>Source</span><span>Report reason</span><span>Reports</span><span>Status</span></div>
            {data.map(item => <button key={item.caseId} className={`admin-table-row ${selected?.caseId === item.caseId ? 'selected' : ''}`}
              onClick={() => setSelectedId(item.caseId)}><span><b>{item.sourceType.replaceAll('_', ' ')}</b><small>{item.contentSnapshot.slice(0, 62)}</small></span>
              <span>{item.reportReason}</span><span>{item.reportCount}</span><span><AdminStatusBadge status={item.status}/></span></button>)}
          </div>}
      </div>
      <aside className="admin-detail-card">{selected ? <ModerationDetail item={selected} onDecision={value => {decide.reset(); setDecision(value)}}/> :
        <EmptyState title="Select a case" description="Evidence and decision controls will appear here."/>}</aside>
    </div>
    <AdminActionDialog open={Boolean(decision)} title={decision === 'REMOVE' ? 'Remove this content?' : 'Keep this content?'}
      description="Your reason will be stored with the content snapshot and decision."
      confirmLabel={decision === 'REMOVE' ? 'Remove content' : 'Keep content'} danger={decision === 'REMOVE'}
      submitting={decide.isPending} error={error} onCancel={() => {decide.reset(); setDecision(null)}} onConfirm={confirm}/>
  </section>;
}

function ModerationDetail({item, onDecision}: {item: ModerationCase; onDecision: (value: 'KEEP' | 'REMOVE') => void}) {
  return <><div className="admin-detail-heading"><div><span className="admin-eyebrow">CASE DECISION</span><h2>{item.sourceType.replaceAll('_', ' ')}</h2>
    <p>Case {item.caseId.slice(0, 8)} · v{item.version}</p></div></div>
    <div className="moderation-snapshot"><span>Reported content</span><blockquote>{item.contentSnapshot}</blockquote></div>
    <div className="admin-signal-list"><div><span>Primary reason</span><b>{item.reportReason}</b></div><div><span>Reporter count</span><b>{item.reportCount}</b></div>
      <div><span>Author</span><b>{item.authorName ?? 'Unknown author'}</b></div><div><span>Current state</span><AdminStatusBadge status={item.status}/></div></div>
    {item.status === 'PENDING' ? <div className="admin-detail-actions"><button className="button secondary" onClick={() => onDecision('KEEP')}>Keep content</button>
      <button className="button danger" onClick={() => onDecision('REMOVE')}>Remove content</button></div> :
      <div className="admin-notice"><b>Case resolved</b><p>This decision is immutable; the audit log contains the reason.</p></div>}
  </>;
}
