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
  /**
   * Dugum siniri asildi mi.
   *
   * `unreachable`den AYRI bir alan cunku ayri bir olgu: biri VERI KUSURU,
   * digeri CIZIM SINIRI. Sunucu kirpildiginda `unreachable` gondermiyor --
   * cizilmeyenlerin hangisinin hangi sebeple disarida kaldigi ayirt
   * edilemez -- bu yuzden arayuz de iki mesaji ayri gosterir.
   */
  truncated: boolean;
}

export const orgChartApi = {
  get: () => api.get<OrgChart>('/api/org-chart').then((r) => r.data),
};
