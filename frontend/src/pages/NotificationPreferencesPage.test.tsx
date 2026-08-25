import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { NotificationPreferencesPage } from './NotificationPreferencesPage';
import { notificationPreferenceApi } from '../api/notificationPreferences';
import { SnackbarProvider } from '../components/SnackbarProvider';

vi.mock('../api/notificationPreferences');

const preferences = (leaveRequest: boolean, leaveDecision: boolean) => ({
  items: [
    { kind: 'LEAVE_REQUEST' as const, label: 'Leave requests from my team', enabled: leaveRequest },
    { kind: 'LEAVE_DECISION' as const, label: 'Decisions on my leave requests', enabled: leaveDecision },
  ],
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <SnackbarProvider>
        <NotificationPreferencesPage />
      </SnackbarProvider>
    </MemoryRouter>,
  );

describe('NotificationPreferencesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows the options the server offers, with their current state', async () => {
    // Liste SUNUCUDAN geliyor; arayuzde ayrica tutulsaydi yeni bir tur
    // eklendiginde ekranda gorunmez ve kapatilamaz bir bildirim olusurdu.
    vi.mocked(notificationPreferenceApi.mine).mockResolvedValue(preferences(true, false));

    renderPage();

    expect(await screen.findByLabelText('Leave requests from my team')).toBeChecked();
    expect(screen.getByLabelText('Decisions on my leave requests')).not.toBeChecked();
  });

  it('sends the whole set of enabled kinds, not just the one that changed', async () => {
    // Artimli bir uc olsaydi iki es zamanli istek birbirinin uzerine
    // yazabilirdi; komple gondermek istegi idempotent yapar.
    const user = userEvent.setup({ delay: null });
    vi.mocked(notificationPreferenceApi.mine).mockResolvedValue(preferences(true, true));
    vi.mocked(notificationPreferenceApi.replace).mockResolvedValue(preferences(false, true));

    renderPage();

    await user.click(await screen.findByLabelText('Leave requests from my team'));

    await waitFor(() =>
      expect(notificationPreferenceApi.replace).toHaveBeenCalledWith(['LEAVE_DECISION']));
  });

  it('puts the switch back when the server refuses', async () => {
    // Ekran geri alinmasaydi kullanici KAPALI sandigi bir bildirimi almaya
    // devam ederdi -- sessizce yalan soyleyen bir ayar.
    const user = userEvent.setup({ delay: null });
    vi.mocked(notificationPreferenceApi.mine).mockResolvedValue(preferences(true, true));
    vi.mocked(notificationPreferenceApi.replace).mockRejectedValue(new Error('boom'));

    renderPage();

    const toggle = await screen.findByLabelText('Leave requests from my team');
    await user.click(toggle);

    await waitFor(() => expect(toggle).toBeChecked());
    expect(await screen.findByText('An unexpected error occurred')).toBeInTheDocument();
  });

  it('says which notifications cannot be switched off', async () => {
    // Listede gormeyen kullanici onlarin da kapatilabildigini sanip ararsa,
    // bulamadigi icin ayarin bozuk oldugunu dusunurdu.
    vi.mocked(notificationPreferenceApi.mine).mockResolvedValue(preferences(true, true));

    renderPage();

    expect(await screen.findByText(/cannot be switched off/)).toBeInTheDocument();
  });
});
