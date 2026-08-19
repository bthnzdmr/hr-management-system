import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { MyTeamPage } from './MyTeamPage';
import { employeeApi } from '../api/employees';
import { leaveRequestApi } from '../api/leaveRequests';
import type { LeaveRequest } from '../api/leaveRequests';
import type { Employee } from '../types/api';
import { SnackbarProvider } from '../components/SnackbarProvider';

vi.mock('../api/employees', () => ({
  employeeApi: { getMe: vi.fn(), list: vi.fn() },
}));

vi.mock('../api/leaveRequests', () => ({
  leaveRequestApi: { list: vi.fn(), decide: vi.fn() },
}));

const canDecide = vi.fn(() => true);

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ canDecideLeave: canDecide() }),
}));

function employee(id: number, firstName: string, lastName: string): Employee {
  return {
    id,
    firstName,
    lastName,
    email: `${firstName}.${lastName}@example.com`.toLowerCase(),
    jobTitle: 'Engineer',
    departmentId: 1,
    departmentName: 'Software Development',
    managerId: null,
    managerFullName: null,
    hireDate: '2024-01-01',
    active: true,
    version: 0,
  } as Employee;
}

function leave(overrides: Partial<LeaveRequest> = {}): LeaveRequest {
  return {
    id: 1,
    employeeId: 11,
    employeeFullName: 'Ada Lovelace',
    type: 'ANNUAL',
    status: 'PENDING',
    startDate: '2026-08-20',
    endDate: '2026-08-22',
    days: 3,
    note: null,
    decisionNote: null,
    recordedBy: 'ada@example.com',
    decidedBy: null,
    decidedAt: null,
    createdAt: '2026-08-01T00:00:00Z',
    ...overrides,
  };
}

function page<T>(content: T[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 50 } as never;
}

function renderPage() {
  return render(
    <MemoryRouter>
      <SnackbarProvider>
        <MyTeamPage />
      </SnackbarProvider>
    </MemoryRouter>,
  );
}

describe('MyTeamPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    canDecide.mockReturnValue(true);

    vi.mocked(employeeApi.getMe).mockResolvedValue(employee(2, 'Grace', 'Hopper'));
    vi.mocked(employeeApi.list).mockResolvedValue(page([
      employee(2, 'Grace', 'Hopper'),
      employee(11, 'Ada', 'Lovelace'),
      employee(12, 'Alan', 'Kay'),
    ]));
    vi.mocked(leaveRequestApi.list).mockResolvedValue(page([]));
  });

  it('leaves the manager out of their own team list', async () => {
    // Kapsam kendisini DE donduruyor (olculdu: 5 kayit = kendisi + 4 ast).
    // "Bana rapor verenler" listesinde kendini gormek sacma olurdu ve
    // sayiyi da bir fazla gosterirdi.
    renderPage();

    const team = await screen.findByText(/Reporting to you/);
    const panel = team.closest('div')?.parentElement as HTMLElement;

    expect(within(panel).getByText('Ada Lovelace')).toBeInTheDocument();
    expect(within(panel).getByText('Alan Kay')).toBeInTheDocument();
    expect(screen.getByText(/Reporting to you \(2\)/)).toBeInTheDocument();
  });

  it('names the manager in the header instead', async () => {
    renderPage();

    expect(await screen.findByText(/Grace Hopper — Engineer/)).toBeInTheDocument();
  });

  it('asks only for pending requests in the decision list', async () => {
    renderPage();

    await waitFor(() => expect(leaveRequestApi.list).toHaveBeenCalledWith(
      expect.objectContaining({ status: ['PENDING'] }),
    ));
  });

  it('shows what is waiting and approves it', async () => {
    const user = userEvent.setup({ delay: null });
    vi.mocked(leaveRequestApi.list).mockImplementation((params) =>
      Promise.resolve(page(params.status?.includes('PENDING') && params.status.length === 1
        ? [leave()] : [])));
    vi.mocked(leaveRequestApi.decide).mockResolvedValue(leave({ status: 'APPROVED' }));

    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Approve' }));

    await waitFor(() =>
      expect(leaveRequestApi.decide).toHaveBeenCalledWith(1, 'APPROVED', undefined));
  });

  it('says plainly when nothing is waiting', async () => {
    // "Bekleyen yok" bir CEVAPTIR; bolumu gizlemek, kullaniciyi bakip
    // bakmadigindan emin olamaz halde birakirdi.
    renderPage();

    expect(await screen.findByText(/Nothing is waiting/)).toBeInTheDocument();
  });

  it('offers a way to see all leave', async () => {
    renderPage();

    expect(await screen.findByRole('link', { name: 'All leave' }))
      .toHaveAttribute('href', '/leave');
  });

  it('tells a manager with no reports that nobody reports to them', async () => {
    vi.mocked(employeeApi.list).mockResolvedValue(page([employee(2, 'Grace', 'Hopper')]));

    renderPage();

    expect(await screen.findByText('Nobody reports to you yet')).toBeInTheDocument();
  });

  it('hides the decision buttons from someone who cannot decide', async () => {
    // Sunucu zaten reddederdi; amac kacinilmaz bir 403'u hic gostermemek.
    canDecide.mockReturnValue(false);
    vi.mocked(leaveRequestApi.list).mockImplementation((params) =>
      Promise.resolve(page(params.status?.includes('PENDING') && params.status.length === 1
        ? [leave()] : [])));

    renderPage();

    // Ad hem bekleyenler hem ekip listesinde geciyor; bekleyen SATIRINI
    // tarih ozetinden buluyoruz.
    await screen.findByText(/20-08-2026 → 22-08-2026/);
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument();
  });

  it('does not draw an empty calendar', async () => {
    // Bos bir takvim, bilgi tasimayan bir kutu olurdu.
    renderPage();

    await screen.findByText(/Reporting to you/);
    expect(screen.queryByText('Who is off this month')).not.toBeInTheDocument();
  });
});
