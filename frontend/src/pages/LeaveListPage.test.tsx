import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LeaveListPage } from './LeaveListPage';
import { leaveRequestApi } from '../api/leaveRequests';
import type { LeaveRequest } from '../api/leaveRequests';
import { SnackbarProvider } from '../components/SnackbarProvider';

vi.mock('../api/leaveRequests', () => ({
  leaveRequestApi: { list: vi.fn(), create: vi.fn(), decide: vi.fn() },
}));

const canEdit = vi.fn(() => true);
const canDecide = vi.fn(() => true);

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ canEditEmployees: canEdit(), canDecideLeave: canDecide(),
    canSeeLeave: true }),
}));

function leave(overrides: Partial<LeaveRequest> = {}): LeaveRequest {
  return {
    id: 1,
    employeeId: 10,
    employeeFullName: 'Ada Lovelace',
    type: 'ANNUAL',
    status: 'PENDING',
    startDate: '2031-03-10',
    endDate: '2031-03-15',
    days: 6,
    note: null,
    decisionNote: null,
    recordedBy: 'hr@example.com',
    decidedBy: null,
    decidedAt: null,
    createdAt: '2031-01-01T00:00:00Z',
    ...overrides,
  };
}

function page(rows: LeaveRequest[]) {
  return {
    content: rows,
    totalElements: rows.length,
    totalPages: 1,
    number: 0,
    size: 20,
  } as Awaited<ReturnType<typeof leaveRequestApi.list>>;
}

function renderPage() {
  return render(
    <SnackbarProvider>
      <LeaveListPage />
    </SnackbarProvider>,
  );
}

