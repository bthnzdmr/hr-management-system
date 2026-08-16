import { api } from './client';
import type { Page } from '../types/api';

export type AuditAction =
  | 'ACCOUNT_CREATED'
  | 'ROLES_CHANGED'
  | 'ACCOUNT_STATUS_CHANGED'
  | 'EMPLOYEE_CREATED'
  | 'EMPLOYEE_STATUS_CHANGED'
  | 'SALARY_CHANGED'
  | 'DEPARTMENT_CREATED'
  | 'DEPARTMENT_STATUS_CHANGED'
  | 'LEAVE_REQUESTED'
  | 'LEAVE_DECIDED';

export interface AuditEntry {
  id: number;
  actor: string;
  action: AuditAction;
  targetType: string;
  targetId: string | null;
  /** Insan tarafindan okunacak ozet; ucret TUTARI buraya hic yazilmaz. */
  detail: string | null;
  /** Ayni istegin butun servislerdeki loglarini birbirine baglar. */
  correlationId: string | null;
  occurredAt: string;
}

export interface AuditQuery {
  actor?: string;
  action?: AuditAction;
  since?: string;
  page?: number;
  size?: number;
}

export const auditApi = {
  // Siralama GONDERILMEZ: sunucu istemcinin siralamasini zaten yok sayiyor.
  list: (query: AuditQuery) =>
    api.get<Page<AuditEntry>>('/api/audit', {
      params: {
        actor: query.actor || undefined,
        action: query.action || undefined,
        since: query.since || undefined,
        page: query.page,
        size: query.size,
      },
    }).then((r) => r.data),
};
