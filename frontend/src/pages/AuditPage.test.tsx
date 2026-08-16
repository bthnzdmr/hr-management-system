import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuditPage } from './AuditPage';
import { auditApi } from '../api/audit';
import type { AuditEntry } from '../api/audit';

vi.mock('../api/audit', () => ({
  auditApi: { list: vi.fn() },
}));

function entry(overrides: Partial<AuditEntry> = {}): AuditEntry {
  return {
    id: 1,
    actor: 'sysadmin@example.com',
    action: 'ROLES_CHANGED',
    targetType: 'USER',
    targetId: '42',
    detail: 'roles=[EMPLOYEE, MANAGER]',
    correlationId: 'abc123',
    occurredAt: '2026-08-17T09:15:30Z',
    ...overrides,
  };
}

function page(rows: AuditEntry[]) {
  return {
    content: rows, totalElements: rows.length, totalPages: 1, number: 0, size: 25,
  } as Awaited<ReturnType<typeof auditApi.list>>;
}

describe('AuditPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(auditApi.list).mockResolvedValue(page([entry()]));
  });

  it('shows who did what', async () => {
    render(<AuditPage />);

    expect(await screen.findByText('sysadmin@example.com')).toBeInTheDocument();
    expect(screen.getByText('Roles changed')).toBeInTheDocument();
    expect(screen.getByText('USER #42')).toBeInTheDocument();
  });

  it('narrows the trail by action', async () => {
    const user = userEvent.setup({ delay: null });
    render(<AuditPage />);
    await screen.findByText('sysadmin@example.com');

    await user.click(screen.getByLabelText('What'));
    await user.click(await screen.findByRole('option', { name: 'Salary changed' }));

    await waitFor(() => expect(auditApi.list).toHaveBeenCalledWith(
      expect.objectContaining({ action: 'SALARY_CHANGED' }),
    ));
  });

  it('never asks the server to sort', async () => {
    // Siralama, degeri gostermeden buyukluk iliskisi sizdirir; sunucu
    // istemcinin siralamasini zaten yok sayiyor, istemci de gondermemeli.
    render(<AuditPage />);
    await screen.findByText('sysadmin@example.com');

    const [query] = vi.mocked(auditApi.list).mock.calls[0];
    expect(query).not.toHaveProperty('sort');
  });

  it('shows the failure instead of an endless skeleton', async () => {
    vi.mocked(auditApi.list).mockRejectedValue(new Error('forbidden'));

    render(<AuditPage />);

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    // Basarisiz yukleme ile GERCEKTEN bos iz ayri hallerdir.
    expect(screen.queryByText('Nothing recorded yet')).not.toBeInTheDocument();
  });
});
