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

  it('sends the root address to the employee list', async () => {
    renderAt('/');

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
