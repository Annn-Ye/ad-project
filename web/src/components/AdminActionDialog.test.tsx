import '@testing-library/jest-dom/vitest';
import {cleanup, fireEvent, render, screen} from '@testing-library/react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {AdminActionDialog} from './AdminActionDialog';

describe('AdminActionDialog', () => {
  afterEach(cleanup);

  it('requires a trimmed audit reason and disables controls while submitting', () => {
    const confirm = vi.fn();
    const {rerender} = render(<AdminActionDialog open title="Disable account?" description="Protected action"
      confirmLabel="Disable account" submitting={false} onCancel={vi.fn()} onConfirm={confirm}/>);

    fireEvent.click(screen.getByRole('button', {name: 'Disable account'}));
    expect(screen.getByRole('alert')).toHaveTextContent('A reason is required');
    fireEvent.change(screen.getByPlaceholderText('Explain why this action is necessary'), {target: {value: '  Security review  '}});
    fireEvent.click(screen.getByRole('button', {name: 'Disable account'}));
    expect(confirm).toHaveBeenCalledWith('Security review');

    rerender(<AdminActionDialog open title="Disable account?" description="Protected action"
      confirmLabel="Disable account" submitting onCancel={vi.fn()} onConfirm={confirm}/>);
    expect(screen.getByRole('button', {name: 'Saving…'})).toBeDisabled();
    expect(screen.getByRole('button', {name: 'Cancel'})).toBeDisabled();
  });
});
