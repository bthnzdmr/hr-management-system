import { Box, Stack, Tooltip, Typography } from '@mui/material';
import type { ManagerLoad as ManagerLoadRow } from '../api/dashboard';

interface Props {
  managers: ManagerLoadRow[];
}

/** En kucuk ve en buyuk daire capi (piksel). */
const MIN_SIZE = 34;
const MAX_SIZE = 76;

/**
 * Yonetici basina tasinan ekip buyuklugu.
 *
 * <p><b>Neden burada balon, org chart'ta agac?</b> Balon gorsellestirmesi AG
 * verisi icin gucludur: cok yonlu, donguli iliskiler. Organizasyon semasi ise
 * bir AGACTIR ve oradaki en degerli bilgi hiyerarsi SEVIYESIDIR -- balonlara
 * konuldugunda "kim kimin ustunde" konum tesadufune kalir, klavyeyle gezinme
 * ve Ctrl+F kaybolur.
 *
 * <p>Burada ise sorulan soru hiyerarsi degil KARSILASTIRMA: "kim kac kisi
 * tasiyor". Daire capi bunu tek bakista verir ve seviye zaten sorulmuyor.
 *
 * <p><b>Kutuphane eklenmedi:</b> ihtiyacimiz olan sey oranli bir daire ve
 * bunun icin zaten CSS var -- Sparkline'daki karar. Daha onemlisi, isim ve
 * sayi GERCEK METIN olarak yaziliyor: bir canvas veya SVG olsaydi ekran
 * okuyucu icin elle aria yazmak, Ctrl+F icinse hicbir sey yapmak gerekirdi.
 */
export function ManagerLoad({ managers }: Props) {
  if (managers.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        Nobody has direct reports yet.
      </Typography>
    );
  }

  const largest = Math.max(...managers.map((m) => m.directReports), 1);

  return (
    <Box
      component="ul"
      sx={{
        listStyle: 'none',
        m: 0,
        p: 0,
        display: 'flex',
        flexWrap: 'wrap',
        gap: 2,
        alignItems: 'flex-end',
      }}
    >
      {managers.map((manager) => {
        // Cap ALANA gore olceklenir, capa gore degil: goz bir dairenin
        // buyuklugunu alanindan okur. Dogrudan capla olceklemek iki katini
        // dort kat gibi gosterirdi.
        const ratio = manager.directReports / largest;
        const size = MIN_SIZE + (MAX_SIZE - MIN_SIZE) * Math.sqrt(ratio);
        const name = `${manager.firstName} ${manager.lastName}`;

        return (
          <Box
            component="li"
            key={manager.employeeId}
            sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 92 }}
          >
            <Tooltip title={`${name} · ${manager.departmentName}`}>
              <Box
                sx={{
                  width: size,
                  height: size,
                  borderRadius: '50%',
                  display: 'grid',
                  placeItems: 'center',
                  fontVariantNumeric: 'tabular-nums',
                  fontWeight: 600,
                  // Yogunluk da yuku anlatir ama TEK BASINA degil: sayi
                  // dairenin icinde yaziyor, yani bilgi renkten bagimsiz.
                  bgcolor: (t) => `color-mix(in srgb, ${t.palette.primary.main} ${
                    30 + Math.round(ratio * 45)}%, transparent)`,
                  color: 'text.primary',
                  fontSize: size * 0.32,
                }}
              >
                {manager.directReports}
              </Box>
            </Tooltip>

            {/* Isim GERCEK METIN: ekran okuyucu okur, Ctrl+F bulur. */}
            <Typography
              variant="caption"
              align="center"
              sx={{ mt: 0.75, lineHeight: 1.25, wordBreak: 'break-word' }}
            >
              {name}
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ fontSize: 10 }}>
              {manager.departmentName}
            </Typography>
          </Box>
        );
      })}
    </Box>
  );
}

/** Panelde ozetin yaninda duran kucuk aciklama. */
export function ManagerLoadLegend({ managers }: Props) {
  if (managers.length === 0) return null;

  const total = managers.reduce((sum, m) => sum + m.directReports, 0);
  const largest = managers[0];

  return (
    <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', mb: 1.5 }}>
      <Typography variant="caption" color="text.secondary">
        {managers.length} {managers.length === 1 ? 'manager' : 'managers'} carry {total}{' '}
        {total === 1 ? 'person' : 'people'}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        · largest team: {largest.firstName} {largest.lastName} ({largest.directReports})
      </Typography>
    </Stack>
  );
}
