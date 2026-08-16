import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LeaveListPage } from './LeaveListPage';
import { leaveRequestApi } from '../api/leaveRequests';
import type { LeaveRequest } from '../api/leaveRequests';
import { SnackbarProvider } from '../components/SnackbarProvider';

vi.mock('../api/leaveRequests', () => ({
  leaveRequestApi: { list: vi.fn(), create: vi.fn(), decide: vi.fn() },
}));

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ canEditEmployees: true, canDecideLeave: true, canSeeLeave: true }),
}));

/** Dar ekran taklidi: matchMedia jsdom'da yok, elle kurulur. */
function useNarrowScreen() {
  window.matchMedia = (query: string) => ({
    matches: true,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }) as MediaQueryList;
}

const leave: LeaveRequest = {
  id: 1, employeeId: 10, employeeFullName: 'Ada Lovelace', type: 'ANNUAL',
  status: 'PENDING', startDate: '2031-03-10', endDate: '2031-03-15', days: 6,
  note: null, decisionNote: null, decidedBy: null, decidedAt: null,
  createdAt: '2031-01-01T00:00:00Z',
};

describe('LeaveListPage on a narrow screen', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useNarrowScreen();
    vi.mocked(leaveRequestApi.list).mockResolvedValue({
      content: [leave], totalElements: 1, totalPages: 1, number: 0, size: 20,
    } as Awaited<ReturnType<typeof leaveRequestApi.list>>);
  });

  it('shows cards instead of a table that would scroll sideways', async () => {
    render(<SnackbarProvider><LeaveListPage /></SnackbarProvider>);

    await screen.findByText('Ada Lovelace');
    // Yedi sutunlu tablo telefonda yatay kaydirma demekti; ayni veri, farkli bicim.
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('renders only one view, so every accessible name appears once', async () => {
    render(<SnackbarProvider><LeaveListPage /></SnackbarProvider>);

    // Ikisini birden cizip birini gizlemek ekran okuyucuya ayni dugmeyi iki
    // kez okuturdu -- proje bunu bir kez olctu.
    expect(await screen.findAllByRole('button', { name: 'Approve' })).toHaveLength(1);
  });
});
