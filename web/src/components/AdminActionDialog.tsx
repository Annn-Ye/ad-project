import {useEffect, useState, type FormEvent} from 'react';

export function AdminActionDialog({open, title, description, confirmLabel, danger = false,
  submitting, error, onCancel, onConfirm}: {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  danger?: boolean;
  submitting: boolean;
  error?: string;
  onCancel: () => void;
  onConfirm: (reason: string) => void;
}) {
  const [reason, setReason] = useState('');
  const [localError, setLocalError] = useState('');

  useEffect(() => {
    if (open) {
      setReason('');
      setLocalError('');
    }
  }, [open]);

  if (!open) return null;
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!reason.trim()) {
      setLocalError('A reason is required for the audit log.');
      return;
    }
    onConfirm(reason.trim());
  };
  return <div className="modal-backdrop" role="presentation">
    <form className="modal admin-modal" role="dialog" aria-modal="true" aria-labelledby="admin-dialog-title"
      onSubmit={submit}>
      <div className="admin-modal-icon" aria-hidden="true">!</div>
      <div>
        <h2 id="admin-dialog-title">{title}</h2>
        <p>{description}</p>
      </div>
      <label>REASON
        <textarea value={reason} maxLength={500} disabled={submitting}
          onChange={event => {setReason(event.target.value); setLocalError('')}}
          placeholder="Explain why this action is necessary" autoFocus/>
      </label>
      {(localError || error) && <p className="form-error" role="alert">{localError || error}</p>}
      <div className="actions">
        <button type="button" className="button secondary" disabled={submitting} onClick={onCancel}>Cancel</button>
        <button type="submit" className={`button ${danger ? 'danger-fill' : 'primary'}`} disabled={submitting}>
          {submitting ? 'Saving…' : confirmLabel}
        </button>
      </div>
    </form>
  </div>;
}
