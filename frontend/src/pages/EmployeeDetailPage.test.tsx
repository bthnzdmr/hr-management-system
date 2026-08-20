import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { EmployeeDetailPage } from './EmployeeDetailPage';
import { SnackbarProvider } from '../components/SnackbarProvider';
import { employeeApi } from '../api/employees';
import { leaveBalanceApi } from '../api/leaveBalances';
import { leaveEntitlementApi } from '../api/leaveEntitlements';
import type { Employee } from '../types/api';

vi.mock('../api/employees', () => ({
  employeeApi: {
    getById: vi.fn(),
    getDirectReports: vi.fn(),
    getSalary: vi.fn(),
    updateSalary: vi.fn(),
  },
}));

vi.mock('../api/leaveBalances', () => ({
  leaveBalanceApi: { get: vi.fn() },
}));

vi.mock('../api/leaveEntitlements', () => ({
  leaveEntitlementApi: { set: vi.fn() },
}));

const canEditEmployees = vi.fn(() => false);
const canSeeSalaries = vi.fn(() => false);
const canSeeLeave = vi.fn(() => true);

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    canEditEmployees: canEditEmployees(),
    canManageAccounts: false,
    canSeeSalaries: canSeeSalaries(),
    canSeeLeave: canSeeLeave(),
  }),
}));

function makeEmployee(overrides: Partial<Employee> = {}): Employee {
  return {
    id: 5,
    version: 0,
    firstName: 'Alan',
    lastName: 'Kay',
    email: 'alan@example.com',
    phone: null,
    departmentId: 1,
    departmentName: 'Software Development',
    managerId: null,
    managerFullName: null,
    jobTitle: 'Staff Engineer',
    hireDate: '2020-01-01',
    active: true,
    terminatedAt: null,
    terminationReason: null,
    ...overrides,
  };
}

function renderPage() {
  return render(
    <SnackbarProvider>
      <MemoryRouter initialEntries={['/employees/5/details']}>
        <Routes>
          <Route path="/employees/:id/details" element={<EmployeeDetailPage />} />
        </Routes>
      </MemoryRouter>
    </SnackbarProvider>,
  );
}

