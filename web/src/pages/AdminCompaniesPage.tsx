import {useState, type FormEvent} from 'react';
import {AuthApiError} from '../api/authClient';
import {useAdminCompanies, useReviewCompany} from '../api/adminQueries';
import {AdminActionDialog} from '../components/AdminActionDialog';
import {AdminStatusBadge} from '../components/AdminStatusBadge';
import {EmptyState, ErrorState, LoadingState} from '../components/AsyncState';
import type {CompanyReview, CompanyVerificationStatus} from '../models/admin';

type Decision = 'APPROVE' | 'REJECT' | 'REQUEST_CHANGES';

export function AdminCompaniesPage() {
  const [searchInput, setSearchInput] = useState('');
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<CompanyVerificationStatus | ''>('PENDING');
  const [page, setPage] = useState(1);
  const [selectedId, setSelectedId] = useState('');
  const [decision, setDecision] = useState<Decision | null>(null);
  const [notice, setNotice] = useState('');
  const companies = useAdminCompanies({q, status, page, pageSize: 20});
  const review = useReviewCompany();
  const data = companies.data?.data ?? [];
  const selected = data.find(company => company.companyId === selectedId) ?? data[0] ?? null;
  const error = review.error instanceof AuthApiError
    ? `${review.error.message}${review.error.requestId ? ` Request ID: ${review.error.requestId}` : ''}` : undefined;

  const search = (event: FormEvent) => { event.preventDefault(); setQ(searchInput.trim()); setPage(1) };
  const confirm = (reason: string) => {
    if (!selected || !decision) return;
    review.mutate({company: selected, decision, reason}, {onSuccess: () => {setDecision(null); setNotice(`${selected.name} review decision was saved.`)}, onError: caught => {
      if (caught instanceof AuthApiError && caught.code === 'VERSION_CONFLICT') {
        setDecision(null);
        setNotice('This company changed while the dialog was open. The latest profile is loading; review it and confirm the decision again.');
      }
    }});
  };
  return <section className="admin-page">
    <header className="admin-page-header"><div><span className="admin-eyebrow">TRUST & VERIFICATION</span><h1>Company reviews</h1>
      <p>Review company profile evidence before recruiter publishing access is unlocked.</p></div></header>
    {notice && <div className="admin-page-notice" role="status">{notice}<button onClick={() => setNotice('')} aria-label="Dismiss notification">×</button></div>}
    <div className="admin-directory-layout">
      <div className="admin-table-card">
        <div className="admin-card-title"><div><h2>Verification queue</h2><p>Newest requests are reviewed first.</p></div>
          <span className="admin-count">{companies.data?.meta.total ?? '—'} reviews</span></div>
        <form className="admin-filters" onSubmit={search}>
          <input value={searchInput} onChange={event => setSearchInput(event.target.value)} placeholder="Search company"/>
          <select value={status} onChange={event => {setStatus(event.target.value as CompanyVerificationStatus | ''); setPage(1)}}>
            <option value="">All statuses</option><option value="PENDING">Pending</option><option value="CHANGES_REQUESTED">Changes requested</option>
            <option value="APPROVED">Approved</option><option value="REJECTED">Rejected</option>
          </select><button className="button secondary">Search</button>
        </form>
        {companies.isPending ? <LoadingState label="Loading company reviews…"/> : companies.isError ?
          <ErrorState error={companies.error} onRetry={() => companies.refetch()}/> : data.length === 0 ?
            <EmptyState title="Review queue is clear" description="There are no companies matching these filters."/> : <>
              <div className="admin-table company-admin-table">
                <div className="admin-table-head"><span>Company</span><span>Location</span><span>Submitted by</span><span>Status</span></div>
                {data.map(company => <button key={company.companyId} className={`admin-table-row ${selected?.companyId === company.companyId ? 'selected' : ''}`}
                  onClick={() => setSelectedId(company.companyId)}><span className="admin-person"><i className="company-mark">{company.name.slice(0, 2).toUpperCase()}</i>
                    <span><b>{company.name}</b><small>{company.website ?? 'No website supplied'}</small></span></span>
                  <span>{company.location ?? '—'}</span><span>{company.createdByName}</span><span><AdminStatusBadge status={company.verificationStatus}/></span></button>)}
              </div>
              <div className="admin-pagination"><span>{companies.data.meta.total} total</span><button disabled={page === 1} onClick={() => setPage(page - 1)}>Previous</button>
                <b>{page}</b><button disabled={!companies.data.meta.hasNext} onClick={() => setPage(page + 1)}>Next</button></div>
            </>}
      </div>
      <aside className="admin-detail-card">
        {selected ? <CompanyDetail company={selected} onDecision={value => {review.reset(); setDecision(value)}}/> :
          <EmptyState title="Select a company" description="Company evidence and decisions will appear here."/>}
      </aside>
    </div>
    <AdminActionDialog open={Boolean(decision)} title={decisionTitle(decision, selected?.name)}
      description="Add a clear explanation. Recruiters will use this outcome to understand the verification state."
      confirmLabel={decision === 'APPROVE' ? 'Approve company' : decision === 'REJECT' ? 'Reject company' : 'Request changes'}
      danger={decision === 'REJECT'} submitting={review.isPending} error={error} onCancel={() => {review.reset(); setDecision(null)}} onConfirm={confirm}/>
  </section>;
}

function CompanyDetail({company, onDecision}: {company: CompanyReview; onDecision: (decision: Decision) => void}) {
  const reviewable = company.verificationStatus === 'PENDING' || company.verificationStatus === 'CHANGES_REQUESTED';
  return <><div className="admin-detail-heading"><span className="company-mark large">{company.name.slice(0, 2).toUpperCase()}</span>
    <div><span className="admin-eyebrow">COMPANY PROFILE</span><h2>{company.name}</h2><p>{company.location ?? 'Location not supplied'}</p></div></div>
    <div className="admin-signal-list">
      <div><span>Business website</span><b>{company.website ?? 'Not supplied'}</b></div>
      <div><span>Company stage</span><b>{company.stage?.replaceAll('_', ' ') ?? 'Not supplied'}</b></div>
      <div><span>Team size</span><b>{company.employeeRange ?? 'Not supplied'}</b></div>
      <div><span>Submitted by</span><b>{company.createdByName}</b><small>{company.createdByEmail}</small></div>
    </div>
    <div className="admin-description"><span>Company description</span><p>{company.description ?? 'No company description has been supplied.'}</p></div>
    <div className="admin-signal"><span>Verification state</span><AdminStatusBadge status={company.verificationStatus}/></div>
    {reviewable ? <div className="admin-detail-actions stacked">
      <button className="button primary" onClick={() => onDecision('APPROVE')}>Approve company</button>
      <button className="button secondary" onClick={() => onDecision('REQUEST_CHANGES')}>Request changes</button>
      <button className="button danger" onClick={() => onDecision('REJECT')}>Reject company</button>
    </div> : <div className="admin-notice"><b>Decision recorded</b><p>This company is no longer awaiting review.</p></div>}
  </>;
}
function decisionTitle(decision: Decision | null, company?: string) {
  if (!decision) return '';
  const verb = decision === 'APPROVE' ? 'Approve' : decision === 'REJECT' ? 'Reject' : 'Request changes from';
  return `${verb} ${company ?? 'company'}?`;
}
