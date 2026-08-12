import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { EmployeeFormPage } from './EmployeeFormPage';
import { SnackbarProvider } from '../components/SnackbarProvider';
import { employeeApi } from '../api/employees';
import { departmentApi } from '../api/departments';
import type { Employee } from '../types/api';

vi.mock('../api/employees', () => ({
  employeeApi: {
    list: vi.fn(),
    getById: vi.fn(),
    getSalary: vi.fn(),
    update: vi.fn(),
    updateSalary: vi.fn(),
    create: vi.fn(),
  },
}));

vi.mock('../api/departments', () => ({
  departmentApi: { list: vi.fn() },
}));

function makeEmployee(overrides: Partial<Employee> = {}): Employee {
  return {
    id: 5,
    firstName: 'Alan',
    lastName: 'Kay',
    email: 'alan@example.com',
    phone: null,
    departmentId: 1,
    departmentName: 'Software Development',
    managerId: 9,
    managerFullName: 'Barbara Liskov',
    jobTitle: 'Engineer',
    hireDate: '2024-01-15',
    active: true,
    terminatedAt: null,
    terminationReason: null,
    ...overrides,
  };
}

function renderForm(path: string) {
  return render(
    <SnackbarProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/employees/new" element={<EmployeeFormPage />} />
          <Route path="/employees/:id" element={<EmployeeFormPage />} />
          <Route path="/employees" element={<div>list</div>} />
        </Routes>
      </MemoryRouter>
    </SnackbarProvider>,
  );
}

describe('EmployeeFormPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(departmentApi.list).mockResolvedValue([
      { id: 1, name: 'Software Development' },
      { id: 2, name: 'Finance' },
    ]);
    vi.mocked(employeeApi.getById).mockResolvedValue(makeEmployee());
    vi.mocked(employeeApi.getSalary).mockResolvedValue({ employeeId: 5, salary: 95000 });
    vi.mocked(employeeApi.list).mockResolvedValue({
      content: [makeEmployee({ id: 9, firstName: 'Barbara', lastName: 'Liskov' })],
      totalElements: 1, totalPages: 1, number: 0, size: 10,
    });
    vi.mocked(employeeApi.update).mockResolvedValue(makeEmployee());
    vi.mocked(employeeApi.create).mockResolvedValue(makeEmployee());
  });

  it('shows the current manager by name, not by id', async () => {
    renderForm('/employees/5');

    expect(await screen.findByDisplayValue('Barbara Liskov')).toBeInTheDocument();
  });

  it('keeps the existing manager when nothing else is touched', async () => {
    // Yonetici alani ad ile dolduruluyor; okunamazsa kaydederken sessizce
    // null gider ve kisi yoneticisiz kalirdi.
    const user = userEvent.setup();
    renderForm('/employees/5');
    await screen.findByDisplayValue('Barbara Liskov');

    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(employeeApi.update).toHaveBeenCalledWith(5, expect.objectContaining({ managerId: 9 })));
  });

  it('sends no manager once the selection is cleared', async () => {
    const user = userEvent.setup();
    renderForm('/employees/5');
    await screen.findByDisplayValue('Barbara Liskov');

    // Temizleme dugmesi alan odaklanana kadar gizlidir; once kutuya girilir.
    await user.click(screen.getByLabelText('Manager'));
    await user.click(screen.getByRole('button', { name: 'Clear' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(employeeApi.update).toHaveBeenCalledWith(5, expect.objectContaining({ managerId: null })));
  });

  it('asks the server only for active employees as manager candidates', async () => {
    renderForm('/employees/new');
    await screen.findByLabelText('Manager');

    // Pasif bir kisi yonetici atanamaz; aday listesinde de gorunmemeli.
    await waitFor(() =>
      expect(employeeApi.list).toHaveBeenCalledWith(expect.objectContaining({ active: true })));
  });

  it('does not offer the edited employee as their own manager', async () => {
    const user = userEvent.setup();
    vi.mocked(employeeApi.list).mockResolvedValue({
      content: [makeEmployee({ id: 5 }), makeEmployee({ id: 9, firstName: 'Barbara', lastName: 'Liskov' })],
      totalElements: 2, totalPages: 1, number: 0, size: 10,
    });

    renderForm('/employees/5');
    await screen.findByDisplayValue('Barbara Liskov');

    await user.click(screen.getByLabelText('Open'));

    const options = await screen.findAllByRole('option');
    expect(options).toHaveLength(1);
    expect(options[0]).toHaveTextContent('Barbara Liskov');
  });

  it('does not touch the salary endpoint when the salary is unchanged', async () => {
    // Gereksiz bir yazma, gereksiz bir olay ve gereksiz bir mail demektir.
    const user = userEvent.setup();
    renderForm('/employees/5');
    await screen.findByDisplayValue('Barbara Liskov');

    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(employeeApi.update).toHaveBeenCalled());
    expect(employeeApi.updateSalary).not.toHaveBeenCalled();
  });

  it('writes the salary through its own endpoint when it changes', async () => {
    const user = userEvent.setup();
    renderForm('/employees/5');
    const salary = await screen.findByLabelText('Salary');

    await user.clear(salary);
    await user.type(salary, '99000');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(employeeApi.updateSalary).toHaveBeenCalledWith(5, { salary: 99000 }));
  });

  it('refuses to save instead of silently ignoring a cleared salary', async () => {
    // Olculen kusur: alan bosaltilinca salary null oluyor ve "!== null"
    // korumasi silme niyetini SESSIZCE yutuyordu -- istek hic gitmiyor,
    // kullaniciya "Employee updated" deniyordu. Gerceklesmeyen bir islemin
    // basarili bildirilmesi, en hassas alanda bir dogruluk hatasidir.
    const user = userEvent.setup();
    renderForm('/employees/5');
    const salary = await screen.findByLabelText('Salary');

    await user.clear(salary);
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(/Salary cannot be removed here/)).toBeInTheDocument();

    // Hicbir sey yazilmamali: ne genel guncelleme, ne maas ucu.
    expect(employeeApi.updateSalary).not.toHaveBeenCalled();
    expect(employeeApi.update).not.toHaveBeenCalled();
  });

  it('still allows saving a record that never had a salary', async () => {
    // Maasi HIC OLMAYAN kayitta bos alan gecerlidir; kural yalnizca
    // "vardi, silindi" durumuna uygulanir.
    vi.mocked(employeeApi.getSalary).mockResolvedValue({ employeeId: 5, salary: null });

    const user = userEvent.setup();
    renderForm('/employees/5');
    await screen.findByLabelText('Salary');

    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(employeeApi.update).toHaveBeenCalled());
    expect(employeeApi.updateSalary).not.toHaveBeenCalled();
  });

  it('opens the form even when the salary cannot be read', async () => {
    // Maas ikincil bir bilgidir; alinamamasi formu tamamen engellememeli.
    vi.mocked(employeeApi.getSalary).mockRejectedValue(new Error('forbidden'));

    renderForm('/employees/5');

    expect(await screen.findByDisplayValue('alan@example.com')).toBeInTheDocument();
  });

  it('reports the server error instead of navigating away', async () => {
    const user = userEvent.setup();
    vi.mocked(employeeApi.update).mockRejectedValue(new Error('boom'));

    renderForm('/employees/5');
    await screen.findByDisplayValue('Barbara Liskov');

    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('An unexpected error occurred')).toBeInTheDocument();
    expect(screen.queryByText('list')).not.toBeInTheDocument();
  });
});