describe('EmployeeDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    canEditEmployees.mockReturnValue(false);
    canSeeSalaries.mockReturnValue(false);
    canSeeLeave.mockReturnValue(true);
    vi.mocked(employeeApi.getById).mockResolvedValue(makeEmployee());
    vi.mocked(employeeApi.getDirectReports).mockResolvedValue([]);
    vi.mocked(employeeApi.getSalary).mockResolvedValue({ employeeId: 5, employeeFullName: 'Alan Kay', salary: 95000 });
    vi.mocked(leaveBalanceApi.get).mockResolvedValue({
      employeeId: 5, year: 2026, entitledDays: 14, carriedOverDays: 3,
      usedDays: 4, reservedDays: 1, availableDays: 12, source: 'GRANTED',
    });
  });

  it('links to this person leave history, already filtered', async () => {
    // Suzme altyapisi vardi ama oraya GIDECEK bir yol yoktu: ozellik yarim
    // kalmisti. Adres, filtreyi kuran seyin ta kendisi.
    renderPage();

    const link = await screen.findByRole('link', { name: 'Leave history' });
    expect(link).toHaveAttribute('href', '/leave?employee=5&status=ALL');
  });

  it('offers no leave link to a role the leave endpoint refuses', async () => {
    // Bordro uzmani ve sistem yoneticisi bu kaydi gorur ama izin ucundan 403
    // alir; kosulsuz bir baglanti onlari kesin hataya gotururdu.
    canSeeLeave.mockReturnValue(false);

    renderPage();

    await screen.findByText('Alan Kay');
    expect(screen.queryByRole('link', { name: 'Leave history' })).not.toBeInTheDocument();
  });

  it('hides compensation entirely when the server refuses it', async () => {
    // Kurali SUNUCU uygular; arayuz onu tekrar yazmaz, sorar. Cevap
    // gelmediyse bolum hic cizilmez -- "gizli" degil, YOK.
    vi.mocked(employeeApi.getSalary).mockRejectedValue(new Error('forbidden'));

    renderPage();

    await screen.findByText('Staff Engineer · Software Development');
    expect(screen.queryByText('Compensation')).not.toBeInTheDocument();
  });

  it('shows the salary to someone allowed to read it', async () => {
    renderPage();

    expect(await screen.findByText('Compensation')).toBeInTheDocument();
    expect(screen.getByText('95,000')).toBeInTheDocument();
  });

  it('offers no way to change the salary without the payroll role', async () => {
    // Okuyabilmek yazabilmek degildir: calisan kendi ucretini gorur,
    // belirleyen bordro uzmanidir.
    renderPage();

    await screen.findByText('Compensation');
    expect(screen.queryByRole('button', { name: 'Update' })).not.toBeInTheDocument();
  });

  it('writes the salary through its own endpoint', async () => {
    canSeeSalaries.mockReturnValue(true);
    vi.mocked(employeeApi.updateSalary).mockResolvedValue({ employeeId: 5, employeeFullName: 'Alan Kay', salary: 99000 });

    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Update' }));
    const field = screen.getByLabelText('Salary');
    await user.clear(field);
    await user.type(field, '99000');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(employeeApi.updateSalary).toHaveBeenCalledWith(5, { salary: 99000 }));
    // Sayfa yeniden okunmaz; donen deger dogrudan ekrana yazilir.
    expect(await screen.findByText('99,000')).toBeInTheDocument();
  });

  it('refuses to save instead of silently ignoring a cleared salary', async () => {
    // Olculen kusur: bosaltilan alan sessizce yutuluyor ve kullaniciya
    // basarili deniyordu. Sunucu maas silmeyi desteklemiyor.
    canSeeSalaries.mockReturnValue(true);

    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Update' }));
    await user.clear(screen.getByLabelText('Salary'));

    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    expect(screen.getByText('Salary cannot be removed, only changed')).toBeInTheDocument();
    expect(employeeApi.updateSalary).not.toHaveBeenCalled();
  });

  it('lets payroll set a salary that was never entered', async () => {
    canSeeSalaries.mockReturnValue(true);
    vi.mocked(employeeApi.getSalary).mockResolvedValue({ employeeId: 5, employeeFullName: 'Alan Kay', salary: null });

    renderPage();

    expect(await screen.findByText('Not set')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Set salary' })).toBeInTheDocument();
  });

  it('hides the leave entitlement entirely when the server refuses it', async () => {
    // Ucret bolumundeki kararin aynisi: kurali SUNUCU uygular, arayuz sorar.
    // Cevap gelmediyse bolum "gizli" degil, YOK.
    vi.mocked(leaveBalanceApi.get).mockRejectedValue(new Error('forbidden'));

    renderPage();

    await screen.findByText('Staff Engineer · Software Development');
    expect(screen.queryByText('Annual leave 2026')).not.toBeInTheDocument();
  });

  it('shows the entitlement, what is used and what remains', async () => {
    renderPage();

    expect(await screen.findByText('Annual leave 2026')).toBeInTheDocument();
    expect(screen.getByText('Entitled')).toBeInTheDocument();
    expect(screen.getByText('Remaining')).toBeInTheDocument();
  });

  it('offers no way to change the entitlement without the HR role', async () => {
    // Okuyabilmek yazabilmek degildir: calisan kendi bakiyesini gorur,
    // hakki belirleyen Ik'dir. Sunucu da PUT'u yalnizca Ik'ya aciyor.
    renderPage();

    await screen.findByText('Annual leave 2026');
    expect(screen.queryByRole('button', { name: 'Adjust' })).not.toBeInTheDocument();
  });

  it('says "set" rather than "adjust" while the amount is only a default', async () => {
    // Verilmis hak ile varsayilan ayni gorunmemeli: ikincisi bir KARAR degil,
    // bir tahmindir ve dugme bunu soyluyor.
    canEditEmployees.mockReturnValue(true);
    vi.mocked(leaveBalanceApi.get).mockResolvedValue({
      employeeId: 5, year: 2026, entitledDays: 20, carriedOverDays: 0,
      usedDays: 0, reservedDays: 0, availableDays: 20, source: 'DEFAULT',
    });

    renderPage();

    expect(await screen.findByRole('button', { name: 'Set entitlement' })).toBeInTheDocument();
  });

  it('saves a new entitlement and shows the recomputed remaining days', async () => {
    canEditEmployees.mockReturnValue(true);
    vi.mocked(leaveEntitlementApi.set).mockResolvedValue({
      employeeId: 5, employeeFullName: 'Alan Kay', year: 2026, entitledDays: 26, carriedOverDays: 0,
      note: 'Long service award',
    });

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Adjust' }));
    await userEvent.type(screen.getByLabelText('Reason'), 'Long service award');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => {
      expect(leaveEntitlementApi.set).toHaveBeenCalledWith(5, 2026, {
        entitledDays: 14, carriedOverDays: 3, note: 'Long service award',
      });
    });

    // 26 hak + 0 devir - 4 kullanilan - 1 bekleyen = 21. Sayilar bilerek
    // cakismiyor: ilk denemede hak ile kalan tesadufen esitti ve iddia HANGI
    // alani okudugunu soyleyemiyordu.
    expect(await screen.findByText('21')).toBeInTheDocument();
  });

  it('refuses to save without a reason', async () => {
    // Gerekce sunucuda ZORUNLU. Burada da isteniyor ki kullanici sebebini
    // sunucudan donen bir hatayla ogrenmesin.
    canEditEmployees.mockReturnValue(true);

    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Adjust' }));

    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });
});
