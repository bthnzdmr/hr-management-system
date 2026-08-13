import { describe, expect, it, beforeEach, vi } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DepartmentListPage } from './DepartmentListPage';
import { SnackbarProvider } from '../components/SnackbarProvider';
import { departmentApi } from '../api/departments';
import type { Department } from '../types/api';

vi.mock('../api/departments', () => ({
  departmentApi: { list: vi.fn(), create: vi.fn(), changeStatus: vi.fn() },
}));

function department(overrides: Partial<Department> = {}): Department {
  return { id: 1, name: 'Sales', active: true, activeEmployeeCount: 0, ...overrides };
}

function renderPage() {
  return render(
    <SnackbarProvider>
      <DepartmentListPage />
    </SnackbarProvider>,
  );
}

describe('DepartmentListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(departmentApi.list).mockResolvedValue([department()]);
  });

  it('asks for closed departments too, so they can be reopened', async () => {
    renderPage();
    await screen.findByText('Sales');

    // Kapatilmis bir departmani geri acabilmek icin once gorebilmek gerekir.
    expect(departmentApi.list).toHaveBeenCalledWith(true);
  });

  it('refuses to offer closing a department that still has people', async () => {
    // Sunucu da reddediyor; burada devre disi birakmak kullaniciya kacinilmaz
    // bir hata yasatmamak icin.
    vi.mocked(departmentApi.list).mockResolvedValue([
      department({ name: 'Staffed', activeEmployeeCount: 3 }),
    ]);

    renderPage();

    const close = await screen.findByRole('button', { name: 'Close Staffed' });
    expect(close).toBeDisabled();
  });

  it('offers closing an empty department', async () => {
    renderPage();

    const close = await screen.findByRole('button', { name: 'Close Sales' });
    expect(close).toBeEnabled();

    vi.mocked(departmentApi.changeStatus).mockResolvedValue(department({ active: false }));
    await userEvent.click(close);

    await waitFor(() => expect(departmentApi.changeStatus).toHaveBeenCalledWith(1, false));
  });

  it('offers reopening a closed department', async () => {
    vi.mocked(departmentApi.list).mockResolvedValue([
      department({ name: 'Retired', active: false }),
    ]);

    renderPage();

    expect(await screen.findByRole('button', { name: 'Reopen Retired' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'Close Retired' })).not.toBeInTheDocument();
  });

  it('shows the server rule message rather than a generic failure', async () => {
    // "Icinde hala N aktif personel var" mesaji NE YAPILMASI gerektigini soyler.
    //
    // GERCEK bir AxiosError kurulur: errorMessage axios.isAxiosError ile tip
    // kontrolu yapar ve duz bir nesne o kontrolden gecmez -- kurgu gercege
    // benzemezse test, uretimde calismayan bir yolu dogrulamis olurdu.
    const failure = new AxiosError('Bad Request', 'ERR_BAD_REQUEST');
    failure.response = {
      status: 400,
      statusText: 'Bad Request',
      data: { detail: 'This department still has 3 active employees.' },
      headers: new AxiosHeaders(),
      config: { headers: new AxiosHeaders() },
    };
    vi.mocked(departmentApi.changeStatus).mockRejectedValue(failure);

    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Close Sales' }));

    expect(await screen.findByText(/still has 3 active employees/)).toBeInTheDocument();
  });

  it('will not submit an empty name', async () => {
    renderPage();
    await screen.findByText('Sales');

    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled();
  });

  it('creates a department and reloads the list', async () => {
    vi.mocked(departmentApi.create).mockResolvedValue(department({ id: 2, name: 'Finance' }));

    renderPage();
    await screen.findByText('Sales');

    await userEvent.type(screen.getByLabelText(/New department/), '  Finance  ');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    // Bosluklar kirpilir: "  Finance  " ile "Finance" ayri departman degildir.
    await waitFor(() => expect(departmentApi.create).toHaveBeenCalledWith('Finance'));
    expect(departmentApi.list).toHaveBeenCalledTimes(2);
  });

  it('explains an empty list instead of showing a blank table', async () => {
    vi.mocked(departmentApi.list).mockResolvedValue([]);

    renderPage();

    expect(await screen.findByText('No departments yet')).toBeInTheDocument();
  });
});
