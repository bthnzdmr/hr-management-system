import { api } from './client';
import type { Department } from '../types/api';

export const departmentApi = {
  // Referans verisi: sinirli sayida ve nadiren degisir, bu yuzden sayfasiz.
  list: () => api.get<Department[]>('/api/departments').then((r) => r.data),
};
