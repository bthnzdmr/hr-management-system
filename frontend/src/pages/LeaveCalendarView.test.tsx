import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { LeaveListPage } from './LeaveListPage';
import { leaveRequestApi } from '../api/leaveRequests';
import { employeeApi } from '../api/employees';
import type { LeaveRequest } from '../api/leaveRequests';
import { SnackbarProvider } from '../components/SnackbarProvider';

vi.mock('../components/EmployeePicker', () => ({
  EmployeePicker: ({ label }: { label: string }) => <button type="button">{label}</button>,
}));

vi.mock('../api/leaveRequests', () => ({
  leaveRequestApi: { list: vi.fn(), create: vi.fn(), decide: vi.fn() },
}));

vi.mock('../api/employees', () => ({ employeeApi: { getById: vi.fn() } }));

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    canEditEmployees: true, canDecideLeave: true, canSeeLeave: true, canRequestLeave: true,
  }),
}));

function leave(overrides: Partial<LeaveRequest> = {}): LeaveRequest {
  return {
    id: 1,
    employeeId: 10,
    employeeFullName: 'Ada Lovelace',
    type: 'ANNUAL',
    status: 'APPROVED',
    startDate: '2026-08-10',
    endDate: '2026-08-14',
    days: 5,
    note: null,
    decisionNote: null,
    recordedBy: 'hr@example.com',
    decidedBy: 'hr@example.com',
    decidedAt: '2026-08-01T00:00:00Z',
    createdAt: '2026-08-01T00:00:00Z',
    ...overrides,
  };
}

function page(rows: LeaveRequest[], totalElements = rows.length) {
  return {
    content: rows, totalElements, totalPages: 1, number: 0, size: 100,
  } as Awaited<ReturnType<typeof leaveRequestApi.list>>;
}

/** Ay ACIKCA veriliyor: `currentMonth()` kullanan bir test her ay baska sonuc verirdi. */
function renderCalendar(url = '/leave?view=calendar&month=2026-08') {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <SnackbarProvider>
        <Routes>
          <Route path="/leave" element={<LeaveListPage />} />
        </Routes>
      </SnackbarProvider>
    </MemoryRouter>,
  );
}

describe('Leave calendar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(leaveRequestApi.list).mockResolvedValue(page([leave()]));
    vi.mocked(employeeApi.getById).mockResolvedValue({} as never);
  });

  it('asks the server for the month window and only for leave that is really taken', async () => {
    // Reddedilmis ve iptal edilmis kayitlar SUNUCUDA eleniyor: cizilmeyecek
    // satirlar 100'luk butceyi yerdi.
    renderCalendar();

    await waitFor(() => expect(leaveRequestApi.list).toHaveBeenCalledWith(
      expect.objectContaining({
        status: ['APPROVED', 'PENDING'],
        from: '2026-08-01',
        until: '2026-08-31',
        size: 100,
      }),
    ));
  });

  it('names each bar with the person, the range and the day count', async () => {
    // Serit GERCEK METIN tasir; renk ve konum tek basina bilgi tasiyamaz.
    renderCalendar();

    expect(await screen.findByTitle(/Ada Lovelace: Annual leave, 10-08-2026 to 14-08-2026, 5 days/))
      .toBeInTheDocument();
  });

  it('says a pending leave is still awaiting a decision', async () => {
    vi.mocked(leaveRequestApi.list).mockResolvedValue(page([leave({ status: 'PENDING' })]));

    renderCalendar();

    expect(await screen.findByTitle(/awaiting a decision/)).toBeInTheDocument();
  });

  it('moves the window when the month changes, without touching the list filters', async () => {
    const user = userEvent.setup({ delay: null });
    renderCalendar();

    await screen.findByText('August 2026');
    await user.click(screen.getByRole('button', { name: 'Next month' }));

    await waitFor(() => expect(leaveRequestApi.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ from: '2026-09-01', until: '2026-09-30' }),
    ));
    expect(screen.getByText('September 2026')).toBeInTheDocument();
  });

  it('crosses the year boundary', async () => {
    const user = userEvent.setup({ delay: null });
    renderCalendar('/leave?view=calendar&month=2026-12');

    await screen.findByText('December 2026');
    await user.click(screen.getByRole('button', { name: 'Next month' }));

    expect(await screen.findByText('January 2027')).toBeInTheDocument();
  });

  it('admits when it could not fit the whole month', async () => {
    // Sessizce kirpmak, eksik oldugunu soylemeyen bir takvim uretirdi.
    vi.mocked(leaveRequestApi.list).mockResolvedValue(page([leave()], 140));

    renderCalendar();

    expect(await screen.findByText(/Showing 1 of 140/)).toBeInTheDocument();
  });

  it('offers a way out of an empty month', async () => {
    vi.mocked(leaveRequestApi.list).mockResolvedValue(page([]));

    renderCalendar();

    expect(await screen.findByText('Nobody is off in August 2026')).toBeInTheDocument();
    // Cikis dugmesinin adi ok dugmesinden AYRI: ayni addaki iki denetim ekran
    // okuyucuda ayirt edilemezdi ve bunu bu test yakaladi.
    expect(screen.getByRole('button', { name: 'Show September 2026' })).toBeInTheDocument();
  });

  it('hides the date range filters, because the month already is the window', async () => {
    // Iki ayri tarih denetimi birbiriyle celisirdi.
    renderCalendar();

    await screen.findByText('August 2026');
    expect(screen.queryByLabelText('From')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Until')).not.toBeInTheDocument();
  });

  it('keeps the list as the escape route', async () => {
    const user = userEvent.setup({ delay: null });
    renderCalendar();

    await screen.findByText('August 2026');
    await user.click(screen.getByRole('button', { name: 'List' }));

    // Listeye donunce durum suzgeci geri gelir ve ay penceresi kalkar.
    await waitFor(() => expect(leaveRequestApi.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: ['PENDING'] }),
    ));
  });
});
