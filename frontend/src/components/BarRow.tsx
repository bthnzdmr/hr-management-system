import { Box, Typography } from '@mui/material';

interface Props {
  label: string;
  value: number;
  /** Cubugun tam dolu sayildigi deger. */
  max: number;
  /** Sifir degerini soluk gostermek icin. */
  muted?: boolean;
}

/**
 * Tek satirlik yatay cubuk.
 *
 * Grafik kutuphanesi EKLENMEDI: ihtiyacimiz olan sey oranli bir dikdortgen ve
 * bunun icin zaten CSS var. Bagimlilik eklemenin bedeli yalnizca paket boyutu
 * degil, ogrenilecek ikinci bir API ve surdurulecek ikinci bir surumdur.
 */
export function BarRow({ label, value, max, muted = false }: Props) {
  // max sifirsa bolme yapilmaz: 0/0 NaN uretir ve cubuk hic cizilmez.
  const ratio = max > 0 ? Math.max((value / max) * 100, value > 0 ? 2 : 0) : 0;

  return (
    <Box
      sx={{
        display: 'grid',
        // Dar ekranda etiket daralir ama sayi hep okunur kalir.
        gridTemplateColumns: { xs: '6.5rem 1fr 2.5rem', sm: '9rem 1fr 2.5rem' },
        alignItems: 'center',
        gap: 1,
      }}
    >
      <Typography variant="body2" noWrap title={label} sx={{ color: muted ? 'text.secondary' : 'text.primary' }}>
        {label}
      </Typography>

      <Box sx={{ height: 8, borderRadius: 999, bgcolor: 'action.hover', overflow: 'hidden' }}>
        <Box
          sx={{
            height: '100%',
            width: `${ratio}%`,
            borderRadius: 999,
            bgcolor: muted ? 'text.disabled' : 'primary.main',
            transition: 'width 240ms ease',
            '@media (prefers-reduced-motion: reduce)': { transition: 'none' },
          }}
        />
      </Box>

      <Typography
        variant="body2"
        sx={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums', color: 'text.secondary' }}
      >
        {value}
      </Typography>
    </Box>
  );
}
