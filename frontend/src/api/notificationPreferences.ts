import { api } from './client';

/** Sunucudaki `NotificationKind` ile ayni kume; ikisi birlikte degisir. */
export type NotificationKind = 'LEAVE_REQUEST' | 'LEAVE_DECISION';

export interface NotificationPreferenceItem {
  kind: NotificationKind;
  /**
   * Gorunen ad SUNUCUDAN geliyor.
   *
   * Arayuzde ayrica tutulsaydi ayni metin iki yerde yasar ve biri zamanla
   * geride kalirdi -- bu projede dort kez olculmus bir hata.
   */
  label: string;
  enabled: boolean;
}

export interface NotificationPreferences {
  items: NotificationPreferenceItem[];
}

export const notificationPreferenceApi = {
  mine: () =>
    api.get<NotificationPreferences>('/api/notification-preferences/me').then((r) => r.data),

  /**
   * ACIK olanlarin TAMAMI gonderilir, tek tek "sustur/ac" degil.
   *
   * Artimli bir uc olsaydi iki es zamanli istek birbirinin uzerine yazabilir
   * ve kimsenin istemedigi bir ara duruma dusulebilirdi.
   */
  replace: (enabled: NotificationKind[]) =>
    api
      .put<NotificationPreferences>('/api/notification-preferences/me', { enabled })
      .then((r) => r.data),
};
