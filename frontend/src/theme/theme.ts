import { createTheme, alpha } from '@mui/material/styles';
import type { PaletteMode, Theme } from '@mui/material';

/**
 * Tema tek yerde tanimlanir.
 *
 * Renkler bilesenlerin icine dagitilsaydi koyu temaya gecmek her dosyaya
 * dokunmayi gerektirirdi. Burada yalnizca PALET moda gore degisir; olculer,
 * yaziyuzu ve bilesen bicimleri ortaktir.
 */

/**
 * Baslik yuzu: Instrument Sans.
 *
 * Onceki secim Bricolage Grotesque'ti; kalin, oynak, "yaratici ajans" yuzu.
 * Kurumsal bir panelde baslik yuzu dikkat cekmemeli, HIYERARSI kurmali.
 * Instrument Sans dar ve sakin; sikistirilmis harf arasiyla basliklara
 * otorite verir, kisilik katmaya calismaz.
 */
const DISPLAY = '"Instrument Sans Variable", "Inter Variable", system-ui, sans-serif';

/**
 * Verilen palet.
 *
 * ONEMLI: Bu renkler bir kademe YUKARI kaydirildi. Once #1E2631 sayfanin
 * zeminiydi ve kartlar onun uzerinde bir ton aciliyordu; sonuc yumusak ve
 * "ic ferah" bir goruntuydu. Simdi #1E2631 KART yuzeyi, altina daha derin
 * bir zemin (#141A22) girdi. Ayni renkler, daha derin bir yigin.
 */
const BASE = {
  /** Sayfanin zemini. Verilen sette yoktu; ayni hue'nun daha derini. */
  ground: '#141A22',
  ink900: '#1E2631',
  ink800: '#293340',
  ink700: '#3B4859',
  paper: '#F8FAFC',
  grey: '#9CA3AF',
} as const;

/**
 * Vurgular. Verilen kil (#C8937E) ve gul (#C48B8B) pastel tonlardi; yan yana
 * duran uc pastel ciddi bir arayuzde sekerleme paleti gibi okunur.
 *
 * Iki sey degisti:
 * 1. Kil bir kademe DOYURULDU ve koyulastirildi (#C68465) -- seftali degil,
 *    yanik toprak. Ayni hue, farkli karakter.
 * 2. Gul ve adacayi artik VURGU degil; yalnizca anlamsal renk (hata/basari).
 *    Ciddiyet, vurgu renginin tonundan cok KAPLADIGI ALANDAN gelir.
 */
const ACCENT = {
  clay: '#C68465',
  sage: '#6E9C97',
  rose: '#C27C7C',
  amber: '#C99A54',
} as const;

/**
 * Acik tema turevleri.
 *
 * Beyaz zeminde kil ~2:1 kontrast verir ve uzerine beyaz metin konan bir
 * dugme olarak okunmaz. Ayni hue korunup parlaklik dusuruldu.
 */
const LIGHT = {
  ground: '#F4F6F8',
  surface: '#EDF1F5',
  line: '#DCE2E9',
  muted: '#59646F',
  clay: '#985438',
  sage: '#3F6B66',
  rose: '#9A4F4F',
  amber: '#7A5718',
} as const;

function palette(mode: PaletteMode) {
  const light = mode === 'light';

  return {
    mode,
    primary: {
      main: light ? LIGHT.clay : ACCENT.clay,
      contrastText: light ? '#FFFFFF' : BASE.ground,
    },
    secondary: { main: light ? LIGHT.sage : ACCENT.sage },
    success: { main: light ? LIGHT.sage : ACCENT.sage },
    warning: { main: light ? LIGHT.amber : ACCENT.amber },
    error: { main: light ? LIGHT.rose : ACCENT.rose },
    background: {
      default: light ? LIGHT.ground : BASE.ground,
      paper: light ? '#FFFFFF' : BASE.ink900,
    },
    text: {
      primary: light ? '#161C24' : BASE.paper,
      secondary: light ? LIGHT.muted : BASE.grey,
    },
    divider: light ? LIGHT.line : BASE.ink800,
  };
}

