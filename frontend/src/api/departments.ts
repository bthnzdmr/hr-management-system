import { api } from './client';
import type { Department } from '../types/api';

export const departmentApi = {
  /**
   * Referans verisi: sinirli sayida ve nadiren degisir, bu yuzden sayfasiz.
   *
   * Varsayilan yalnizca AKTIF olanlar -- secim listesine kapatilmis bir
   * departman dusmemelidir. Yonetim ekrani hepsini ister: kapatilmis olani
   * geri acabilmek icin once gorebilmek gerekir.
   */
  list: (includeInactive = false) =>
    api.get<Department[]>('/api/departments', { params: { includeInactive } })
      .then((r) => r.data),

  create: (name: string) =>
    api.post<Department>('/api/departments', { name }).then((r) => r.data),

  changeStatus: (id: number, active: boolean) =>
    api.put<Department>(`/api/departments/${id}/status`, { active }).then((r) => r.data),
};
