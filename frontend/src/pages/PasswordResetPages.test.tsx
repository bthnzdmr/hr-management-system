import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ForgotPasswordPage } from './ForgotPasswordPage';
import { ResetPasswordPage } from './ResetPasswordPage';
import { passwordResetApi } from '../api/passwordReset';

vi.mock('../api/passwordReset', () => ({
  passwordResetApi: { request: vi.fn(), confirm: vi.fn() },
}));

function renderAt(url: string, element: React.ReactElement, path: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path={path} element={element} />
        <Route path="/login" element={<p>Sign in page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(passwordResetApi.request).mockResolvedValue(undefined);
  });

  it('never says whether the account exists', async () => {
    // Sunucu bunu bilerek gizliyor; arayuz "boyle bir hesap yok" deseydi
    // numaralandirma bilgisini geri sizdirirdi.
    const user = userEvent.setup({ delay: null });
    renderAt('/forgot-password', <ForgotPasswordPage />, '/forgot-password');

    await user.type(screen.getByLabelText(/^Email/), 'nobody@example.com');
    await user.click(screen.getByRole('button', { name: 'Send reset link' }));

    expect(await screen.findByText(/a reset link is on its way/i)).toBeInTheDocument();
    expect(screen.queryByText(/no such account/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/not found/i)).not.toBeInTheDocument();
  });

  it('asks the server exactly once, with the address typed', async () => {
    const user = userEvent.setup({ delay: null });
    renderAt('/forgot-password', <ForgotPasswordPage />, '/forgot-password');

    await user.type(screen.getByLabelText(/^Email/), 'ada@example.com');
    await user.click(screen.getByRole('button', { name: 'Send reset link' }));

    expect(passwordResetApi.request).toHaveBeenCalledExactlyOnceWith('ada@example.com');
  });
});

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(passwordResetApi.confirm).mockResolvedValue(undefined);
  });

  it('refuses to show the form without a token', () => {
    // Formu doldurup ANCAK gonderdikten sonra hata gormek zaman kaybi olurdu.
    renderAt('/reset-password', <ResetPasswordPage />, '/reset-password');

    expect(screen.getByText('This link is incomplete')).toBeInTheDocument();
    expect(screen.queryByLabelText(/^New password/)).not.toBeInTheDocument();
  });

  it('sends the token from the address bar', async () => {
    const user = userEvent.setup({ delay: null });
    renderAt('/reset-password?token=abc123', <ResetPasswordPage />, '/reset-password');

    await user.type(screen.getByLabelText(/^New password/), 'a-long-enough-password');
    await user.type(screen.getByLabelText(/^Repeat new password/), 'a-long-enough-password');
    await user.click(screen.getByRole('button', { name: 'Set password' }));

    expect(passwordResetApi.confirm)
      .toHaveBeenCalledExactlyOnceWith('abc123', 'a-long-enough-password');
  });

  it('will not submit two entries that differ', async () => {
    const user = userEvent.setup({ delay: null });
    renderAt('/reset-password?token=abc123', <ResetPasswordPage />, '/reset-password');

    await user.type(screen.getByLabelText(/^New password/), 'a-long-enough-password');
    await user.type(screen.getByLabelText(/^Repeat new password/), 'a-long-enough-passwerd');

    expect(screen.getByRole('button', { name: 'Set password' })).toBeDisabled();
    expect(screen.getByText('The two entries do not match')).toBeInTheDocument();
  });

  it('sends the user to sign in rather than opening a session', async () => {
    // Sifirlama butun oturumlari kapatir; kullanici yeni parolayi GERCEKTEN
    // bildigini ancak girerek gosterir.
    const user = userEvent.setup({ delay: null });
    renderAt('/reset-password?token=abc123', <ResetPasswordPage />, '/reset-password');

    await user.type(screen.getByLabelText(/^New password/), 'a-long-enough-password');
    await user.type(screen.getByLabelText(/^Repeat new password/), 'a-long-enough-password');
    await user.click(screen.getByRole('button', { name: 'Set password' }));

    expect(await screen.findByText('Sign in page')).toBeInTheDocument();
  });

  it('shows the server refusal instead of pretending it worked', async () => {
    vi.mocked(passwordResetApi.confirm).mockRejectedValue(new Error('Invalid reset link'));

    const user = userEvent.setup({ delay: null });
    renderAt('/reset-password?token=stale', <ResetPasswordPage />, '/reset-password');

    await user.type(screen.getByLabelText(/^New password/), 'a-long-enough-password');
    await user.type(screen.getByLabelText(/^Repeat new password/), 'a-long-enough-password');
    await user.click(screen.getByRole('button', { name: 'Set password' }));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByText('Sign in page')).not.toBeInTheDocument();
  });
});
