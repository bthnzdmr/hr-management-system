import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { ChangePasswordPage } from './ChangePasswordPage';
import employeesReducer from '../store/employeesSlice';
import { AuthProvider } from '../auth/AuthContext';
import { SnackbarProvider } from '../components/SnackbarProvider';
import { tokenStorage } from '../api/client';
import { userApi } from '../api/users';

vi.mock('../api/users', () => ({
  userApi: { changeOwnPassword: vi.fn() },
}));

function fakeToken(): string {
  const body = {
    sub: 'ada@example.com',
    roles: ['EMPLOYEE'],
    exp: Math.floor(Date.now() / 1000) + 900,
  };
  return `header.${btoa(JSON.stringify(body))}.signature`;
}

function renderPage() {
  tokenStorage.set(fakeToken());
  tokenStorage.setRefresh('refresh-token');

  const store = configureStore({ reducer: { employees: employeesReducer } });

  return render(
    <Provider store={store}>
      <SnackbarProvider>
        <MemoryRouter initialEntries={['/account/password']}>
          <AuthProvider>
            <Routes>
              <Route path="/account/password" element={<ChangePasswordPage />} />
              <Route path="/login" element={<div>sign in</div>} />
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      </SnackbarProvider>
    </Provider>,
  );
}

// Duzenli ifade sart: MUI zorunlu alanlarin etiketine " *" ekliyor ve
// birebir eslesme tutmuyor.
async function fillIn(user: ReturnType<typeof userEvent.setup>, next: string, repeat: string) {
  await user.type(screen.getByLabelText(/^Current password/), 'current-password');
  await user.type(screen.getByLabelText(/^New password/), next);
  await user.type(screen.getByLabelText(/^Repeat new password/), repeat);
}

describe('ChangePasswordPage', () => {
  beforeEach(() => {
    tokenStorage.clear();
    vi.clearAllMocks();
    vi.mocked(userApi.changeOwnPassword).mockResolvedValue(undefined);
  });

  it('sends the current and the new password', async () => {
    const user = userEvent.setup({ delay: null });
    renderPage();

    await fillIn(user, 'a-brand-new-password', 'a-brand-new-password');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    await waitFor(() => expect(userApi.changeOwnPassword).toHaveBeenCalledWith({
      currentPassword: 'current-password',
      newPassword: 'a-brand-new-password',
    }));
  });

  it('refuses to submit when the repeated password does not match', async () => {
    // Yazim hatasiyla kendini kilitlemeyi onler. Sunucunun bu alandan haberi
    // yoktur; kontrol tamamen arayuze aittir.
    const user = userEvent.setup({ delay: null });
    renderPage();

    await fillIn(user, 'a-brand-new-password', 'a-brand-new-passward');

    expect(screen.getByText('The two passwords do not match')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Change password' })).toBeDisabled();
    expect(userApi.changeOwnPassword).not.toHaveBeenCalled();
  });

  it('signs the user out afterwards because every session was revoked', async () => {
    // Sunucu parola degisiminde tum jetonlari iptal ediyor; kullaniciyi bu
    // ekranda birakmak, her istegi sessizce basarisiz olan bir oturumda
    // birakmak olurdu.
    const user = userEvent.setup({ delay: null });
    renderPage();

    await fillIn(user, 'a-brand-new-password', 'a-brand-new-password');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    expect(await screen.findByText('sign in')).toBeInTheDocument();
    expect(tokenStorage.get()).toBeNull();
    expect(tokenStorage.getRefresh()).toBeNull();
  });

  it('keeps the user on the page when the current password is wrong', async () => {
    const user = userEvent.setup({ delay: null });
    vi.mocked(userApi.changeOwnPassword).mockRejectedValue(new Error('boom'));

    renderPage();

    await fillIn(user, 'a-brand-new-password', 'a-brand-new-password');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    expect(await screen.findByText('An unexpected error occurred')).toBeInTheDocument();
    expect(screen.queryByText('sign in')).not.toBeInTheDocument();
    // Oturum kapanmamali: parola yanlis yazmak disari atilma sebebi degildir.
    expect(tokenStorage.get()).not.toBeNull();
  });
  it('shows the password rules before anything is typed', async () => {
    // Gorunmeyen bir kural, korlemesine carpilan bir kuraldir: kullanici
    // parolayi yazip gonderdikten SONRA ogrenmemeli.
    renderPage();

    expect(await screen.findByText(/At least 12 characters/)).toBeInTheDocument();
    expect(screen.getByText(/common word/)).toBeInTheDocument();
    expect(screen.getByText(/email address/)).toBeInTheDocument();
  });
});
