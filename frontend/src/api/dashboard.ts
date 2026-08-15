import { api } from './client';

export interface DashboardOverview {
  headcount: {
    active: number;
    inactive: number;
    hiredLast30Days: number;
    hiredLast90Days: number;
    leftLast12Months: number;
    turnoverRate: number;
  };
  byDepartment: { department: string; active: number }[];
  turnoverByMonth: { month: string; leavers: number }[];
  hiresByMonth: { month: string; hires: number }[];
  terminationReasons: { reason: string; count: number }[];
  spanOfControl: {
    managerCount: number;
    averageDirectReports: number;
    largestTeam: number;
  };
  /**
   * Yonetici basina yuk. spanOfControl ozetin kendisi; bu DAGILIMI tasir --
   * ortalama, "biri 12 tasirken digeri 1 tasiyor" durumunu gizler.
   */
  dataQuality: {
    activeWithoutManager: number;
    emptyDepartments: number;
  };
}

export const dashboardApi = {
  // Tek istek, tek anlik goruntu: her kutucuk ayri uctan beslenseydi
  // bazilari digerlerinden once doner ve birbiriyle celisen sayilar gorunurdu.
  overview: () => api.get<DashboardOverview>('/api/dashboard').then((r) => r.data),
};
