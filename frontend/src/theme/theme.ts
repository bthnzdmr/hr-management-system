import { createTheme } from '@mui/material/styles';
import type { PaletteMode, Theme } from '@mui/material';

/**
 * Tema tek yerde tanimlanir.
 *
 * Renkler bilesenlerin icine dagitilsaydi koyu temaya gecmek her dosyaya
 * dokunmayi gerektirirdi. Burada yalnizca PALET moda gore degisir; olculer,
 * yaziyuzu ve bilesen bicimleri ortaktir.
 */
function palette(mode: PaletteMode) {
  const light = mode === 'light';

  return {
    mode,
    primary: {
      // Varsayilan MUI mavisi yerine koyu bir yesil: kurumsal bir Ik
      // uygulamasinda mavi "her uygulama" gibi durur.
      main: light ? '#0F6E5C' : '#4FC2A8',
      contrastText: light ? '#FFFFFF' : '#06231C',
    },
    secondary: { main: light ? '#7A5AF8' : '#A38BFF' },
    success: { main: light ? '#2E7D4F' : '#6FCF97' },
    warning: { main: light ? '#B26B00' : '#E0A458' },
    error: { main: light ? '#C0392B' : '#EF8073' },
    background: {
      // Saf beyaz yerine hafif kirik bir zemin: kartlar zeminden ayrilsin.
      default: light ? '#F6F7F6' : '#0F1513',
      paper: light ? '#FFFFFF' : '#171F1C',
    },
    text: {
      primary: light ? '#16211E' : '#E7EDEA',
      secondary: light ? '#5A6764' : '#9CABA6',
    },
    divider: light ? '#E2E7E5' : '#28332F',
  };
}

export function buildTheme(mode: PaletteMode): Theme {
  const theme = createTheme({
    palette: palette(mode),

    shape: { borderRadius: 10 },

    typography: {
      fontFamily: '"Inter", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
      h5: { fontWeight: 650, letterSpacing: '-0.01em' },
      h6: { fontWeight: 620, letterSpacing: '-0.01em' },
      subtitle2: { fontWeight: 600 },
      button: { textTransform: 'none', fontWeight: 600 },
    },

    components: {
      // Golge yerine cizgi: kalabalik bir tabloda golgeler gorsel gurultu yapar.
      MuiPaper: {
        defaultProps: { elevation: 0 },
        styleOverrides: {
          root: ({ theme: t }) => ({ border: `1px solid ${t.palette.divider}` }),
        },
      },
      MuiAppBar: {
        defaultProps: { elevation: 0, color: 'inherit' },
        styleOverrides: {
          root: ({ theme: t }) => ({
            borderBottom: `1px solid ${t.palette.divider}`,
            backgroundImage: 'none',
          }),
        },
      },
      MuiTableCell: {
        styleOverrides: {
          head: ({ theme: t }) => ({
            fontWeight: 600,
            fontSize: 13,
            color: t.palette.text.secondary,
            backgroundColor: t.palette.mode === 'light' ? '#F0F3F1' : '#1D2723',
          }),
        },
      },
      MuiButton: { defaultProps: { disableElevation: true } },
      MuiChip: { styleOverrides: { root: { fontWeight: 600 } } },
      MuiTextField: { defaultProps: { size: 'small' } },
    },
  });

  return theme;
}
