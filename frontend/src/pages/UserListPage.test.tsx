import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { UserListPage } from './UserListPage';
import employeesReducer from '../store/employeesSlice';
import { AuthProvider } from '../auth/AuthContext';
import { SnackbarProvider } from '../components/SnackbarProvider';
import { tokenStorage } from '../api/client';
import { userApi } from '../api/users';
import type { User } from '../types/api';

vi.mock('../api/users', () => ({
  userApi: {
    list: vi.fn(),
    create: vi.fn(),
    changeRole: vi.fn(),
    changeStatus: vi.fn(),
  },
}));

vi.mock('../api/employees', () => ({
  employeeApi: { list: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 }) },
}));

function account(overrides: Partial<User> = {}): User {
  return {
    id: 1,
    email: 'ada@example.com',
    role: 'USER',
    active: true,
    employeeId: null,
    employeeFullName: null,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function fakeToken(email: string): string {
  const body = { sub: email, role: 'ADMIN', exp: Math.floor(Date.now() / 1000) + 900 };
  return `header.${btoa(JSON.stringify(body))}.signature`;
}

function renderPage(signedInAs = 'admin@example.com') {
  tokenStorage.set(fakeToken(signedInAs));

  const store = configureStore({ reducer: { employees: employeesReducer } });

  return render(
    <Provider store={store}>
      <SnackbarProvider>
        <MemoryRouter>
          <AuthProvider>
            <UserListPage />
          </AuthProvider>
        </MemoryRouter>
      </SnackbarProvider>
    </Provider>,
  );
}

function pageOf(users: User[]) {
  return { content: users, totalElements: users.length, totalPages: 1, number: 0, size: 10 };
}

describe('UserListPage', () => {
  beforeEach(() => {
    tokenStorage.clear();
    vi.clearAllMocks();
    vi.mocked(userApi.list).mockResolvedValue(pageOf([account()]));
  });

  it('lists the accounts returned by the server', async () => {
    renderPage();

    expect(await screen.findByText('ada@example.com')).toBeInTheDocument();
  });

  it('shows which employee an account belongs to', async () => {
    vi.mocked(userApi.list).mockResolvedValue(
      pageOf([account({ employeeId: 4, employeeFullName: 'Ada Lovelace' })]),
    );

    renderPage();

    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
  });

  it('says so when an account is not linked to an employee', async () => {
    renderPage();

    expect(await screen.findByText('Not linked')).toBeInTheDocument();
  });

  it('never renders anything that looks like a password', async () => {
    renderPage();
    await screen.findByText('ada@example.com');

    // Sunucu parola ozetini hic gondermiyor; bu test sozlesme degisirse
    // sessizce sizmasini engeller.
    expect(document.body.textContent).not.toMatch(/\$2[aby]\$/);
  });

  it('promotes an account through the server', async () => {
    const user = userEvent.setup();
    vi.mocked(userApi.changeRole).mockResolvedValue(account({ role: 'ADMIN' }));

    renderPage();
    await screen.findByText('ada@example.com');

    // Sayfa boyutu secimi de bir combobox; rol kutusu adiyla ayirt edilir.
    await user.click(screen.getByRole('combobox', { name: 'Role for ada@example.com' }));
    await user.click(await screen.findByRole('option', { name: 'ADMIN' }));

    await waitFor(() => expect(userApi.changeRole).toHaveBeenCalledWith(1, 'ADMIN'));
  });

  it('does not let an administrator act on their own account', async () => {
    // Sunucu da reddediyor; amac kacinilmaz olarak reddedilecek bir dugmeyi
    // hic sunmamak.
    vi.mocked(userApi.list).mockResolvedValue(
      pageOf([account({ email: 'admin@example.com', role: 'ADMIN' })]),
    );

    renderPage('admin@example.com');

    expect(await screen.findByText('You')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Deactivate' })).toBeDisabled();
    expect(screen.getByRole('combobox', { name: 'Role for admin@example.com' }))
      .toHaveAttribute('aria-disabled', 'true');
  });

  it('confirms before deactivating an account', async () => {
    const user = userEvent.setup();
    vi.mocked(userApi.changeStatus).mockResolvedValue(account({ active: false }));

    renderPage();
    await screen.findByText('ada@example.com');

    await user.click(screen.getByRole('button', { name: 'Deactivate' }));

    const dialog = await screen.findByRole('dialog');
    expect(userApi.changeStatus).not.toHaveBeenCalled();

    await user.click(within(dialog).getByRole('button', { name: 'Deactivate' }));

    await waitFor(() => expect(userApi.changeStatus).toHaveBeenCalledWith(1, false));
  });

  it('reactivates without asking', async () => {
    const user = userEvent.setup();
    vi.mocked(userApi.list).mockResolvedValue(pageOf([account({ active: false })]));
    vi.mocked(userApi.changeStatus).mockResolvedValue(account({ active: true }));

    renderPage();
    await screen.findByText('ada@example.com');

    await user.click(screen.getByRole('button', { name: 'Activate' }));

    await waitFor(() => expect(userApi.changeStatus).toHaveBeenCalledWith(1, true));
  });

  it('shows the rule the server enforced instead of pretending it worked', async () => {
    // "Son yonetici kalmali" gibi kurallar sunucudan gelir; arayuz onlari
    // tekrarlamaz, gosterir.
    const user = userEvent.setup();
    vi.mocked(userApi.list).mockResolvedValue(pageOf([account({ active: false })]));
    vi.mocked(userApi.changeStatus).mockRejectedValue(new Error('boom'));

    renderPage();
    await screen.findByText('ada@example.com');

    await user.click(screen.getByRole('button', { name: 'Activate' }));

    expect(await screen.findByText('An unexpected error occurred')).toBeInTheDocument();
  });

  it('shows the failure message when the accounts cannot be loaded', async () => {
    vi.mocked(userApi.list).mockRejectedValue(new Error('boom'));

    renderPage();

    expect(await screen.findByText('An unexpected error occurred')).toBeInTheDocument();
  });
});
