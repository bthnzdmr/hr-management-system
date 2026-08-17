import type { ReactNode } from 'react';
import { Box, Stack, Typography } from '@mui/material';

/**
 * Giris akisindaki sayfalarin ortak kabugu.
 *
 * LoginPage'den cikarildi: parola sifirlama iki sayfa daha getirdi ve ayni
 * duzeni uc kez kopyalamak, birinin geride kalmasi demekti. Ucuncu gercek
 * kullanim gelmeden once cikarilmasi erken olurdu.
 */
export function AuthLayout({ title, description, children }: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    // Kimlik ISIKLA degil, sayfayi ikiye bolen keskin bir kenarla kuruluyor:
    // solda koyu blok, sagda is.
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <Box
        sx={{
          // Dar ekranda tamamen kaybolur: telefonda ekranin yarisini dekora
          // ayirmak, formu ekranin disina itmek demektir.
          display: { xs: 'none', md: 'flex' },
          flexDirection: 'column',
          justifyContent: 'space-between',
          width: '42%',
          maxWidth: 520,
          p: 6,
          bgcolor: '#141A22',
          borderRight: '1px solid',
          borderColor: '#293340',
        }}
      >
        <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center' }}>
          <Box
            sx={{
              width: 30,
              height: 30,
              borderRadius: 1.5,
              display: 'grid',
              placeItems: 'center',
              fontSize: 13,
              fontWeight: 600,
              color: '#141A22',
              bgcolor: '#C68465',
            }}
          >
            HR
          </Box>
          <Typography variant="subtitle1" sx={{ color: '#F8FAFC' }}>
            People
          </Typography>
        </Stack>

        <Box>
          <Typography variant="h3" sx={{ color: '#F8FAFC', fontSize: 34, lineHeight: 1.2 }}>
            One record per person, kept current.
          </Typography>
          <Typography variant="body2" sx={{ color: '#9CA3AF', mt: 2, maxWidth: 360 }}>
            Directory, reporting lines and departures — with every change
            announced to the people who need to know.
          </Typography>
        </Box>

        <Typography variant="caption" sx={{ color: '#5D6875' }}>
          HR Management System
        </Typography>
      </Box>

      <Box sx={{ flexGrow: 1, display: 'grid', placeItems: 'center', px: 3, py: 6 }}>
        <Box sx={{ width: '100%', maxWidth: 380 }}>
          {/* Marka dar ekranda BURADA gorunur; genis ekranda soldaki blokta
              duruyor ve iki kez gosterilmesi gereksiz olurdu. */}
          <Box
            sx={{
              display: { xs: 'grid', md: 'none' },
              width: 38,
              height: 38,
              borderRadius: 1.5,
              placeItems: 'center',
              fontSize: 15,
              fontWeight: 600,
              mb: 3,
              color: 'primary.contrastText',
              bgcolor: 'primary.main',
            }}
          >
            HR
          </Box>

          <Typography variant="h4" component="h1" sx={{ fontSize: 27 }}>
            {title}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
            {description}
          </Typography>

          {children}
        </Box>
      </Box>
    </Box>
  );
}
