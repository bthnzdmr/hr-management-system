import { api } from './client';

export const passwordResetApi = {
  /**
   * Cevap hesabin VAR OLUP OLMADIGINI soylemez ve arayuz de sormaz: her
   * durumda ayni onay gosterilir.
   */
  request: (email: string) =>
    api.post<void>('/api/auth/password-reset', { email }).then(() => undefined),

  confirm: (token: string, newPassword: string) =>
    api.post<void>('/api/auth/password-reset/confirm', { token, newPassword })
      .then(() => undefined),
};
