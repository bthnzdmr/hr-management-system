import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import employeesReducer from '../store/employeesSlice';
import { AuthProvider, useAuth } from './AuthContext';
import { api, tokenStorage } from '../api/client';

/** Verilen govde ve gecerlilik suresiyle imzasiz bir JWT uretir. */
function fakeToken(payload: Record<string, unknown>, expiresInSeconds = 900): string {
  const body = { ...payload, exp: Math.floor(Date.now() / 1000) + expiresInSeconds };
  return `header.${btoa(JSON.stringify(body))}.signature`;
}

function Probe() {
  const { user, canEditEmployees, canManageAccounts, login, logout } = useAuth();

  return (
    <div>
      <span data-testid="email">{user?.email ?? 'anonymous'}</span>
      <span data-testid="roles">{user?.roles.join(",") ?? 'none'}</span>
      <span data-testid="canEdit">{canEditEmployees ? 'yes' : 'no'}</span>
      <span data-testid="canManage">{canManageAccounts ? 'yes' : 'no'}</span>
      <button onClick={() => login({ email: 'a@b.c', password: 'secret' })}>login</button>
      <button onClick={logout}>logout</button>
    </div>
  );
}

// AuthProvider oturum bitince store'u sifirladigi icin Redux saglayicisi
// gerektirir; uygulamadaki agac burada birebir kurulur.
function renderProbe() {
  const store = configureStore({ reducer: { employees: employeesReducer } });

  return render(
    <Provider store={store}>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </Provider>,
  );
}

describe('AuthProvider', () => {
  beforeEach(() => {
    tokenStorage.clear();
    vi.restoreAllMocks();
  });

  it('starts anonymous when nothing is stored', () => {
    renderProbe();

    expect(screen.getByTestId('email')).toHaveTextContent('anonymous');
  });

  it('restores the session from the stored token after a page reload', () => {
    tokenStorage.set(fakeToken({ sub: 'admin@example.com', roles: ['HR_SPECIALIST', 'SYSTEM_ADMIN'] }));

    renderProbe();

    expect(screen.getByTestId('email')).toHaveTextContent('admin@example.com');
    expect(screen.getByTestId('canEdit')).toHaveTextContent('yes');
    expect(screen.getByTestId('canManage')).toHaveTextContent('yes');
  });

  it('discards an expired token instead of trusting it', () => {
    tokenStorage.set(fakeToken({ sub: 'admin@example.com', roles: ['SYSTEM_ADMIN'] }, -60));

    renderProbe();

    expect(screen.getByTestId('email')).toHaveTextContent('anonymous');
    expect(tokenStorage.get()).toBeNull();
  });

  it('discards a token whose body cannot be read', () => {
    tokenStorage.set('not-a-token');

    renderProbe();

    expect(screen.getByTestId('email')).toHaveTextContent('anonymous');
  });

  it('grants no write capability to a plain employee', () => {
    tokenStorage.set(fakeToken({ sub: 'user@example.com', roles: ['EMPLOYEE'] }));

    renderProbe();

    expect(screen.getByTestId('canEdit')).toHaveTextContent('no');
    expect(screen.getByTestId('canManage')).toHaveTextContent('no');
  });

  it('stores the token and identifies the user after signing in', async () => {
    const token = fakeToken({ sub: 'admin@example.com', roles: ['HR_SPECIALIST', 'SYSTEM_ADMIN'] });
    vi.spyOn(api, 'post').mockResolvedValue({ data: { token } });

    renderProbe();
    await userEvent.click(screen.getByText('login'));

    await waitFor(() => expect(screen.getByTestId('email')).toHaveTextContent('admin@example.com'));
    expect(tokenStorage.get()).toBe(token);
  });

  it('removes the stored token when signing out', async () => {
    tokenStorage.set(fakeToken({ sub: 'admin@example.com', roles: ['HR_SPECIALIST', 'SYSTEM_ADMIN'] }));

    renderProbe();
    await userEvent.click(screen.getByText('logout'));

    expect(screen.getByTestId('email')).toHaveTextContent('anonymous');
    expect(tokenStorage.get()).toBeNull();
  });
});
