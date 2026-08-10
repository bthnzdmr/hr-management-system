import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { EmployeeListPage } from './EmployeeListPage';
import employeesReducer from '../store/employeesSlice';
import { AuthProvider } from '../auth/AuthContext';
import { tokenStorage } from '../api/client';
import { employeeApi } from '../api/employees';
import type { Employee } from '../types/api';

vi.mock('../api/employees', () => ({
  employeeApi: { list: vi.fn(), deactivate: vi.fn() },
}));

const employee: Employee = {
  id: 1,
  firstName: 'Grace',
  lastName: 'Hopper',
  email: 'grace@example.com',
  phone: null,
  departmentId: 1,
  departmentName: 'Software Development',
  managerId: null,
  jobTitle: 'Compiler Engineer',
  hireDate: '2024-03-01',
  active: true,
};

function fakeToken(role: 'ADMIN' | 'USER'): string {
  const body = { sub: `${role.toLowerCase()}@example.com`, role, exp: Math.floor(Date.now() / 1000) + 900 };
  return `header.${btoa(JSON.stringify(body))}.signature`;
}

function renderPage(role: 'ADMIN' | 'USER') {
  tokenStorage.set(fakeToken(role));

  const store = configureStore({ reducer: { employees: employeesReducer } });

  return render(
    <Provider store={store}>
      <MemoryRouter>
        <AuthProvider>
          <EmployeeListPage />
        </AuthProvider>
      </MemoryRouter>
    </Provider>,
  );
}

describe('EmployeeListPage', () => {
  beforeEach(() => {
    tokenStorage.clear();
    vi.mocked(employeeApi.list).mockResolvedValue({
      content: [employee], totalElements: 1, totalPages: 1, number: 0, size: 10,
    });
  });

  it('lists the employees returned by the server', async () => {
    renderPage('ADMIN');

    expect(await screen.findByText('Grace Hopper')).toBeInTheDocument();
    expect(screen.getByText('Software Development')).toBeInTheDocument();
  });

  it('offers the write actions to an administrator', async () => {
    renderPage('ADMIN');

    expect(await screen.findByRole('button', { name: /new employee/i })).toBeInTheDocument();
    expect(screen.getByLabelText('Edit')).toBeInTheDocument();
  });

  it('hides the write actions from a plain user', async () => {
    // Bu bir guvenlik onlemi degil: sunucu zaten 403 doner. Amac kullaniciya
    // kacinilmaz olarak reddedilecek bir dugmeyi hic gostermemek.
    renderPage('USER');

    await screen.findByText('Grace Hopper');
    expect(screen.queryByRole('button', { name: /new employee/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Edit')).not.toBeInTheDocument();
  });

  it('shows the failure message when the list cannot be loaded', async () => {
    vi.mocked(employeeApi.list).mockRejectedValue(new Error('boom'));

    renderPage('ADMIN');

    await waitFor(() =>
      expect(screen.getByText('An unexpected error occurred')).toBeInTheDocument());
  });
});
