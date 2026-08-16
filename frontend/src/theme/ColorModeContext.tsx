import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { CssBaseline, ThemeProvider, useMediaQuery } from '@mui/material';
import type { PaletteMode } from '@mui/material';
import { buildTheme } from './theme';

const STORAGE_KEY = 'hr.colorMode';

interface ColorModeContextValue {
  mode: PaletteMode;
  toggle: () => void;
}

const ColorModeContext = createContext<ColorModeContextValue | null>(null);

/**
 * Tema modu Context API ile tasinir.
 *
 * Bu, Context'in dogru kullanim ornegi: SEYREK degisen (kullanici gunde bir
 * kez dokunur) ve HER YERDEN okunan bir deger. Sik degisen liste durumu ise
 * Redux'ta -- Context degistiginde tum tuketiciler yeniden render olur.
 */
export function ColorModeProvider({ children }: { children: ReactNode }) {
  const prefersDark = useMediaQuery('(prefers-color-scheme: dark)');

  // Tip IDDIASI dogrulama degildir: depodaki bozuk bir deger dogrudan temaya
  // gecerdi. Taninmayan tercih, tercihsizlik sayilir -- useDensity ayni sinifi
  // zaten boyle ele aliyor.
  const [stored, setStored] = useState<PaletteMode | null>(() => {
    const value = localStorage.getItem(STORAGE_KEY);
    return value === 'light' || value === 'dark' ? value : null;
  });

  // Kullanici bir secim yapmadiysa isletim sisteminin tercihi izlenir.
  const mode: PaletteMode = stored ?? (prefersDark ? 'dark' : 'light');

  const toggle = useCallback(() => {
    setStored((current) => {
      const next: PaletteMode =
        (current ?? (prefersDark ? 'dark' : 'light')) === 'light' ? 'dark' : 'light';
      localStorage.setItem(STORAGE_KEY, next);
      return next;
    });
  }, [prefersDark]);

  // Tema nesnesi her render'da yeniden uretilirse tum agac yeniden render olur.
  const theme = useMemo(() => buildTheme(mode), [mode]);
  const value = useMemo(() => ({ mode, toggle }), [mode, toggle]);

  return (
    <ColorModeContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        {/* CssBaseline temanin zeminini body'ye uygular; olmadan koyu temada
            sayfa arka plani beyaz kalirdi. */}
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ColorModeContext.Provider>
  );
}

export function useColorMode(): ColorModeContextValue {
  const context = useContext(ColorModeContext);
  if (!context) {
    throw new Error('useColorMode must be used within ColorModeProvider');
  }
  return context;
}
