import { api } from './client';

export interface OrgNode {
  id: number;
  firstName: string;
  lastName: string;
  jobTitle: string;
  departmentName: string;
  depth: number;
  reports: OrgNode[];
}

export interface OrgChart {
  roots: OrgNode[];
  placed: number;
  /**
   * Aktif olup agacta gorunmeyen kisi sayisi.
   *
   * Yoneticisi pasiflesmis personel koke baglanamaz ve agactan duser. Bu sayi
   * gosterilmezse ekranda 8 kisi gorunur, sirkette 10 kisi calisir ve aradaki
   * fark kimseye bildirilmez.
   */
  unreachable: number;
}

export const orgChartApi = {
  get: () => api.get<OrgChart>('/api/org-chart').then((r) => r.data),
};
