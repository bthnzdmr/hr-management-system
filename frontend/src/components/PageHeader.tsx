import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';

interface Props {
  /** Sayfanin bagli oldugu bolum; kullanicinin nerede oldugunu soyler. */
  eyebrow?: string;
  title: string;
  description?: ReactNode;
  /** Sagda duran eylemler; yetki kontrolu cagiran sayfaya aittir. */
  actions?: ReactNode;
  /**
   * Alttaki ayrac cizgisi.
   *
   * Liste sayfalarinda basligi icerikten ayirmasi gerekiyor, ama tuvali olan
   * bir sayfada cerceveli panellerin hemen ustune bir cizgi daha binince sayfa
   * ust uste seritlere bolunmus gorunuyor.
   */
  divider?: boolean;
}

/**
 * Sayfa basligi.
 *
 * Bes sayfa ayni on iki satirlik blogu kopyalamisti; biri degistiginde
 * digerleri geride kaliyordu. Tek yerde toplanmasi yalnizca tekrari
 * kaldirmakla kalmaz, sayfa basliklarinin ayni RITIMDE olmasini yapisal
 * olarak garanti eder -- tipografik hiyerarsi tesadufe birakilmaz.
 *
 * Alttaki ince cizgi basligi icerikten ayirir: baslik ile ilk kart arasinda
 * yalnizca bosluk olsaydi sayfa nerede baslayip nerede devam ettigini
 * soylemezdi.
 */
export function PageHeader({ eyebrow, title, description, actions, divider = true }: Props) {
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: { xs: 'column', sm: 'row' },
        alignItems: { xs: 'stretch', sm: 'flex-end' },
        justifyContent: 'space-between',
        gap: 2,
        pb: divider ? 2.5 : 0,
        borderBottom: divider ? 1 : 0,
        borderColor: 'divider',
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        {eyebrow && (
          <Typography
            variant="overline"
            color="text.secondary"
            sx={{ fontSize: 11, display: 'block', mb: 0.25 }}
          >
            {eyebrow}
          </Typography>
        )}
        <Typography variant="h4" component="h1" sx={{ fontSize: { xs: 24, md: 28 } }}>
          {title}
        </Typography>
        {description && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {description}
          </Typography>
        )}
      </Box>

      {actions && (
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', flexShrink: 0 }}>{actions}</Box>
      )}
    </Box>
  );
}
