import { api } from './client';

/**
 * Bir personelin bir yila ait yillik izin hakki.
 *
 * OKUMA UCU YOK ve bu bilincli: `/api/leave-balances/{employeeId}` zaten hakki,
 * devreden gunu ve kaynagi donduruyor. Ikinci bir okuma ucu ayni bilgiyi iki
 * yerde tutmak olurdu.
 */
export interface LeaveEntitlementRequest {
  entitledDays: number;
  carriedOverDays: number;
  /** Zorunlu: tahakkuk isinin yazdigi satirdan ayirt edilebilmesi icin. */
  note: string;
}

export interface LeaveEntitlement {
  employeeId: number;
  year: number;
  entitledDays: number;
  carriedOverDays: number;
  note: string | null;
}

export const leaveEntitlementApi = {
  set: (employeeId: number, year: number, body: LeaveEntitlementRequest) =>
    api
      .put<LeaveEntitlement>(`/api/leave-entitlements/${employeeId}/${year}`, body)
      .then((r) => r.data),
};
