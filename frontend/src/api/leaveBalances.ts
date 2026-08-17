import { api } from './client';

/**
 * Yillik izin bakiyesi.
 *
 * Uc bilerek `/api/leave-balances`, `/api/employees/{id}/...` DEGIL: ikincisi
 * sunucudaki genel personel okuma kuralina duser ve izin verisi onu gormemesi
 * gereken rollere acilirdi.
 */
export interface LeaveBalance {
  employeeId: number;
  year: number;
  entitledDays: number;
  carriedOverDays: number;
  usedDays: number;
  /** Karar bekleyen gunler; bakiyeden DUSULMUS sayilir. */
  reservedDays: number;
  /** Negatif olabilir: hak asilmissa gizlenmez. */
  availableDays: number;
  /**
   * Hak Ik tarafindan mi verildi, yoksa varsayilan mi uygulandi?
   *
   * Ekranda ayirt edilir: varsayilan bir KARAR degil, bir tahmindir.
   */
  source: 'GRANTED' | 'DEFAULT';
}

export const leaveBalanceApi = {
  get: (employeeId: number, year?: number) =>
    api.get<LeaveBalance>(`/api/leave-balances/${employeeId}`, {
      params: { year },
    }).then((r) => r.data),
};
