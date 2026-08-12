import { Avatar } from '@mui/material';

interface Props {
  firstName: string;
  lastName: string;
  size?: number;
}

/**
 * Renkler PALETIN icinden secilir ve her biri KENDI metin rengiyle eslesir.
 *
 * Tek bir beyaz metin rengi kullanilsaydi acik kil ve gul tonlarinda bas
 * harfler okunmazdi -- kontrast zeminle birlikte secilmesi gereken bir sey,
 * sonradan uzerine konan degil.
 */
const COLORS: { bg: string; fg: string }[] = [
  { bg: '#C8937E', fg: '#1E2631' },
  { bg: '#79A29E', fg: '#1E2631' },
  { bg: '#C48B8B', fg: '#1E2631' },
  { bg: '#3B4859', fg: '#F8FAFC' },
  { bg: '#A2664D', fg: '#F8FAFC' },
  { bg: '#4E756F', fg: '#F8FAFC' },
];

// Ayni kisi her zaman ayni rengi alsin diye ad-soyad uzerinden deterministik
// bir secim yapilir; rastgele olsaydi her render'da renk degisirdi.
function colorFor(seed: string) {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash * 31 + seed.charCodeAt(i)) % 100000;
  }
  return COLORS[hash % COLORS.length];
}

export function InitialsAvatar({ firstName, lastName, size = 36 }: Props) {
  const initials = `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase();
  const { bg, fg } = colorFor(`${firstName}${lastName}`);

  return (
    <Avatar
      sx={{
        width: size,
        height: size,
        bgcolor: bg,
        color: fg,
        fontSize: size * 0.38,
        fontWeight: 680,
        letterSpacing: '0.02em',
      }}
    >
      {initials}
    </Avatar>
  );
}