describe('LeaveListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    canEdit.mockReturnValue(true);
    canDecide.mockReturnValue(true);
    vi.mocked(leaveRequestApi.list).mockResolvedValue(page([leave()]));
  });

  it('asks only for pending requests until told otherwise', async () => {
    // Varsayilan gorunum "karar bekleyenler": sonuclanmis kayitlar arasinda
    // is bulmak, listenin isini kullaniciya yaptirmak olurdu.
    renderPage();

    await screen.findByText('Ada Lovelace');
    expect(leaveRequestApi.list).toHaveBeenCalledWith(
      expect.objectContaining({ status: ['PENDING'] }),
    );
  });

  it('counts the last day as part of the leave', async () => {
    // 10-15 Mart alti gundur. Bu hesap SUNUCUDA yapiliyor; arayuz tekrar
    // hesaplasaydi ayni +1 iki yerde yasar ve biri geride kalirdi.
    renderPage();

    const row = (await screen.findByText('Ada Lovelace')).closest('tr') as HTMLElement;
    expect(within(row).getByText('6')).toBeInTheDocument();
  });

  it('sends the decision and reloads the list', async () => {
    vi.mocked(leaveRequestApi.decide).mockResolvedValue(leave({ status: 'APPROVED' }));

    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Approve' }));

    expect(leaveRequestApi.decide).toHaveBeenCalledWith(1, 'APPROVED', undefined);
    // Liste yeniden okunur: karar sonrasi ekranda eski durum kalsaydi
    // kullanici islemin gecmedigini sanirdi.
    await waitFor(() => expect(leaveRequestApi.list).toHaveBeenCalledTimes(2));
  });

  it('locks only the row being decided, not the whole table', async () => {
    // Global bir mesgul bayragi butun satirlari kilitlerdi; projede ayni
    // kusur onay penceresinde bir kez olculdu.
    vi.mocked(leaveRequestApi.list).mockResolvedValue(page([
      leave({ id: 1 }),
      leave({ id: 2, employeeFullName: 'Grace Hopper' }),
    ]));
    vi.mocked(leaveRequestApi.decide).mockReturnValue(new Promise(() => {}));

    const user = userEvent.setup({ delay: null });
    renderPage();

    const first = (await screen.findByText('Ada Lovelace')).closest('tr') as HTMLElement;
    const second = (screen.getByText('Grace Hopper')).closest('tr') as HTMLElement;

    await user.click(within(first).getByRole('button', { name: 'Approve' }));

    expect(within(first).getByRole('button', { name: 'Approve' })).toBeDisabled();
    expect(within(second).getByRole('button', { name: 'Approve' })).toBeEnabled();
  });

  it('shows decision buttons to a manager who cannot record leave', async () => {
    // Yonetici izin GIREMEZ ama karar VEREBILIR; iki yetenek ayri.
    canEdit.mockReturnValue(false);
    canDecide.mockReturnValue(true);

    renderPage();

    await screen.findByText('Ada Lovelace');
    expect(screen.getByRole('button', { name: 'Approve' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Record leave' })).not.toBeInTheDocument();
  });

  it('offers no decision buttons to someone who can neither record nor decide', async () => {
    // Arayuzdeki kontrol GUVENLIK degil: sunucu zaten reddeder. Amac,
    // kacinilmaz olarak 403 alacak bir dugmeyi hic gostermemek.
    canEdit.mockReturnValue(false);
    canDecide.mockReturnValue(false);

    renderPage();

    await screen.findByText('Ada Lovelace');
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Record leave' })).not.toBeInTheDocument();
  });

  it('does not show decision buttons on a request that is already settled', async () => {
    vi.mocked(leaveRequestApi.list).mockResolvedValue(page([
      leave({ status: 'APPROVED', decidedBy: 'hr@example.com' }),
    ]));

    renderPage();

    await screen.findByText('Ada Lovelace');
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    expect(screen.getByText('hr@example.com')).toBeInTheDocument();
  });

  it('gives an empty pending list a way out', async () => {
    // Filtreye uyan kayit kalmadiginda cikis yolu olmali; personel listesinde
    // ayni kusur telefonda olculmustu.
    vi.mocked(leaveRequestApi.list).mockResolvedValue(page([]));

    renderPage();

    expect(await screen.findByText('Nothing to decide')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Show all requests' })).toBeInTheDocument();
  });

  it('asks for a reason before rejecting and keeps it separate from the request', async () => {
    // Ret gerekcesi ile talep gerekcesi IKI AYRI olgudur: sunucuda ayri
    // kolonlarda durur, arayuz de ikisini karistirmamali.
    vi.mocked(leaveRequestApi.decide).mockResolvedValue(leave({ status: 'REJECTED' }));

    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Reject' }));
    await user.type(screen.getByLabelText(/reason/i), 'Team is short-staffed');
    await user.click(screen.getByRole('button', { name: 'Reject' }));

    await waitFor(() => expect(leaveRequestApi.decide)
      .toHaveBeenCalledWith(1, 'REJECTED', 'Team is short-staffed'));
  });

  it('does not reject until the dialog is confirmed', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Reject' }));
    await user.click(screen.getByRole('button', { name: 'Keep it pending' }));

    expect(leaveRequestApi.decide).not.toHaveBeenCalled();
  });

  it('can withdraw a request without deciding it', async () => {
    // Sunucu CANCELLED'i kabul ediyordu ama arayuzde dugmesi yoktu: durum
    // GOSTERILEBILIYOR ama URETILEMIYORDU.
    vi.mocked(leaveRequestApi.decide).mockResolvedValue(leave({ status: 'CANCELLED' }));

    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Cancel' }));

    await waitFor(() => expect(leaveRequestApi.decide)
      .toHaveBeenCalledWith(1, 'CANCELLED', undefined));
  });

  it('shows the failure instead of an endless skeleton', async () => {
    vi.mocked(leaveRequestApi.list).mockRejectedValue(new Error('boom'));

    renderPage();

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  it('does not claim the queue is clear when the request failed', async () => {
    // Olculdu: 403 alan bir kullanici ayni ekranda hem hatayi hem "karar
    // bekleyen yok" yazisini goruyordu. Ikincisi YANLIS bir guvence:
    // yuklenememek ile bos olmak ayri hallerdir.
    vi.mocked(leaveRequestApi.list).mockRejectedValue(new Error('forbidden'));

    renderPage();

    await screen.findByRole('alert');
    expect(screen.queryByText('Nothing to decide')).not.toBeInTheDocument();
  });
});
