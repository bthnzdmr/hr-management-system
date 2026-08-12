import { createTheme, alpha } from '@mui/material/styles';
import type { PaletteMode, Theme } from '@mui/material';

/**
 * Tema tek yerde tanimlanir.
 *
 * Renkler bilesenlerin icine dagitilsaydi koyu temaya gecmek her dosyaya
 * dokunmayi gerektirirdi. Burada yalnizca PALET moda gore degisir; olculer,
 * yaziyuzu ve bilesen bicimleri ortaktir.
 */

/** Baslik yuzu. Degisken surum: tek dosya, butun agirliklar. */
const DISPLAY = '"Bricolage Grotesque Variable", "Inter Variable", system-ui, sans-serif';

/**
 * Verilen palet. Koyu tonlar zemin, sicak tonlar vurgu.
 *
 * Bu set KOYU tema icin secilmis: uc lacivert-gri yuzey, bir acik metin, bir
 * sonuk gri ve uc sicak vurgu. Acik temada aynen kullanilamaz -- SAGE ve CLAY
 * beyaz uzerinde metin olarak okunmaz, MUTED ise kontrast esigini gecmez.
 * Bu yuzden acik tema icin ayni hue'lardan KOYULASTIRILMIS turevler uretildi.
 */
const BASE = {
  ink900: '#1E2631',
  ink800: '#293340',
  ink700: '#3B4859',
  clay: '#C8937E',
  rose: '#C48B8B',
  /**
   * Verilen adacayi #709995'ti; kart zemininde (#293340) kontrasti 4.07:1
   * olctuk ve WCAG'in 4.5 esigini gecmiyordu. Hue korunup parlaklik bir
   * kademe artirildi: 4.55:1. Gozle "yeterince okunuyor" demek yerine
   * olculdu -- kontrast tahmin edilecek bir sey degil.
   */
  sage: '#79A29E',
  paper: '#F8FAFC',
  grey: '#9CA3AF',
} as const;

/**
 * Acik tema icin koyulastirilmis turevler.
 *
 * Beyaz zeminde CLAY'in kontrasti ~2:1; uzerine beyaz metin konan bir dugme
 * olarak okunmaz. Ayni hue korunup parlaklik dusuruldu.
 */
const DARKENED = {
  clay: '#A2664D',
  sage: '#4E756F',
  rose: '#A15E5E',
  muted: '#5D6875',
  line: '#DCE2E9',
  surface: '#EEF2F6',
} as const;

/**
 * Palette UYARI rengi yok.
 *
 * CLAY hem birincil eylem hem uyari olsaydi "dikkat" kutucugu bir dugme gibi
 * gorunurdu. Anlamsal renk vurgu renginden ayri olmali; palet ailesine uyan
 * bir kehribar turetildi.
 */
const AMBER = { dark: '#D9A56A', light: '#8A6220' } as const;

function palette(mode: PaletteMode) {
  const light = mode === 'light';

  return {
    mode,
    primary: {
      main: light ? DARKENED.clay : BASE.clay,
      contrastText: light ? '#FFFFFF' : BASE.ink900,
    },
    secondary: { main: light ? DARKENED.sage : BASE.sage },
    success: { main: light ? DARKENED.sage : BASE.sage },
    warning: { main: light ? AMBER.light : AMBER.dark },
    error: { main: light ? DARKENED.rose : BASE.rose },
    background: {
      default: light ? BASE.paper : BASE.ink900,
      paper: light ? '#FFFFFF' : BASE.ink800,
    },
    text: {
      primary: light ? BASE.ink900 : BASE.paper,
      secondary: light ? DARKENED.muted : BASE.grey,
    },
    divider: light ? DARKENED.line : BASE.ink700,
  };
}

