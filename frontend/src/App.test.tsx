import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import App from './App';
import employeesReducer from './store/employeesSlice';
import { AuthProvider } from './auth/AuthContext';
import { ColorModeProvider } from './theme/ColorModeContext';
import { SnackbarProvider } from './components/SnackbarProvider';
import { tokenStorage } from './api/client';
import { employeeApi } from './api/employees';
import type { Role } from './types/api';

vi.mock('./api/employees', () => ({
  employeeApi: { list: vi.fn(), changeStatus: vi.fn(), getDirectReports: vi.fn() },
}));

vi.mock('./api/dashboard', () => ({
  dashboardApi: {
    overview: vi.fn().mockResolvedValue({
      headcount: {
        active: 0, inactive: 0, hiredLast30Days: 0, hiredLast90Days: 0,
        leftLast12Months: 0, turnoverRate: 0,
      },
      byDepartment: [],
      turnoverByMonth: [],
      hiresByMonth: [],
      terminationReasons: [],
      spanOfControl: { managerCount: 0, averageDirectReports: 0, largestTeam: 0 },
      dataQuality: { activeWithoutManager: 0, emptyDepartments: 0 },
    }),
  },
}));

function fakeToken(roles: Role[]): string {
  const body = {
    sub: `${roles[0].toLowerCase()}@example.com`,
    roles,
    exp: Math.floor(Date.now() / 1000) + 900,
  };
  return `header.${btoa(JSON.stringify(body))}.signature`;
}

function renderAt(path: string, roles: Role[] | null = ['HR_SPECIALIST']) {
  if (roles) tokenStorage.set(fakeToken(roles));

  const store = configureStore({ reducer: { employees: employeesReducer } });

  return render(
    <Provider store={store}>
      <ColorModeProvider>
        <SnackbarProvider>
          <MemoryRouter initialEntries={[path]}>
            <AuthProvider>
              <App />
            </AuthProvider>
          </MemoryRouter>
        </SnackbarProvider>
      </ColorModeProvider>
    </Provider>,
  );
}

describe('routing', () => {
  beforeEach(() => {
    tokenStorage.clear();
    vi.clearAllMocks();
    vi.mocked(employeeApi.list).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    });
  });

  it('sends someone who can read the dashboard straight to it', async () => {
    renderAt('/', ['HR_SPECIALIST']);

    expect(await screen.findByRole('heading', { name: /people on the team/ }))
      .toBeInTheDocument();
  });

  it('sends a plain employee to the list instead', async () => {
    // Herkesi panele gondermek olmazdi: EMPLOYEE paneli goremez ve acilista
    // kacinilmaz bir yonlendirme yerdi.
    renderAt('/', ['EMPLOYEE']);

    expect(await screen.findByRole('heading', { name: 'Employees' })).toBeInTheDocument();
  });

  it('keeps the dashboard away from a plain employee', async () => {
    renderAt('/dashboard', ['EMPLOYEE']);

    expect(await screen.findByRole('heading', { name: 'Employees' })).toBeInTheDocument();
  });

  it('reports an unknown address instead of silently redirecting', async () => {
    // Onceki halde her yanlis adres listeye gidiyordu; yazim hatasi
    // fark edilmiyordu.
    renderAt('/employees/definitely-not-a-page/deeper');

    expect(await screen.findByText('Page not found')).toBeInTheDocument();
  });

  it('keeps the navigation shell on the not-found page', async () => {
    // Kullanici cikis yapabilmeli ve menude gezinebilmeli.
    renderAt('/nope');

    expect(await screen.findByText('Page not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Employees' })).toBeInTheDocument();
  });

  it('sends an anonymous visitor to the login page', async () => {
    renderAt('/employees', null);

    expect(await screen.findByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });
});
