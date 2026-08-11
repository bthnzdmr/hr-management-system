import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { Box, Button, Paper, Stack, Typography } from '@mui/material';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlineOutlined';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
}

/**
 * Render sirasinda firlayan hatalari yakalar.
 *
 * React 16'dan beri yakalanmayan bir render hatasi TUM agaci soker: kullanici
 * bembeyaz bir sayfa gorur, ne oldugunu anlamaz ve yenilemekten baska yolu
 * kalmaz. Sinir, hatayi kendi altinda tutar.
 *
 * Bunun sinif bileseni olmasi zorunludur: hata yakalama icin kanca (hook)
 * karsiligi YOKTUR -- componentDidCatch tek yoldur.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Gercek bir sistemde buradan hata izleme servisine gonderilir.
    // Kullaniciya yiginin kendisi GOSTERILMEZ: ic detay sizdirmak, sunucuda
    // istisna mesajini istemciye dondurmemekle ayni sebeple yanlistir.
    console.error('Unhandled render error', error, info.componentStack);
  }

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    return (
      <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '100vh', p: 3 }}>
        <Paper sx={{ p: 4, maxWidth: 480, textAlign: 'center' }}>
          <Stack spacing={2} sx={{ alignItems: 'center' }}>
            <ErrorOutlineIcon color="error" sx={{ fontSize: 48 }} />
            <Typography variant="h6">Something went wrong</Typography>
            <Typography variant="body2" color="text.secondary">
              The page could not be displayed. Reloading usually helps.
            </Typography>
            {/* Durumu sifirlamak yetmez: hataya yol acan veri hala bellekte
                olabilir, bu yuzden tam yeniden yukleme yapilir. */}
            <Button variant="contained" onClick={() => window.location.reload()}>
              Reload the page
            </Button>
          </Stack>
        </Paper>
      </Box>
    );
  }
}
