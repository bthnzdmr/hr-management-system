import { useCallback, useState } from 'react';

/**
 * Hangi satirlarda islem surdugu.
 *
 * Uc sayfa da tek yuvali bir bayrak (`busyId`) tutuyordu ve bu, ayni anda iki
 * satirda islem baslatildiginda kiriliyordu: once biten islem bayragi
 * temizleyip DIGER satirin dugmelerini ucus halindeyken tekrar aciyordu --
 * cift gonderim mumkun hale geliyordu.
 *
 * Kume tutmak bunu yapisal olarak imkansiz kilar: her satir kendi kaydini
 * birakir ve yalnizca kendisini temizler.
 */
export function useBusyRows() {
  const [busy, setBusy] = useState<ReadonlySet<number>>(() => new Set());

  const start = useCallback((id: number) => {
    setBusy((current) => new Set(current).add(id));
  }, []);

  const finish = useCallback((id: number) => {
    setBusy((current) => {
      const next = new Set(current);
      next.delete(id);
      return next;
    });
  }, []);

  const isBusy = useCallback((id: number | null | undefined) =>
    id != null && busy.has(id), [busy]);

  return { start, finish, isBusy };
}
