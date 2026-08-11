import { api } from './client';
import type { Page, PasswordChangeRequest, Role, User, UserCreateRequest } from '../types/api';

const BASE = '/api/users';

export const userApi = {
  list: (page: number, size: number) =>
    api.get<Page<User>>(BASE, { params: { page, size, sort: 'email,asc' } }).then((r) => r.data),

  create: (request: UserCreateRequest) =>
    api.post<User>(BASE, request).then((r) => r.data),

  changeRole: (id: number, role: Role) =>
    api.put<User>(`${BASE}/${id}/role`, { role }).then((r) => r.data),

  changeStatus: (id: number, active: boolean) =>
    api.put<User>(`${BASE}/${id}/status`, { active }).then((r) => r.data),

  // Yolda id YOK: "/me" daima token'in sahibidir. Istemcinin gonderdigi bir
  // id'ye guvenmek, herkesin baskasinin parolasini denemesine kapi acardi.
  changeOwnPassword: (request: PasswordChangeRequest) =>
    api.put<void>(`${BASE}/me/password`, request).then(() => undefined),
};
