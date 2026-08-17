import { useCallback, useState } from 'react';

const STORAGE_KEY = 'hr.navCollapsed';

/**
 * Kenar cubugu daraltilmis mi?
 *
 * Yogunluk anahtariyla ayni gerekce Context yerine kanca: deger yalnizca
 * kabuk tarafindan okunuyor, Context'e koymak tuketicisi olmayan bir yayin
 * kurmak olurdu.
 *
 * Varsayilan ACIK. Daraltilmis bir menu, uygulamayi ilk kez acan birine
 * gezinmeyi ikonlardan tahmin ettirirdi; genisleten kisi bunu bilerek yapar.
 */
export function useNavCollapsed(): { collapsed: boolean; toggle: () => void } {
  // Bilinmeyen bir deger sessizce varsayilana duser; okunamayan tercih,
  // tercihsizlik demektir.
  const [collapsed, setCollapsed] = useState(
    () => localStorage.getItem(STORAGE_KEY) === 'true',
  );

  const toggle = useCallback(() => {
    setCollapsed((current) => {
      const next = !current;
      localStorage.setItem(STORAGE_KEY, String(next));

      return next;
    });
  }, []);

  return { collapsed, toggle };
}
