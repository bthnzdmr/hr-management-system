import { api } from './client';
import type {
  Employee, EmployeeCreateRequest, EmployeeUpdateRequest, Page, SalaryResponse, SalaryUpdateRequest,
} from '../types/api';

const BASE = '/api/employees';

export const employeeApi = {
  // Liste daima sayfalidir: sinirsiz liste donen bir uc, veri buyudukce
  // hem sunucuyu hem tarayiciyi kilitler.
  list: (page: number, size: number) =>
    api.get<Page<Employee>>(BASE, { params: { page, size } }).then((r) => r.data),

  getById: (id: number) => api.get<Employee>(`${BASE}/${id}`).then((r) => r.data),

  create: (request: EmployeeCreateRequest) =>
    api.post<Employee>(BASE, request).then((r) => r.data),

  update: (id: number, request: EmployeeUpdateRequest) =>
    api.put<Employee>(`${BASE}/${id}`, request).then((r) => r.data),

  // Silmez, pasiflestirir: gecmis veri korunur ve manager_id referanslari kirilmaz.
  deactivate: (id: number) => api.delete<void>(`${BASE}/${id}`).then(() => undefined),

  // Maas ayri bir alt kaynaktir ve yalnizca ADMIN erisebilir; genel personel
  // cevabinda hic donmez.
  getSalary: (id: number) =>
    api.get<SalaryResponse>(`${BASE}/${id}/salary`).then((r) => r.data),

  updateSalary: (id: number, request: SalaryUpdateRequest) =>
    api.put<SalaryResponse>(`${BASE}/${id}/salary`, request).then((r) => r.data),
};
