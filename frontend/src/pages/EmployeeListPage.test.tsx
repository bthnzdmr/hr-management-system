import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { EmployeeListPage } from './EmployeeListPage';
import employeesReducer from '../store/employeesSlice';
import { AuthProvider } from '../auth/AuthContext';
import { SnackbarProvider } from '../components/SnackbarProvider';
import { tokenStorage } from '../api/client';
import { employeeApi } from '../api/employees';
import type { Employee, Role } from '../types/api';

vi.mock('../api/employees', () => ({
  employeeApi: {
    list: vi.fn(),
    changeStatus: vi.fn(),
    getDirectReports: vi.fn(),
  },
}));

function makeEmployee(overrides: Partial<Employee> = {}): Employee {
  return {
    id: 1,
    version: 0,
    firstName: 'Grace',
    lastName: 'Hopper',
    email: 'grace@example.com',
    phone: null,
    departmentId: 1,
    departmentName: 'Software Development',
    managerId: null,
    managerFullName: null,
    jobTitle: 'Compiler Engineer',
    hireDate: '2024-03-01',
    active: true,
    terminatedAt: null,
    terminationReason: null,
    ...overrides,
  };
}

function fakeToken(roles: Role[]): string {
  const body = {
    sub: `${roles[0].toLowerCase()}@example.com`,
    roles,
    exp: Math.floor(Date.now() / 1000) + 900,
  };
  return `header.${btoa(JSON.stringify(body))}.signature`;
}

function renderPage(roles: Role[]) {
  tokenStorage.set(fakeToken(roles));

  const store = configureStore({ reducer: { employees: employeesReducer } });

  return render(
    <Provider store={store}>
      <SnackbarProvider>
        <MemoryRouter>
          <AuthProvider>
            <EmployeeListPage />
          </AuthProvider>
        </MemoryRouter>
      </SnackbarProvider>
    </Provider>,
  );
}

function pageOf(employees: Employee[]) {
  return {
    content: employees,
    totalElements: employees.length,
    totalPages: 1,
    number: 0,
    size: 10,
  };
}

