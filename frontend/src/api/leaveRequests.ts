import { api } from './client';
import type { Page } from '../types/api';

export type LeaveType = 'ANNUAL' | 'SICK' | 'UNPAID' | 'PARENTAL';
export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface LeaveRequest {
  id: number;
  employeeId: number;
  employeeFullName: string;
  type: LeaveType;
  status: LeaveStatus;
  startDate: string;
  /** Son gun DAHILDIR. */
  endDate: string;
  /** Sunucuda hesaplanir: gun sayisi son gunu de sayar. */
  days: number;
  /** Talebi acan kisinin gerekcesi; karar bunu DEGISTIRMEZ. */
  note: string | null;
  /** Karari verenin gerekcesi. Once ikisi tek alandi ve reddetme talebin
   *  gerekcesini siliyordu. */
  decisionNote: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  createdAt: string;
}

export interface LeaveRequestCreate {
  employeeId: number;
  type: LeaveType;
  startDate: string;
  endDate: string;
  note?: string;
}

export const leaveRequestApi = {
  /**
   * Kapsam sunucuda uygulanir; arayuz kimin ne gorecegini KENDI karar vermez.
   * Buradaki tek is filtrenin sunucuya tasinmasi.
   */
  list: (params: { status?: LeaveStatus[]; page?: number; size?: number }) =>
    api.get<Page<LeaveRequest>>('/api/leave-requests', {
      params: {
        status: params.status?.length ? params.status : undefined,
        page: params.page,
        size: params.size,
      },
    }).then((r) => r.data),

  create: (request: LeaveRequestCreate) =>
    api.post<LeaveRequest>('/api/leave-requests', request).then((r) => r.data),

  /**
   * Tek uc uc yone birden calisir; yeni bir durum eklemek yeni bir istemci
   * metodu gerektirmez.
   */
  decide: (id: number, status: Exclude<LeaveStatus, 'PENDING'>, note?: string) =>
    api.put<LeaveRequest>(`/api/leave-requests/${id}/decision`, { status, note })
      .then((r) => r.data),
};