export function buildTheme(mode: PaletteMode): Theme {
  const light = mode === 'light';

  // Yukseltilmis yuzey: koyu temada golge gorunmez, bu yuzden derinlik ISIKLA
  // anlatilir -- ust katman bir ton acilir. Acik temada tam tersi: yuzey beyaz
  // kalir, derinlik yumusak golgeyle verilir.
  const raised = light ? DARKENED.surface : BASE.ink700;

  const softShadow = light
    ? '0 1px 2px rgba(30, 38, 49, 0.06), 0 8px 24px -12px rgba(30, 38, 49, 0.18)'
    : '0 1px 2px rgba(0, 0, 0, 0.4), 0 12px 32px -16px rgba(0, 0, 0, 0.6)';

  const liftedShadow = light
    ? '0 2px 4px rgba(30, 38, 49, 0.08), 0 16px 32px -16px rgba(30, 38, 49, 0.24)'
    : '0 2px 6px rgba(0, 0, 0, 0.5), 0 20px 40px -20px rgba(0, 0, 0, 0.7)';

  return createTheme({
    palette: palette(mode),

    shape: { borderRadius: 12 },

    typography: {
      // Govde: Inter. GERCEKTEN yuklenir -- daha once burada yazili olmasina
      // ragmen hicbir yerde import edilmiyordu ve sessizce system-ui'ye
      // dusuyordu. Referans etmek yuklemek degildir.
      fontFamily: '"Inter Variable", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',

      // Basliklar ve buyuk sayilar ayri bir yuzle: tek yazi tipiyle hiyerarsi
      // yalnizca boyut ve agirlikla kurulur ve arayuz duz gorunur.
      h1: { fontFamily: DISPLAY, fontWeight: 700, letterSpacing: '-0.03em' },
      h2: { fontFamily: DISPLAY, fontWeight: 700, letterSpacing: '-0.03em' },
      h3: { fontFamily: DISPLAY, fontWeight: 700, letterSpacing: '-0.025em' },
      h4: { fontFamily: DISPLAY, fontWeight: 650, letterSpacing: '-0.025em' },
      h5: { fontFamily: DISPLAY, fontWeight: 650, letterSpacing: '-0.02em' },
      h6: { fontFamily: DISPLAY, fontWeight: 640, letterSpacing: '-0.015em' },
      subtitle1: { fontWeight: 600, letterSpacing: '-0.01em' },
      subtitle2: { fontWeight: 640, letterSpacing: '0.01em' },
      button: { textTransform: 'none', fontWeight: 620, letterSpacing: '0.01em' },
      overline: { fontWeight: 700, letterSpacing: '0.1em' },
    },

    components: {
      MuiCssBaseline: {
        styleOverrides: {
          // Sayilarin sutun halinde hizalanmasi icin: tabular rakamlar
          // olmadan "111" ile "999" farkli genislikte olur ve tablo titrer.
          'th, td, .MuiChip-label': { fontVariantNumeric: 'tabular-nums' },
          // Hareketi kapatan kullaniciya hareket dayatilmaz.
          '@media (prefers-reduced-motion: reduce)': {
            '*': { animationDuration: '0.01ms !important', transitionDuration: '0.01ms !important' },
          },
        },
      },

      MuiPaper: {
        defaultProps: { elevation: 0 },
        styleOverrides: {
          root: ({ theme: t }) => ({
            border: `1px solid ${t.palette.divider}`,
            backgroundImage: 'none',
            boxShadow: softShadow,
          }),
        },
      },

      MuiAppBar: {
        defaultProps: { elevation: 0, color: 'inherit' },
        styleOverrides: {
          root: ({ theme: t }) => ({
            borderBottom: `1px solid ${t.palette.divider}`,
            backgroundImage: 'none',
            boxShadow: 'none',
            // Cam etkisi: icerik ust cubugun altindan kayarken belli olur ve
            // katman hissi olusur.
            backgroundColor: alpha(t.palette.background.default, 0.82),
            backdropFilter: 'blur(12px)',
          }),
        },
      },

      MuiDrawer: {
        styleOverrides: {
          paper: ({ theme: t }) => ({
            border: 'none',
            borderRight: `1px solid ${t.palette.divider}`,
            backgroundColor: light ? '#FFFFFF' : BASE.ink900,
            boxShadow: 'none',
          }),
        },
      },

      MuiListItemButton: {
        styleOverrides: {
          root: ({ theme: t }) => ({
            borderRadius: 10,
            position: 'relative',
            transition: 'background-color 160ms ease, color 160ms ease',
            '&.Mui-selected': {
              backgroundColor: alpha(t.palette.primary.main, light ? 0.12 : 0.18),
              color: t.palette.primary.main,
              // Sol cubuk: secili ogeyi zeminden ayirmanin en sessiz yolu.
              '&::before': {
                content: '""',
                position: 'absolute',
                left: 0,
                top: '22%',
                bottom: '22%',
                width: 3,
                borderRadius: 3,
                backgroundColor: t.palette.primary.main,
              },
              '&:hover': {
                backgroundColor: alpha(t.palette.primary.main, light ? 0.18 : 0.24),
              },
            },
          }),
        },
      },

      MuiTableCell: {
        styleOverrides: {
          head: ({ theme: t }) => ({
            fontWeight: 640,
            fontSize: 12,
            letterSpacing: '0.06em',
            textTransform: 'uppercase',
            color: t.palette.text.secondary,
            backgroundColor: raised,
            borderBottom: `1px solid ${t.palette.divider}`,
          }),
          root: ({ theme: t }) => ({ borderBottom: `1px solid ${t.palette.divider}` }),
        },
      },

      MuiTableRow: {
        styleOverrides: {
          root: ({ theme: t }) => ({
            transition: 'background-color 140ms ease',
            '&:hover': { backgroundColor: alpha(t.palette.primary.main, 0.05) },
            '&:last-child td': { borderBottom: 0 },
          }),
        },
      },

      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: { borderRadius: 10 },
          contained: {
            boxShadow: 'none',
            transition: 'transform 140ms ease, box-shadow 140ms ease, background-color 140ms ease',
            '&:hover': { boxShadow: liftedShadow, transform: 'translateY(-1px)' },
            '&:active': { transform: 'translateY(0)' },
          },
        },
      },

      MuiChip: {
        styleOverrides: {
          root: { fontWeight: 620, borderRadius: 8 },
          // Dolu renk yerine TONLU zemin: bir tabloda yan yana duran dolu
          // rozetler gozu yorar ve hepsi ayni derecede acil gorunur.
          filled: ({ theme: t, ownerState }) => {
            const color = ownerState.color;
            if (!color || color === 'default') {
              return { backgroundColor: raised, color: t.palette.text.secondary };
            }
            return {
              backgroundColor: alpha(t.palette[color].main, light ? 0.14 : 0.2),
              color: light ? t.palette[color].main : t.palette[color].main,
              border: `1px solid ${alpha(t.palette[color].main, 0.28)}`,
            };
          },
        },
      },

      MuiTextField: { defaultProps: { size: 'small' } },

      MuiOutlinedInput: {
        styleOverrides: {
          root: ({ theme: t }) => ({
            borderRadius: 10,
            transition: 'box-shadow 140ms ease',
            '&.Mui-focused': {
              boxShadow: `0 0 0 3px ${alpha(t.palette.primary.main, 0.18)}`,
            },
          }),
        },
      },

      MuiSkeleton: {
        styleOverrides: {
          root: { backgroundColor: raised },
        },
      },

      MuiTooltip: {
        styleOverrides: {
          tooltip: ({ theme: t }) => ({
            backgroundColor: light ? BASE.ink900 : BASE.ink700,
            color: BASE.paper,
            fontSize: 12,
            borderRadius: 8,
            border: `1px solid ${t.palette.divider}`,
          }),
        },
      },
    },
  });
}

/** Karti kaldiran hover efekti; panel kutucuklari kullanir. */
export const HOVER_LIFT = {
  transition: 'transform 180ms ease, box-shadow 180ms ease, border-color 180ms ease',
  '&:hover': { transform: 'translateY(-2px)' },
} as const;
