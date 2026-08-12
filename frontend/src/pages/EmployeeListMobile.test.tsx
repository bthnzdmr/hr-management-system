import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
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
  employeeApi: { list: vi.fn(), changeStatus: vi.fn(), getDirectReports: vi.fn() },
}));

function makeEmployee(overrides: Partial<Employee> = {}): Employee {
  return {
    id: 1,
    firstName: 'Grace',
    lastName: 'Hopper',
    email: 'grace@example.com',
    phone: null,
    departmentId: 1,
    departmentName: 'Software Development',
    managerId: 9,
    managerFullName: 'Barbara Liskov',
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
    sub: 'hr@example.com',
    roles,
    exp: Math.floor(Date.now() / 1000) + 900,
  };
  return `header.${btoa(JSON.stringify(body))}.signature`;
}

function renderNarrow(roles: Role[] = ['HR_SPECIALIST']) {
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

/**
 * Dar ekran davranisi.
 *
 * Varsayilan sahte matchMedia masaustu genisligi bildirir; burada tersi
 * gerekiyor: "max-width" sorgulari eslesir, "min-width" sorgulari eslesmez.
 */
describe('EmployeeListPage on a narrow screen', () => {
  beforeEach(() => {
    tokenStorage.clear();
    vi.clearAllMocks();
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.includes('max-width'),
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }));

    vi.mocked(employeeApi.list).mockResolvedValue({
      content: [makeEmployee()], totalElements: 1, totalPages: 1, number: 0, size: 10,
    });
    vi.mocked(employeeApi.getDirectReports).mockResolvedValue([]);
    vi.mocked(employeeApi.changeStatus).mockResolvedValue(makeEmployee({ active: false }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('offers a way out of an empty filtered list on a phone too', async () => {
    // Olculen kusur: "Filtreleri temizle" dugmesi YALNIZCA tablo gorunumunde
    // vardi. Telefonda bos listeye dusen kullanicinin cikis yolu yoktu ve
    // ayni metin iki yerde ayri ayri yazildigi icin fark edilmemisti.
    vi.mocked(employeeApi.list).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    });

    const user = userEvent.setup();
    renderNarrow();

    await user.type(screen.getByPlaceholderText('Search by name or email'), 'nobody');

    const clear = await screen.findByRole('button', { name: 'Clear filters' }, { timeout: 3000 });
    expect(screen.getByText('No employee matches these filters')).toBeInTheDocument();

    await user.click(clear);

    // Filtre temizlenince "hic kayit yok" hali gosterilir; bu FARKLI bir
    // durumdur ve filtre temizleme dugmesi artik anlamsizdir.
    expect(await screen.findByText('No employees yet')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Clear filters' })).not.toBeInTheDocument();
  });

  it('shows cards instead of a table', async () => {
    renderNarrow();
    await screen.findByText('Grace Hopper');

    // Tablo DOM'a HIC girmemeli: CSS ile gizlenseydi her erisilebilir ad iki
    // kez bulunur ve ekran okuyucu ayni dugmeyi iki kere okurdu.
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.queryByRole('columnheader')).not.toBeInTheDocument();
  });

  it('keeps the same information the table row carried', async () => {
    renderNarrow();
    const name = await screen.findByText('Grace Hopper');

    // Iddia kartin ICINE sinirlanir: "Active" kelimesi filtre dugmesinde de
    // geciyor ve sayfa genelinde aramak iki eleman birden bulurdu.
    const card = name.closest('.MuiPaper-root') as HTMLElement;

    expect(within(card).getByText(/Compiler Engineer/)).toBeInTheDocument();
    expect(within(card).getByText('Reports to Barbara Liskov')).toBeInTheDocument();
    expect(within(card).getByText('Active')).toBeInTheDocument();
  });

  it('gives every action a 44 px touch target', async () => {
    // 32 px'lik masaustu ikon dugmesi parmak icin kucuktur.
    renderNarrow();
    await screen.findByText('Grace Hopper');

    const button = screen.getByRole('button', { name: 'View details' });

    expect(button).toHaveStyle({ width: '44px', height: '44px' });
  });

  it('still finds each action exactly once', async () => {
    renderNarrow();
    await screen.findByText('Grace Hopper');

    expect(screen.getAllByRole('button', { name: 'Edit' })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: 'Deactivate' })).toHaveLength(1);
  });

  it('hides the write actions from a plain employee', async () => {
    renderNarrow(['EMPLOYEE']);
    await screen.findByText('Grace Hopper');

    expect(screen.getByRole('button', { name: 'View details' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });

  it('deactivates through the same confirmation flow', async () => {
    const user = userEvent.setup();
    renderNarrow();
    await screen.findByText('Grace Hopper');

    await user.click(screen.getByRole('button', { name: 'Deactivate' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Deactivate' }));

    await waitFor(() =>
      expect(employeeApi.changeStatus).toHaveBeenCalledWith(1, false, 'RESIGNED'));
  });

  it('keeps the pagination reachable', async () => {
    // Sayfalama tablonun icinde kalsaydi dar ekranda tamamen kaybolurdu.
    renderNarrow();
    await screen.findByText('Grace Hopper');

    expect(screen.getByText('Rows per page:')).toBeInTheDocument();
  });
});
