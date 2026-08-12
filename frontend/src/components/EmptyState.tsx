import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';

interface Props {
  icon?: ReactNode;
  title: string;
  /** Neden bos oldugu; kullaniciyi tahmin etmeye birakmamak icin. */
  description?: string;
  /** Cikis yolu: filtreyi temizlemek, kayit eklemek, tekrar denemek. */
  action?: ReactNode;
}

/**
 * Bos liste durumu.
 *
 * Bos bir liste, kullanicinin YONLENDIRILMESI gereken andir: "hic kayit yok"
 * ile "filtreye uyan kayit yok" bambaska iki durumdur ve ikincisinde tek
 * dogru cevap filtreyi temizlemektir.
 *
 * Bilesen olmasinin somut sebebi: ayni metin personel listesinde iki kez
 * yaziliydi (tablo ve kart gorunumu) ve ikisi AYRISMISTI -- tabloda
 * "filtreleri temizle" dugmesi vardi, kart gorunumunde yoktu. Yani telefonda
 * bos listeye dusen kullanicinin cikis yolu yoktu.
 */
export function EmptyState({ icon, title, description, action }: Props) {
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 1,
        px: 3,
        py: 6,
        textAlign: 'center',
      }}
    >
      {icon && (
        <Box
          aria-hidden
          sx={{
            display: 'grid',
            placeItems: 'center',
            width: 44,
            height: 44,
            borderRadius: 1.5,
            mb: 0.5,
            color: 'text.secondary',
            bgcolor: 'action.hover',
          }}
        >
          {icon}
        </Box>
      )}

      <Typography variant="subtitle1">{title}</Typography>

      {description && (
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 380 }}>
          {description}
        </Typography>
      )}

      {action && <Box sx={{ mt: 1.5 }}>{action}</Box>}
    </Box>
  );
}
