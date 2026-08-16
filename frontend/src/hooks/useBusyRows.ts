import { useCallback, useState } from 'react';

/** Hangi satirlarda islem surdugu; her satir yalnizca kendi kaydini temizler. */
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
