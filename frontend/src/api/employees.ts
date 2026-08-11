import { api } from './client';
import type {
  Employee, EmployeeCreateRequest, EmployeeUpdateRequest, Page, SalaryResponse, SalaryUpdateRequest,
} from '../types/api';

const BASE = '/api/employees';

export interface EmployeeListParams {
  page: number;
  size: number;
  /** Spring Data bicimi: "lastName,asc". */
  sort: string;
  /** Bos birakilirsa sunucuya hic gonderilmez. */
  search?: string;
  /** Tanimsiz = ayrim yapma; true/false = yalnizca o durum. */
  active?: boolean;
}

export const employeeApi = {
  // Liste daima sayfalidir: sinirsiz liste donen bir uc, veri buyudukce
  // hem sunucuyu hem tarayiciyi kilitler.
  list: ({ page, size, sort, search, active }: EmployeeListParams) =>
    api
      .get<Page<Employee>>(BASE, {
        // Bos degerler params'a hic konmaz: axios "search=" gonderirdi ve
        // sunucu bos metni gecerli bir arama sanip hicbir sey dondurmezdi.
        params: {
          page,
          size,
          sort,
          ...(search ? { search } : {}),
          ...(active === undefined ? {} : { active }),
        },
      })
      .then((r) => r.data),

  getById: (id: number) => api.get<Employee>(`${BASE}/${id}`).then((r) => r.data),

  getDirectReports: (id: number) =>
    api.get<Employee[]>(`${BASE}/${id}/direct-reports`).then((r) => r.data),

  create: (request: EmployeeCreateRequest) =>
    api.post<Employee>(BASE, request).then((r) => r.data),

  update: (id: number, request: EmployeeUpdateRequest) =>
    api.put<Employee>(`${BASE}/${id}`, request).then((r) => r.data),

  // Silmez, durumu degistirir: gecmis veri korunur, manager_id referanslari
  // kirilmaz ve islem GERI ALINABILIR. Sunucu guncel kaydi doner.
  changeStatus: (id: number, active: boolean) =>
    api.put<Employee>(`${BASE}/${id}/status`, { active }).then((r) => r.data),

  // Maas ayri bir alt kaynaktir ve yalnizca ADMIN erisebilir; genel personel
  // cevabinda hic donmez.
  getSalary: (id: number) =>
    api.get<SalaryResponse>(`${BASE}/${id}/salary`).then((r) => r.data),

  updateSalary: (id: number, request: SalaryUpdateRequest) =>
    api.put<SalaryResponse>(`${BASE}/${id}/salary`, request).then((r) => r.data),
};