export function buildTheme(mode: PaletteMode): Theme {
  const light = mode === 'light';

  // Yukseltilmis yuzey: koyu temada golge gorunmez, bu yuzden derinlik ISIKLA
  // anlatilir -- ust katman bir ton acilir. Acik temada tam tersi: yuzey beyaz
  // kalir, derinlik cizgiyle verilir.
  const raised = light ? LIGHT.surface : BASE.ink800;

  // Golge KISIK. Onceki tema 32px yayilan yumusak golgeler kullaniyordu ve
  // kartlar yuzeyden kopup yuzuyordu; yumusaklik hissinin kaynaklarindan
  // biriydi. Ciddi arayuzde derinligi CIZGI tasir, golge yalnizca fisildar.
  const softShadow = light ? '0 1px 2px rgba(20, 26, 34, 0.05)' : 'none';

  return createTheme({
    palette: palette(mode),

    // 12 yuvarlakti ve yuvarlaklik yumusaklik demek. 8 hala modern, ama
    // kenarlar artik kendini belli ediyor.
    shape: { borderRadius: 8 },

    typography: {
      // Govde: Inter. GERCEKTEN yuklenir -- daha once burada yazili olmasina
      // ragmen hicbir yerde import edilmiyordu ve sessizce system-ui'ye
      // dusuyordu. Referans etmek yuklemek degildir.
      fontFamily: '"Inter Variable", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',

      // Agirliklar bir kademe DUSTU (700 -> 600). Cok kalin baslik bagirir;
      // otorite kalinliktan degil, siki harf arasindan ve bosluktan gelir.
      h1: { fontFamily: DISPLAY, fontWeight: 600, letterSpacing: '-0.025em' },
      h2: { fontFamily: DISPLAY, fontWeight: 600, letterSpacing: '-0.025em' },
      h3: { fontFamily: DISPLAY, fontWeight: 600, letterSpacing: '-0.022em' },
      h4: { fontFamily: DISPLAY, fontWeight: 600, letterSpacing: '-0.022em' },
      h5: { fontFamily: DISPLAY, fontWeight: 600, letterSpacing: '-0.018em' },
      h6: { fontFamily: DISPLAY, fontWeight: 600, letterSpacing: '-0.014em' },
      subtitle1: { fontWeight: 600, letterSpacing: '-0.01em' },
      subtitle2: { fontWeight: 600, letterSpacing: '0' },
      button: { textTransform: 'none', fontWeight: 560, letterSpacing: '0' },
      overline: { fontWeight: 620, letterSpacing: '0.09em' },
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
            backgroundColor: alpha(t.palette.background.default, 0.85),
            backdropFilter: 'blur(12px)',
          }),
        },
      },

      MuiDrawer: {
        styleOverrides: {
          paper: ({ theme: t }) => ({
            border: 'none',
            borderRight: `1px solid ${t.palette.divider}`,
            // Kenar cubugu icerikten bir ton AYRILIR: koyu temada daha derin,
            // acik temada beyaz. Iki bolgeyi ayiran sey yalnizca cizgi olsaydi
            // sayfa tek parca bir duvar gibi okunurdu.
            backgroundColor: light ? '#FFFFFF' : BASE.ink900,
            backgroundImage: 'none',
          }),
        },
      },

      MuiListItemButton: {
        styleOverrides: {
          root: ({ theme: t }) => ({
            borderRadius: 6,
            position: 'relative',
            transition: 'background-color 140ms ease, color 140ms ease',
            '&.Mui-selected': {
              // Tonlu hap KALDIRILDI. Secili oge artik zeminden ayrilir ve
              // vurguyu yalnizca ince sol cubuk tasir -- vurgu renginin
              // kapladigi alan kucuk kaldikca arayuz ciddi kalir.
              backgroundColor: raised,
              color: t.palette.text.primary,
              '&::before': {
                content: '""',
                position: 'absolute',
                left: 0,
                top: 6,
                bottom: 6,
                width: 2,
                backgroundColor: t.palette.primary.main,
              },
              '&:hover': { backgroundColor: raised },
            },
          }),
        },
      },

      MuiTableCell: {
        styleOverrides: {
          head: ({ theme: t }) => ({
            fontWeight: 600,
            fontSize: 11,
            letterSpacing: '0.07em',
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
          root: () => ({
            transition: 'background-color 120ms ease',
            // Hover'da vurgu tonu degil NOTR bir ton: her satirin uzerine
            // gelindiginde turuncu parlamasi arayuzu oyuncak gibi gosterirdi.
            '&:hover': { backgroundColor: raised },
            '&:last-child td': { borderBottom: 0 },
          }),
        },
      },

      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: { borderRadius: 6 },
          // Zipla-kalk efekti kaldirildi: dugmenin havalanmasi oyun arayuzu
          // dilidir. Geri bildirim renk koyulasmasiyla verilir.
          contained: { boxShadow: 'none', '&:hover': { boxShadow: 'none' } },
        },
      },

      MuiChip: {
        styleOverrides: {
          root: { fontWeight: 560, borderRadius: 5, fontSize: 12 },
          // Dolu renk yerine TONLU zemin: bir tabloda yan yana duran dolu
          // rozetler gozu yorar ve hepsi ayni derecede acil gorunur.
          filled: ({ theme: t, ownerState }) => {
            const color = ownerState.color;
            if (!color || color === 'default') {
              return { backgroundColor: raised, color: t.palette.text.secondary };
            }
            return {
              backgroundColor: alpha(t.palette[color].main, light ? 0.1 : 0.16),
              color: t.palette[color].main,
            };
          },
        },
      },

      MuiTextField: { defaultProps: { size: 'small' } },

      MuiOutlinedInput: {
        styleOverrides: {
          root: ({ theme: t }) => ({
            borderRadius: 6,
            transition: 'box-shadow 120ms ease',
            '&.Mui-focused': {
              boxShadow: `0 0 0 3px ${alpha(t.palette.primary.main, 0.16)}`,
            },
          }),
        },
      },

      MuiSkeleton: {
        styleOverrides: { root: { backgroundColor: raised } },
      },

      MuiTooltip: {
        styleOverrides: {
          tooltip: ({ theme: t }) => ({
            backgroundColor: light ? '#161C24' : BASE.ink700,
            color: BASE.paper,
            fontSize: 12,
            borderRadius: 6,
            border: `1px solid ${t.palette.divider}`,
          }),
        },
      },
    },
  });
}

/**
 * Kart hover'i.
 *
 * Onceki hali karti 2px yukari KALDIRIYORDU; her kutucugun zipladigi bir
 * panel ciddi durmaz. Simdi yalnizca kenar cizgisi belirginlesir -- hareket
 * yok, ama kartin tiklanabilir oldugu yine anlasilir.
 */
export const HOVER_LIFT = {
  transition: 'border-color 140ms ease, background-color 140ms ease',
} as const;
