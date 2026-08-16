import { describe, expect, it, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { Layout } from './Layout';
import employeesReducer from '../store/employeesSlice';
import { AuthProvider } from '../auth/AuthContext';
import { ColorModeProvider } from '../theme/ColorModeContext';
import { tokenStorage } from '../api/client';
import type { Role } from '../types/api';

function fakeToken(roles: Role[]): string {
  const body = {
    sub: 'ada@example.com',
    roles,
    exp: Math.floor(Date.now() / 1000) + 900,
  };
  return `header.${btoa(JSON.stringify(body))}.signature`;
}

function renderShell(roles: Role[]) {
  tokenStorage.set(fakeToken(roles));

  const store = configureStore({ reducer: { employees: employeesReducer } });

  return render(
    <Provider store={store}>
      <ColorModeProvider>
        <MemoryRouter initialEntries={['/employees']}>
          <AuthProvider>
            <Layout />
          </AuthProvider>
        </MemoryRouter>
      </ColorModeProvider>
    </Provider>,
  );
}

describe('Layout', () => {
  beforeEach(() => {
    tokenStorage.clear();
  });

  it('shows the signed-in identity and every role it carries', () => {
    renderShell(['HR_SPECIALIST', 'SYSTEM_ADMIN']);

    expect(screen.getByText('ada@example.com')).toBeInTheDocument();
    // Coklu rol: tek rozet artik kimligi anlatmiyor.
    expect(screen.getByText('HR specialist')).toBeInTheDocument();
    expect(screen.getByText('System administrator')).toBeInTheDocument();
  });

  it('opens the account actions from the identity block', async () => {
    const user = userEvent.setup({ delay: null });
    renderShell(['HR_SPECIALIST']);

    // Adi acikca verilir: aksi halde dugmenin erisilebilir adi icindeki her
    // metnin birlesimi olurdu ("A ada@example.com HR specialist").
    const trigger = screen.getByRole('button', { name: 'Account menu for ada@example.com' });
    expect(trigger).toHaveAttribute('aria-expanded', 'false');

    await user.click(trigger);

    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('menuitem', { name: 'Sign out' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Change password' })).toBeInTheDocument();
  });

  it('offers the account screen to a system administrator', () => {
    renderShell(['HR_SPECIALIST', 'SYSTEM_ADMIN']);

    expect(screen.getByRole('link', { name: 'Accounts' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Overview' })).toBeInTheDocument();
  });

  it('hides those destinations from a plain user', () => {
    // Sunucu zaten 403 doner; amac kullaniciya reddedilecek bir yolu hic
    // gostermemek.
    renderShell(['EMPLOYEE']);

    expect(screen.getByRole('link', { name: 'Employees' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Accounts' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Overview' })).not.toBeInTheDocument();
  });

  it('keeps actions out of the navigation', () => {
    // Menu ogesi gidilecek bir YER olmali, bir eylem degil. "Yeni personel"
    // dugmesi listenin ustunde duruyor ve oraya ait.
    renderShell(['HR_SPECIALIST', 'SYSTEM_ADMIN']);

    expect(screen.queryByRole('link', { name: 'New employee' })).not.toBeInTheDocument();
  });

  it('switches the theme and remembers the choice', async () => {
    const user = userEvent.setup({ delay: null });
    renderShell(['EMPLOYEE']);

    // matchMedia testte hep "eslesmiyor" doner, yani baslangic acik temadir:
    // acik temada koyu temaya gecis ikonu gosterilir.
    expect(screen.getByTestId('DarkModeOutlinedIcon')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Toggle theme' }));

    expect(localStorage.getItem('hr.colorMode')).toBe('dark');
    expect(screen.getByTestId('LightModeOutlinedIcon')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Toggle theme' }));
    expect(localStorage.getItem('hr.colorMode')).toBe('light');
  });
});
