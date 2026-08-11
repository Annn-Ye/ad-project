import {useState, type FormEvent} from 'react';
import {useAuditEvents} from '../api/adminQueries';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';

export function AdminAuditPage() {
  const [actorInput, setActorInput] = useState('');
  const [actorId, setActorId] = useState('');
  const [targetType, setTargetType] = useState('');
  const [page, setPage] = useState(1);
  const events = useAuditEvents({actorId, targetType, page, pageSize: 20});
  const submit = (event: FormEvent) => {event.preventDefault(); setActorId(actorInput.trim()); setPage(1)};
  return <section className="admin-page">
    <header className="admin-page-header"><div><span className="admin-eyebrow">ACCOUNTABILITY</span><h1>Audit log</h1>
      <p>Trace every high-impact platform action to an actor, reason, request and timestamp.</p></div></header>
    <div className="admin-table-card audit-card">
      <div className="admin-card-title"><div><h2>Admin activity</h2><p>Newest events appear first. Times are shown in your browser timezone.</p></div></div>
      <form className="admin-filters" onSubmit={submit}><input value={actorInput} onChange={event => setActorInput(event.target.value)} placeholder="Filter by actor ID"/>
        <select value={targetType} onChange={event => {setTargetType(event.target.value); setPage(1)}}><option value="">All targets</option><option value="USER">User</option>
          <option value="COMPANY">Company</option><option value="MODERATION_CASE">Moderation case</option></select><button className="button secondary">Apply filters</button></form>
      {events.isPending ? <LoadingState label="Loading audit events…"/> : events.isError ? <ErrorState error={events.error} onRetry={() => events.refetch()}/> : !events.data?.data.length ?
        <EmptyState title="No audit events" description="Admin changes will appear here after they are performed."/> : <>
          <div className="admin-table audit-admin-table"><div className="admin-table-head"><span>Time / actor</span><span>Action</span><span>Target</span><span>Reason</span><span>Request ID</span></div>
            {events.data.data.map(item => <div className="admin-table-row" key={item.auditEventId}><span><b>{new Date(item.occurredAt).toLocaleString()}</b><small>{item.actorName}</small></span>
              <span className="audit-action">{item.action.replaceAll('_', ' ')}</span><span>{item.targetType}<small>{item.targetId.slice(0, 12)}</small></span>
              <span>{item.reason}</span><code>{item.requestId}</code></div>)}</div>
          <div className="admin-pagination"><span>{events.data.meta.total} total</span><button disabled={page === 1} onClick={() => setPage(page - 1)}>Previous</button>
            <b>{page}</b><button disabled={!events.data.meta.hasNext} onClick={() => setPage(page + 1)}>Next</button></div>
        </>}
    </div>
  </section>;
}
