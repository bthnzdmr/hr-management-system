import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthProvider, useAuth } from './AuthContext';
import { api, tokenStorage } from '../api/client';

/** Verilen govde ve gecerlilik suresiyle imzasiz bir JWT uretir. */
function fakeToken(payload: Record<string, unknown>, expiresInSeconds = 900): string {
  const body = { ...payload, exp: Math.floor(Date.now() / 1000) + expiresInSeconds };
  return `header.${btoa(JSON.stringify(body))}.signature`;
}

function Probe() {
  const { user, isAdmin, login, logout } = useAuth();

  return (
    <div>
      <span data-testid="email">{user?.email ?? 'anonymous'}</span>
      <span data-testid="role">{isAdmin ? 'admin' : 'not-admin'}</span>
      <button onClick={() => login({ email: 'a@b.c', password: 'secret' })}>login</button>
      <button onClick={logout}>logout</button>
    </div>
  );
}

function renderProbe() {
  return render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
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
    tokenStorage.set(fakeToken({ sub: 'admin@example.com', role: 'ADMIN' }));

    renderProbe();

    expect(screen.getByTestId('email')).toHaveTextContent('admin@example.com');
    expect(screen.getByTestId('role')).toHaveTextContent('admin');
  });

  it('discards an expired token instead of trusting it', () => {
    tokenStorage.set(fakeToken({ sub: 'admin@example.com', role: 'ADMIN' }, -60));

    renderProbe();

    expect(screen.getByTestId('email')).toHaveTextContent('anonymous');
    expect(tokenStorage.get()).toBeNull();
  });

  it('discards a token whose body cannot be read', () => {
    tokenStorage.set('not-a-token');

    renderProbe();

    expect(screen.getByTestId('email')).toHaveTextContent('anonymous');
  });

  it('reads role USER as not admin', () => {
    tokenStorage.set(fakeToken({ sub: 'user@example.com', role: 'USER' }));

    renderProbe();

    expect(screen.getByTestId('role')).toHaveTextContent('not-admin');
  });

  it('stores the token and identifies the user after signing in', async () => {
    const token = fakeToken({ sub: 'admin@example.com', role: 'ADMIN' });
    vi.spyOn(api, 'post').mockResolvedValue({ data: { token } });

    renderProbe();
    await userEvent.click(screen.getByText('login'));

    await waitFor(() => expect(screen.getByTestId('email')).toHaveTextContent('admin@example.com'));
    expect(tokenStorage.get()).toBe(token);
  });

  it('removes the stored token when signing out', async () => {
    tokenStorage.set(fakeToken({ sub: 'admin@example.com', role: 'ADMIN' }));

    renderProbe();
    await userEvent.click(screen.getByText('logout'));

    expect(screen.getByTestId('email')).toHaveTextContent('anonymous');
    expect(tokenStorage.get()).toBeNull();
  });
});
