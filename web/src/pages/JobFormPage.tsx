import {useEffect, useRef, useState, type FormEvent} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {AuthApiError} from '../api/authClient';
import {authSession} from '../api/authSession';
import {useCreateJob, useJob, useUpdateJob} from '../api/queries';
import {ErrorState, LoadingState} from '../components/AsyncState';
import type {EmploymentType, JobDraft, RecruiterJobSummary, Visibility, WorkplaceType} from '../models/recruiter';

const blank: JobDraft = {title: '', employmentType: 'FULL_TIME', workplaceType: 'HYBRID', location: 'Singapore', salaryMin: 5000, salaryMax: 8000, description: '', requirements: '', skills: ['Java', 'Spring Boot', 'MySQL'], deadline: '', visibility: 'PUBLIC'};

export function JobFormPage() {
  const {jobId} = useParams();
  const isEdit = Boolean(jobId);
  const nav = useNavigate();
  const create = useCreateJob();
  const update = useUpdateJob();
  const jobQuery = useJob(jobId ?? '');
  const submittingRef = useRef(false);
  const initializedJobId = useRef('');
  const [form, setForm] = useState<JobDraft>(() => ({...blank, skills: [...blank.skills]}));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [pageError, setPageError] = useState('');
  const [reloadRequired, setReloadRequired] = useState(false);
  const [skill, setSkill] = useState('');
  const currentJob = jobQuery.data;

  useEffect(() => {
    if (isEdit && currentJob && initializedJobId.current !== currentJob.jobId) {
      setForm(toDraft(currentJob));
      initializedJobId.current = currentJob.jobId;
    }
  }, [currentJob, isEdit]);

  if (isEdit && jobQuery.isLoading) return <LoadingState label="Loading the real job draft…"/>;
  if (isEdit && (jobQuery.isError || !currentJob)) {
    if (jobQuery.error instanceof AuthApiError && jobQuery.error.status === 404) {
      return <div className="state-card error"><strong>Job not found</strong><span>This job does not exist or is not part of your company.</span><button className="button secondary" onClick={() => nav('/recruiter/jobs')}>Back to jobs</button></div>;
    }
    return <ErrorState onRetry={() => jobQuery.refetch()}/>;
  }
  if (isEdit && currentJob?.status !== 'DRAFT') {
    return <div className="state-card error"><strong>Job cannot be edited</strong><span>Only DRAFT jobs can be edited. Reload the detail page to see its latest status.</span><button className="button secondary" onClick={() => nav(`/recruiter/jobs/${jobId}`)}>Back to job</button></div>;
  }

  const mutationPending = create.isPending || update.isPending;
  const companyName = currentJob?.company.name ?? authSession.getSnapshot()?.user.company?.name ?? 'Your company';
  const set = <K extends keyof JobDraft>(key: K, value: JobDraft[K]) => setForm(current => ({...current, [key]: value}));

  const validate = () => {
    const next: Record<string, string> = {};
    if (!form.title.trim()) next.title = 'Job title is required.';
    if (!form.location.trim()) next.location = 'Location is required.';
    if (form.salaryMin < 0 || form.salaryMax < form.salaryMin) next.salary = 'Enter a valid salary range.';
    if (!form.description.trim()) next.description = 'Job description is required.';
    if (!form.requirements.split('\n').some(value => value.trim())) next.requirements = 'Add at least one requirement.';
    if (!form.skills.length) next.skills = 'Add at least one skill.';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (submittingRef.current || !validate()) return;
    submittingRef.current = true;
    setPageError('');
    setReloadRequired(false);
    try {
      const job = isEdit && currentJob
        ? await update.mutateAsync({jobId: currentJob.jobId, input: form, expectedVersion: currentJob.version})
        : await create.mutateAsync(form);
      nav(`/recruiter/jobs/${job.jobId}`);
    } catch (caught) {
      const presented = presentJobError(caught, isEdit);
      setErrors(current => ({...current, ...presented.fieldErrors}));
      setPageError(presented.pageError);
      setReloadRequired(presented.reload);
    } finally {
      submittingRef.current = false;
    }
  };

  const reload = async () => {
    setPageError('');
    setReloadRequired(false);
    initializedJobId.current = '';
    await jobQuery.refetch();
  };

  return <form className="job-form-page" onSubmit={submit} noValidate aria-busy={mutationPending}>
    <section className="page-header job-header"><div><button type="button" className="text-button" onClick={() => nav(isEdit ? `/recruiter/jobs/${jobId}` : '/recruiter/jobs')}>‹ Jobs</button><h1>{isEdit ? 'Edit job draft' : 'Create job draft'}</h1><p>{isEdit ? 'Update this persisted DRAFT using optimistic version control.' : 'Create a server-backed draft before publishing.'}</p></div><span className="autosave">DRAFT only</span></section>
    {pageError && <div className="state-card error" role="alert"><span>{pageError}</span>{reloadRequired && <button type="button" className="button secondary" onClick={reload}>Reload draft</button>}</div>}
    <div className="job-layout"><div className="job-main"><section className="panel form-section"><h2>Basic information</h2>
      <label>Job title *<input value={form.title} onChange={event => set('title', event.target.value)}/>{errors.title && <em>{errors.title}</em>}</label>
      <div className="form-grid"><label>Employment type *<select value={form.employmentType} onChange={event => {const value = employmentType(event.target.value); if (value) set('employmentType', value);}}><option value="FULL_TIME">Full-time</option><option value="INTERNSHIP">Internship</option><option value="PART_TIME">Part-time</option></select></label>
      <label>Work mode *<select value={form.workplaceType} onChange={event => {const value = workplaceType(event.target.value); if (value) set('workplaceType', value);}}><option value="HYBRID">Hybrid</option><option value="ONSITE">On-site</option><option value="REMOTE">Remote</option></select></label>
      <label>Location *<input value={form.location} onChange={event => set('location', event.target.value)}/>{errors.location && <em>{errors.location}</em>}</label>
      <label>Salary range (SGD/month) *<span className="salary-inputs"><input aria-label="Minimum salary" type="number" min="0" value={form.salaryMin} onChange={event => set('salaryMin', Number(event.target.value))}/><b>–</b><input aria-label="Maximum salary" type="number" min="0" value={form.salaryMax} onChange={event => set('salaryMax', Number(event.target.value))}/></span>{(errors.salary || errors['salary.min'] || errors['salary.max']) && <em>{errors.salary || errors['salary.min'] || errors['salary.max']}</em>}</label></div>
    </section><section className="panel form-section"><h2>Role details</h2>
      <label>Job description *<textarea rows={4} value={form.description} onChange={event => set('description', event.target.value)}/>{errors.description && <em>{errors.description}</em>}</label>
      <label>Requirements *<textarea rows={3} value={form.requirements} onChange={event => set('requirements', event.target.value)} placeholder="One requirement per line"/>{errors.requirements && <em>{errors.requirements}</em>}</label>
      <label>Required skills *<div className="skill-input">{form.skills.map(value => <button type="button" className="skill" key={value} onClick={() => set('skills', form.skills.filter(item => item !== value))}>{value} ×</button>)}<input aria-label="Add a skill" value={skill} onChange={event => setSkill(event.target.value)} onKeyDown={event => {if (event.key === 'Enter') {event.preventDefault(); const value = skill.trim(); if (value && !form.skills.includes(value)) set('skills', [...form.skills, value]); setSkill('');}}} placeholder="Add a skill and press Enter"/></div>{errors.skills && <em>{errors.skills}</em>}</label>
    </section></div><aside className="job-aside"><section className="panel form-section"><h2>Draft settings</h2><label>Company<input value={companyName} disabled/></label><label>Application deadline<input type="date" value={form.deadline} onChange={event => set('deadline', event.target.value)}/>{errors.deadline && <em>{errors.deadline}</em>}</label><label>Visibility<select value={form.visibility} onChange={event => {const value = visibility(event.target.value); if (value) set('visibility', value);}}><option value="PUBLIC">Public</option><option value="PRIVATE">Private</option></select></label></section><section className="panel preview"><h2>Candidate preview</h2><h3>{form.title || 'Untitled role'}</h3><p>{companyName} · {form.location} · {form.workplaceType}</p><span className="match-badge">SGD {form.salaryMin}–{form.salaryMax} / month</span></section></aside></div>
    <footer className="sticky-actions"><span>{isEdit ? `Editing server version ${currentJob?.version}` : 'The server will create version 1 as DRAFT.'}</span><div className="actions"><button type="button" className="button secondary" onClick={() => nav(isEdit ? `/recruiter/jobs/${jobId}` : '/recruiter/jobs')}>Cancel</button><button type="submit" className="button primary" disabled={mutationPending}>{mutationPending ? 'Saving draft…' : isEdit ? 'Save changes' : 'Save draft'}</button></div></footer>
  </form>;
}

