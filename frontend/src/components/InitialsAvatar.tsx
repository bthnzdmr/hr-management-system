import { Avatar } from '@mui/material';

interface Props {
  firstName: string;
  lastName: string;
  size?: number;
}

/**
 * Avatar zeminleri.
 *
 * Onceki set acik pastellerle koyu tonlari kariştiriyordu; bir liste boyunca
 * alt alta dizildiginde bazi avatarlar parliyor, bazilari kayboluyordu ve
 * goz surekli ayar yapiyordu. Simdi hepsi KOYU: aralarindaki fark yalnizca
 * hue, parlaklik degil. Bu sayede tek bir acik metin rengi yeter ve liste
 * sakin bir ritim tutturur.
 *
 * Tonlar paletin hue ailesinden: lacivert-gri, adacayi, kil, gul ve iki ara
 * ton. Yeni bir renk ailesi eklenmedi.
 */
const AVATAR_TEXT = '#F8FAFC';

const BACKGROUNDS = [
  '#3B4859',
  '#43615C',
  '#8A5238',
  '#7A4B4B',
  '#2F5364',
  '#5A5468',
] as const;

// Ayni kisi her zaman ayni rengi alsin diye ad-soyad uzerinden deterministik
// bir secim yapilir; rastgele olsaydi her render'da renk degisirdi.
function colorFor(seed: string) {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash * 31 + seed.charCodeAt(i)) % 100000;
  }
  return BACKGROUNDS[hash % BACKGROUNDS.length];
}

export function InitialsAvatar({ firstName, lastName, size = 36 }: Props) {
  const initials = `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase();

  return (
    <Avatar
      sx={{
        width: size,
        height: size,
        bgcolor: colorFor(`${firstName}${lastName}`),
        color: AVATAR_TEXT,
        fontSize: size * 0.36,
        fontWeight: 600,
        letterSpacing: '0.01em',
      }}
    >
      {initials}
    </Avatar>
  );
}
