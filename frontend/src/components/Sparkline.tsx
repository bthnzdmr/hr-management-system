import { Box } from '@mui/material';

interface Props {
  /** Aylik degerler, eskiden yeniye sirali. */
  values: number[];
  /** Cizgi rengi; kutucugun tonuyla ayni olur. */
  color: string;
  label: string;
  width?: number;
  height?: number;
}

/**
 * Kutucuk ici mini egri.
 *
 * Kutuphane EKLENMEDI: ihtiyacimiz olan sey bir polyline ve bunun icin zaten
 * SVG var. Bagimlilik eklemenin bedeli yalnizca paket boyutu degil,
 * ogrenilecek ikinci bir API ve surdurulecek ikinci bir surumdur.
 *
 * Neden gerekli: "bu ay 7 kisi ayrildi" tek basina anlamsizdir -- 7'nin
 * yukselis mi dusus mu oldugu asil bilgidir. Sayinin yanindaki egri o baglami
 * tek bakista verir.
 */
export function Sparkline({ values, color, label, width = 72, height = 24 }: Props) {
  if (values.length < 2) return null;

  const max = Math.max(...values);
  const min = Math.min(...values);
  // Duz bir seri (hepsi ayni) sifira bolunmeye yol acardi; o durumda cizgi
  // ortadan gecer -- "degisim yok" da bir bulgudur, bos alan degil.
  const span = max - min || 1;

  const step = width / (values.length - 1);
  const points = values.map((value, index) => {
    const x = index * step;
    // SVG'de y asagi dogru buyur; grafigin tepesi kucuk y demektir.
    const y = height - ((value - min) / span) * height;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });

  const last = points[points.length - 1].split(',');

  return (
    <Box
      component="svg"
      viewBox={`0 0 ${width} ${height + 4}`}
      width={width}
      height={height + 4}
      role="img"
      // Egri DEKOR DEGIL, veri: erisilebilir bir adi olmali. Cizgiyi
      // goremeyen biri de son degeri ve yonu duyabilmeli.
      aria-label={label}
      sx={{ display: 'block', overflow: 'visible' }}
    >
      <polyline
        points={points.join(' ')}
        fill="none"
        stroke={color}
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
        opacity={0.75}
      />
      {/* Son nokta vurgulanir: goz once "simdi nerede" sorusunu sorar. */}
      <circle cx={last[0]} cy={last[1]} r={2} fill={color} />
    </Box>
  );
}
