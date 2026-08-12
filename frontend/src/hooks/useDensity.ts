import { useCallback, useState } from 'react';

export type Density = 'compact' | 'comfortable';

const STORAGE_KEY = 'hr.tableDensity';

/**
 * Tablo satir yogunlugu.
 *
 * Context DEGIL, kanca: tema modunun aksine bu deger her yerden okunmuyor,
 * yalnizca tabloyu cizen bilesen kullaniyor. Context'e koymak, tuketicisi
 * olmayan bir yayin kurmak olurdu.
 *
 * Yine de localStorage'da kalici: gunde yuz satir tarayan biri tercihini
 * her acilista yeniden yapmak istemez.
 */
export function useDensity(): { density: Density; setDensity: (next: Density) => void } {
  const [density, setStored] = useState<Density>(() => {
    const saved = localStorage.getItem(STORAGE_KEY);
    // Bilinmeyen bir deger (elle duzenlenmis veya eski surumden kalmis)
    // sessizce varsayilana duser; okunamayan tercih, tercihsizlik demektir.
    return saved === 'compact' || saved === 'comfortable' ? saved : 'comfortable';
  });

  const setDensity = useCallback((next: Density) => {
    localStorage.setItem(STORAGE_KEY, next);
    setStored(next);
  }, []);

  return { density, setDensity };
}