describe('EmployeeListPage', () => {
  beforeEach(() => {
    tokenStorage.clear();
    localStorage.removeItem('hr.tableDensity');
    // Cagri sayaci sifirlanmazsa "hic cagrilmadi" iddiasi bir onceki testin
    // cagrisina takilir; testler birbirinden bagimsiz olmalidir.
    vi.clearAllMocks();
    vi.mocked(employeeApi.list).mockResolvedValue(pageOf([makeEmployee()]));
    vi.mocked(employeeApi.getDirectReports).mockResolvedValue([]);
    vi.mocked(employeeApi.changeStatus).mockResolvedValue(makeEmployee({ active: false }));
  });

  it('lists the employees returned by the server', async () => {
    renderPage(['HR_SPECIALIST']);

    expect(await screen.findByText('Grace Hopper')).toBeInTheDocument();
    expect(screen.getByText('Software Development')).toBeInTheDocument();
  });

  it('shows who the manager is', async () => {
    // Kullanicinin bildirdigi eksik: listede kimin kime bagli oldugu
    // gorunmuyordu.
    vi.mocked(employeeApi.list).mockResolvedValue(
      pageOf([makeEmployee({ managerId: 9, managerFullName: 'Barbara Liskov' })]),
    );

    renderPage(['EMPLOYEE']);

    expect(await screen.findByText('Barbara Liskov')).toBeInTheDocument();
  });

  it('says so when an employee has no manager', async () => {
    renderPage(['EMPLOYEE']);

    expect(await screen.findByText('No manager')).toBeInTheDocument();
  });

  it('offers the write actions to an administrator', async () => {
    renderPage(['HR_SPECIALIST']);

    expect(await screen.findByRole('button', { name: /new employee/i })).toBeInTheDocument();
    expect(screen.getByLabelText('Edit')).toBeInTheDocument();
  });

  it('hides the write actions from a plain user', async () => {
    // Bu bir guvenlik onlemi degil: sunucu zaten 403 doner. Amac kullaniciya
    // kacinilmaz olarak reddedilecek bir dugmeyi hic gostermemek.
    renderPage(['EMPLOYEE']);

    await screen.findByText('Grace Hopper');
    expect(screen.queryByRole('button', { name: /new employee/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Edit')).not.toBeInTheDocument();
  });

  it('leaves the read-only user a way into the record', async () => {
    renderPage(['EMPLOYEE']);

    expect(await screen.findByLabelText('View details')).toBeInTheDocument();
  });

  it('sends the search term to the server after the user stops typing', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage(['HR_SPECIALIST']);
    await screen.findByText('Grace Hopper');

    await user.type(screen.getByPlaceholderText('Search by name or email'), 'liskov');

    await waitFor(() =>
      expect(employeeApi.list).toHaveBeenCalledWith(
        expect.objectContaining({ search: 'liskov', page: 0 }),
      ));
  });

  it('asks the server for inactive records only when that filter is chosen', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage(['HR_SPECIALIST']);
    await screen.findByText('Grace Hopper');

    await user.click(screen.getByRole('button', { name: 'Inactive' }));

    await waitFor(() =>
      expect(employeeApi.list).toHaveBeenCalledWith(expect.objectContaining({ active: false })));
  });

  it('does not filter by status at all when "All" is selected', async () => {
    renderPage(['HR_SPECIALIST']);

    await waitFor(() => expect(employeeApi.list).toHaveBeenCalled());
    // active: false gonderilseydi yalnizca pasifler gelirdi; ayrim
    // yapmamak parametreyi HIC gondermemek demektir.
    expect(vi.mocked(employeeApi.list).mock.calls[0][0].active).toBeUndefined();
  });

  it('flips the sort direction when the same column header is clicked twice', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage(['HR_SPECIALIST']);
    await screen.findByText('Grace Hopper');

    // Tam ad eslesmesi: /employee/i olsaydi "New employee" dugmesine de uyardi.
    await user.click(screen.getByRole('button', { name: 'Employee' }));

    await waitFor(() =>
      expect(employeeApi.list).toHaveBeenCalledWith(
        expect.objectContaining({ sort: 'lastName,desc' }),
      ));
  });

  it('confirms before deactivating and warns about the direct reports', async () => {
    const user = userEvent.setup({ delay: null });
    vi.mocked(employeeApi.getDirectReports).mockResolvedValue([
      makeEmployee({ id: 2 }),
      makeEmployee({ id: 3 }),
    ]);

    renderPage(['HR_SPECIALIST']);
    await screen.findByText('Grace Hopper');

    // Rol ile sorulur: Tooltip sarmalayici span'e de bir etiket koyuyor ve
    // getByLabelText iki eleman birden buluyor.
    await user.click(screen.getByRole('button', { name: 'Deactivate' }));

    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText(/2 people report to this employee/i)).toBeInTheDocument();
    // Onaylanmadan hicbir sey gonderilmemis olmali.
    expect(employeeApi.changeStatus).not.toHaveBeenCalled();

    await user.click(within(dialog).getByRole('button', { name: 'Deactivate' }));

    // Sebep de gonderilir: sunucu sebepsiz pasiflestirmeyi reddediyor.
    await waitFor(() =>
      expect(employeeApi.changeStatus).toHaveBeenCalledWith(1, false, 'RESIGNED'));
  });

  it('sends nothing when the confirmation is cancelled', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage(['HR_SPECIALIST']);
    await screen.findByText('Grace Hopper');

    // Rol ile sorulur: Tooltip sarmalayici span'e de bir etiket koyuyor ve
    // getByLabelText iki eleman birden buluyor.
    await user.click(screen.getByRole('button', { name: 'Deactivate' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    expect(employeeApi.changeStatus).not.toHaveBeenCalled();
  });

  it('reactivates an inactive employee without asking', async () => {
    // Geri getirmek zararsizdir; onay penceresi yalnizca surtunme olurdu.
    const user = userEvent.setup({ delay: null });
    const inactive = makeEmployee({ active: false });
    vi.mocked(employeeApi.list).mockResolvedValue(pageOf([inactive]));
    vi.mocked(employeeApi.changeStatus).mockResolvedValue({ ...inactive, active: true });

    renderPage(['HR_SPECIALIST']);
    await screen.findByText('Grace Hopper');

    await user.click(screen.getByRole('button', { name: 'Reactivate' }));

    await waitFor(() =>
      expect(employeeApi.changeStatus).toHaveBeenCalledWith(1, true, undefined));
    expect(await screen.findByText('Grace Hopper reactivated')).toBeInTheDocument();
  });

  it('reports a failed status change instead of pretending it worked', async () => {
    const user = userEvent.setup({ delay: null });
    const inactive = makeEmployee({ active: false });
    vi.mocked(employeeApi.list).mockResolvedValue(pageOf([inactive]));
    vi.mocked(employeeApi.changeStatus).mockRejectedValue(new Error('boom'));

    renderPage(['HR_SPECIALIST']);
    await screen.findByText('Grace Hopper');

    await user.click(screen.getByRole('button', { name: 'Reactivate' }));

    expect(await screen.findByText('Could not update Grace Hopper')).toBeInTheDocument();
  });

  it('shows the failure message when the list cannot be loaded', async () => {
    vi.mocked(employeeApi.list).mockRejectedValue(new Error('boom'));

    renderPage(['HR_SPECIALIST']);

    await waitFor(() =>
      expect(screen.getByText('An unexpected error occurred')).toBeInTheDocument());
  });

  it('tells the user when the filters match nothing', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage(['HR_SPECIALIST']);
    await screen.findByText('Grace Hopper');

    vi.mocked(employeeApi.list).mockResolvedValue(pageOf([]));
    await user.type(screen.getByPlaceholderText('Search by name or email'), 'nobody');

    expect(await screen.findByText('No employee matches these filters')).toBeInTheDocument();
  });

  it('remembers the row density between visits', async () => {
    // Gunde yuz satir tarayan biri tercihini her acilista yeniden yapmak
    // istemez; tercih localStorage'da durur.
    const user = userEvent.setup({ delay: null });
    renderPage(['HR_SPECIALIST']);
    await screen.findByText('Grace Hopper');

    await user.click(screen.getByRole('button', { name: 'Switch to compact rows' }));

    expect(localStorage.getItem('hr.tableDensity')).toBe('compact');
    expect(screen.getByRole('button', { name: 'Switch to comfortable rows' })).toBeInTheDocument();
  });

  it('falls back to the default when the stored density is not a value we know', async () => {
    // Elle duzenlenmis veya eski surumden kalmis bir deger sessizce
    // varsayilana dusmeli; okunamayan tercih, tercihsizlik demektir.
    localStorage.setItem('hr.tableDensity', 'enormous');

    renderPage(['HR_SPECIALIST']);
    await screen.findByText('Grace Hopper');

    expect(screen.getByRole('button', { name: 'Switch to compact rows' })).toBeInTheDocument();
  });

  it('ignores a direct-reports answer that arrives for the wrong employee', async () => {
    // Olculen kusur: Alpha icin baslatilan YAVAS istek, kullanici Iptal edip
    // Beta'yi actiktan SONRA donuyor ve Beta'nin penceresine Alpha'nin ast
    // sayisini yaziyordu. Ters sirada daha kotu: 40 astli bir yonetici HIC
    // UYARI GORULMEDEN pasiflestirilebilirdi.
    const alpha = makeEmployee({ id: 1, firstName: 'Alpha', lastName: 'One' });
    const beta = makeEmployee({ id: 2, firstName: 'Beta', lastName: 'Two' });
    vi.mocked(employeeApi.list).mockResolvedValue(pageOf([alpha, beta]));

    // Alpha'nin cevabi ASILI kalir; Beta'nin cevabi hemen doner.
    let releaseAlpha: (value: Employee[]) => void = () => {};
    vi.mocked(employeeApi.getDirectReports).mockImplementation((id: number) =>
      (id === 1
        ? new Promise<Employee[]>((resolve) => { releaseAlpha = resolve; })
        : Promise.resolve([])));

    const user = userEvent.setup({ delay: null });
    renderPage(['HR_SPECIALIST']);
    await screen.findByText('Alpha One');

    // Dugmenin erisilebilir adi yalnizca "Deactivate": satiri bulup icinde ara.
    const rowOf = (name: string) =>
      screen.getByText(name).closest('tr') as HTMLElement;

    await user.click(within(rowOf('Alpha One')).getByRole('button', { name: 'Deactivate' }));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    // MUI pencere kapanirken arka plani aria-hidden birakir; kapanmasi
    // beklenmezse tablodaki dugmeler erisilebilirlik agacinda gorunmez.
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    await user.click(within(rowOf('Beta Two')).getByRole('button', { name: 'Deactivate' }));

    // Simdi Alpha'nin gecikmis cevabi gelir: uc astli.
    releaseAlpha([makeEmployee({ id: 8 }), makeEmployee({ id: 9 }), makeEmployee({ id: 10 })]);

    // Beta'nin penceresinde Alpha'nin ast uyarisi GORUNMEMELI.
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
    expect(screen.queryByText(/report to this employee/i)).not.toBeInTheDocument();
  });

});