function toDraft(job: RecruiterJobSummary): JobDraft {
  return {
    title: job.title,
    employmentType: job.employmentType,
    workplaceType: job.workplaceType,
    location: job.location,
    salaryMin: job.salary.min,
    salaryMax: job.salary.max,
    description: job.description,
    requirements: job.requirements.join('\n'),
    skills: [...job.skills],
    deadline: job.deadline?.slice(0, 10) ?? '',
    visibility: job.visibility,
  };
}

function presentJobError(caught: unknown, isEdit: boolean): {fieldErrors: Record<string, string>; pageError: string; reload: boolean} {
  const fallback = isEdit ? 'Unable to update this draft. Please try again.' : 'Unable to save this draft. Please try again.';
  if (!(caught instanceof AuthApiError)) return {fieldErrors: {}, pageError: fallback, reload: false};
  if (!isEdit && caught.status === 403) return {fieldErrors: {}, pageError: 'Your company must be approved before you can create a job draft.', reload: false};
  if (isEdit && caught.status === 403) return {fieldErrors: {}, pageError: 'You do not have permission to edit this job.', reload: false};
  if (isEdit && caught.status === 404) return {fieldErrors: {}, pageError: 'This job no longer exists or is not part of your company.', reload: false};
  if (isEdit && caught.code === 'VERSION_CONFLICT') return {fieldErrors: {}, pageError: 'This draft changed after you opened it. Reload before saving again.', reload: true};
  if (isEdit && caught.code === 'INVALID_JOB_TRANSITION') return {fieldErrors: {}, pageError: 'Only DRAFT jobs can be edited. Reload its latest status.', reload: true};
  if (caught.code === 'VALIDATION_ERROR' || caught.code === 'INVALID_REQUEST') {
    return {fieldErrors: caught.fieldErrors, pageError: 'Please check the highlighted job fields.', reload: false};
  }
  if (caught.code === 'NETWORK_ERROR') return {fieldErrors: {}, pageError: 'Unable to reach the server. Check your connection and try again.', reload: false};
  return {fieldErrors: {}, pageError: fallback, reload: false};
}

const employmentType = (value: string): EmploymentType | undefined => value === 'FULL_TIME' || value === 'INTERNSHIP' || value === 'PART_TIME' ? value : undefined;
const workplaceType = (value: string): WorkplaceType | undefined => value === 'ONSITE' || value === 'HYBRID' || value === 'REMOTE' ? value : undefined;
const visibility = (value: string): Visibility | undefined => value === 'PUBLIC' || value === 'PRIVATE' ? value : undefined;
