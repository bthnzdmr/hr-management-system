import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { EmployeeDetailPage } from './EmployeeDetailPage';
import { SnackbarProvider } from '../components/SnackbarProvider';
import { employeeApi } from '../api/employees';
import type { Employee } from '../types/api';

vi.mock('../api/employees', () => ({
  employeeApi: {
    getById: vi.fn(),
    getDirectReports: vi.fn(),
    getSalary: vi.fn(),
    updateSalary: vi.fn(),
  },
}));

const canSeeSalaries = vi.fn(() => false);

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    canEditEmployees: false,
    canManageAccounts: false,
    canSeeSalaries: canSeeSalaries(),
  }),
}));

function makeEmployee(overrides: Partial<Employee> = {}): Employee {
  return {
    id: 5,
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
    canSeeSalaries.mockReturnValue(false);
    vi.mocked(employeeApi.getById).mockResolvedValue(makeEmployee());
    vi.mocked(employeeApi.getDirectReports).mockResolvedValue([]);
    vi.mocked(employeeApi.getSalary).mockResolvedValue({ employeeId: 5, salary: 95000 });
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
    vi.mocked(employeeApi.updateSalary).mockResolvedValue({ employeeId: 5, salary: 99000 });

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
    vi.mocked(employeeApi.getSalary).mockResolvedValue({ employeeId: 5, salary: null });

    renderPage();

    expect(await screen.findByText('Not set')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Set salary' })).toBeInTheDocument();
  });
});
