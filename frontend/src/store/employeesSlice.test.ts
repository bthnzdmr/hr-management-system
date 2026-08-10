import { describe, expect, it } from 'vitest';
import reducer, {
  deactivateEmployee, errorCleared, fetchEmployees, pageChanged, pageSizeChanged,
} from './employeesSlice';
import type { Employee } from '../types/api';

const employee = (id: number, active = true): Employee => ({
  id,
  firstName: 'Ada',
  lastName: 'Lovelace',
  email: `ada${id}@example.com`,
  phone: null,
  departmentId: 1,
  departmentName: 'Software Development',
  managerId: null,
  jobTitle: 'Engineer',
  hireDate: '2024-01-15',
  active,
});

const initial = reducer(undefined, { type: '@@INIT' });

describe('employees reducer', () => {
  it('shows a loading state while the request is in flight', () => {
    const state = reducer(initial, fetchEmployees.pending('', { page: 0, size: 10 }));

    expect(state.status).toBe('loading');
    expect(state.error).toBeNull();
  });

  it('replaces the list with the fetched page', () => {
    const page = { content: [employee(1), employee(2)], totalElements: 42, totalPages: 5, number: 0, size: 10 };

    const state = reducer(initial, fetchEmployees.fulfilled(page, '', { page: 0, size: 10 }));

    expect(state.status).toBe('succeeded');
    expect(state.items).toHaveLength(2);
    expect(state.totalElements).toBe(42);
  });

  it('keeps the failure message so the page can show it', () => {
    const action = {
      type: fetchEmployees.rejected.type,
      payload: 'The server could not be reached',
    };

    const state = reducer(initial, action);

    expect(state.status).toBe('failed');
    expect(state.error).toBe('The server could not be reached');
  });

  it('marks the employee inactive without refetching the whole page', () => {
    const loaded = { ...initial, items: [employee(1), employee(2)] };

    const state = reducer(loaded, deactivateEmployee.fulfilled(1, '', 1));

    expect(state.items.find((e) => e.id === 1)?.active).toBe(false);
    expect(state.items.find((e) => e.id === 2)?.active).toBe(true);
  });

  it('returns to the first page when the page size changes', () => {
    // Aksi halde 5. sayfadayken boyutu buyutunce var olmayan bir sayfa istenir.
    const state = reducer({ ...initial, page: 4 }, pageSizeChanged(50));

    expect(state.size).toBe(50);
    expect(state.page).toBe(0);
  });

  it('keeps the page size when only the page changes', () => {
    const state = reducer({ ...initial, size: 20 }, pageChanged(3));

    expect(state.page).toBe(3);
    expect(state.size).toBe(20);
  });

  it('clears the error when the user dismisses it', () => {
    const state = reducer({ ...initial, error: 'boom' }, errorCleared());

    expect(state.error).toBeNull();
  });
});
