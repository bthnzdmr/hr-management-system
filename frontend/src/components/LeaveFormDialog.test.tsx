import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LeaveFormDialog } from './LeaveFormDialog';
import { leaveBalanceApi } from '../api/leaveBalances';
import { employeeApi } from '../api/employees';
import type { LeaveBalance } from '../api/leaveBalances';

vi.mock('../api/leaveBalances', () => ({
  leaveBalanceApi: { get: vi.fn() },
}));

vi.mock('../api/leaveRequests', () => ({
  leaveRequestApi: { create: vi.fn() },
}));

vi.mock('../api/employees', () => ({
  employeeApi: { list: vi.fn() },
}));

function balance(overrides: Partial<LeaveBalance> = {}): LeaveBalance {
  return {
    employeeId: 7,
    year: 2026,
    entitledDays: 20,
    carriedOverDays: 0,
    usedDays: 0,
    reservedDays: 0,
    availableDays: 20,
    source: 'GRANTED',
    ...overrides,
  };
}

function withEmployees() {
  vi.mocked(employeeApi.list).mockResolvedValue({
    content: [{
      id: 7, version: 0, firstName: 'Ada', lastName: 'Lovelace',
      email: 'ada@example.com', phone: null, departmentId: 1,
      departmentName: 'Software Development', managerId: null, managerFullName: null,
      jobTitle: 'Engineer', hireDate: '2020-01-01', active: true,
      terminatedAt: null, terminationReason: null,
    }],
    totalElements: 1, totalPages: 1, number: 0, size: 10,
  } as Awaited<ReturnType<typeof employeeApi.list>>);
}

/** Secim kutusunu acip tek adayi secer. */
async function pickAda(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByLabelText('Employee'));
  await user.click(await screen.findByText(/Ada Lovelace/));
}

describe('LeaveFormDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    withEmployees();
  });

  it('does not ask for a balance until somebody is picked', async () => {
    // Kimse secilmeden sorulsaydi sunucuya anlamsiz bir istek giderdi.
    render(<LeaveFormDialog open onClose={() => {}} onSaved={() => {}} />);

    await waitFor(() => expect(employeeApi.list).toHaveBeenCalled());
    expect(leaveBalanceApi.get).not.toHaveBeenCalled();
  });

  it('shows the remaining days once a person is picked', async () => {
    const user = userEvent.setup({ delay: null });
    vi.mocked(leaveBalanceApi.get).mockResolvedValue(balance({ availableDays: 12 }));

    render(<LeaveFormDialog open onClose={() => {}} onSaved={() => {}} />);
    await pickAda(user);

    expect(await screen.findByText(/annual leave day/)).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
  });

  it('says a default is a default, not a recorded entitlement', async () => {
    // "Verilmis hak" ile "varsayilan" ayni gorunmemeli: ikincisi bir KARAR
    // degil, bir tahmindir.
    const user = userEvent.setup({ delay: null });
    vi.mocked(leaveBalanceApi.get).mockResolvedValue(balance({ source: 'DEFAULT' }));

    render(<LeaveFormDialog open onClose={() => {}} onSaved={() => {}} />);
    await pickAda(user);

    expect(await screen.findByText(/showing the default/)).toBeInTheDocument();
  });

  it('keeps working when the balance cannot be read', async () => {
    // Bakiye bir KOLAYLIK; alinamamasi formu kullanilamaz yapmamali. Son sozu
    // zaten sunucu soyluyor ve hakki asan talebi orada reddediyor.
    const user = userEvent.setup({ delay: null });
    vi.mocked(leaveBalanceApi.get).mockRejectedValue(new Error('boom'));

    render(<LeaveFormDialog open onClose={() => {}} onSaved={() => {}} />);
    await pickAda(user);

    await waitFor(() => expect(leaveBalanceApi.get).toHaveBeenCalled());
    expect(screen.queryByText(/annual leave day/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /save|record/i })).toBeInTheDocument();
  });
});
