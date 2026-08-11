import { describe, expect, it } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import reducer, {
  deactivateEmployee, errorCleared, fetchEmployees, pageChanged, pageSizeChanged,
} from './employeesSlice';
import { rootReducer, sessionEnded } from './index';
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

  /** Gercek akis daima pending ile baslar; cevap ancak eslesen istege aittir. */
  function afterRequest(requestId: string) {
    return reducer(initial, fetchEmployees.pending(requestId, { page: 0, size: 10 }));
  }

  it('replaces the list with the fetched page', () => {
    const page = { content: [employee(1), employee(2)], totalElements: 42, totalPages: 5, number: 0, size: 10 };

    const state = reducer(
      afterRequest('req-1'),
      fetchEmployees.fulfilled(page, 'req-1', { page: 0, size: 10 }),
    );

    expect(state.status).toBe('succeeded');
    expect(state.items).toHaveLength(2);
    expect(state.totalElements).toBe(42);
  });

  it('keeps the failure message so the page can show it', () => {
    const state = reducer(afterRequest('req-1'), {
      type: fetchEmployees.rejected.type,
      payload: 'The server could not be reached',
      meta: { requestId: 'req-1' },
    });

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

  it('drops the previous rows when a fetch fails', () => {
    // Hata bandi ustte dururken altta baska bir sayfanin verisini
    // gostermek, kullaniciya yanlis veriyi dogruymus gibi sunar.
    const loaded = {
      ...afterRequest('req-1'),
      items: [employee(1)],
      totalElements: 5,
    };

    const state = reducer(loaded, {
      type: fetchEmployees.rejected.type,
      payload: 'boom',
      meta: { requestId: 'req-1' },
    });

    expect(state.items).toHaveLength(0);
    expect(state.error).toBe('boom');
  });

  it('ignores a stale response that arrives after a newer request', () => {
    const inFlight = reducer(initial, fetchEmployees.pending('req-2', { page: 1, size: 10 }));

    // 'req-1' daha once baslamis ama GEC donmus bir istek.
    const state = reducer(inFlight, {
      type: fetchEmployees.fulfilled.type,
      payload: { content: [employee(9)], totalElements: 1, totalPages: 1, number: 0, size: 10 },
      meta: { requestId: 'req-1' },
    });

    expect(state.items).toHaveLength(0);
    expect(state.status).toBe('loading');
  });

  it('marks the row as being deactivated while the request is in flight', () => {
    const state = reducer(initial, deactivateEmployee.pending('req-1', 7));

    expect(state.deactivatingId).toBe(7);
  });
});

describe('session reset', () => {
  it('drops the loaded employees when the session ends', () => {
    // Ortak kullanilan bir bilgisayarda, cikis yapan kullanicinin listesi
    // sonraki kullaniciya gorunmemelidir.
    const store = configureStore({ reducer: rootReducer });
    store.dispatch(fetchEmployees.pending('req-1', { page: 0, size: 10 }));
    store.dispatch(fetchEmployees.fulfilled(
      { content: [employee(1)], totalElements: 1, totalPages: 1, number: 0, size: 10 },
      'req-1', { page: 0, size: 10 },
    ));
    expect(store.getState().employees.items).toHaveLength(1);

    store.dispatch(sessionEnded());

    expect(store.getState().employees.items).toHaveLength(0);
    expect(store.getState().employees.status).toBe('idle');
  });
});
