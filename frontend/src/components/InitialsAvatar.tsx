import { Avatar } from '@mui/material';

interface Props {
  firstName: string;
  lastName: string;
  size?: number;
}

// Ayni kisi her zaman ayni rengi alsin diye ad-soyad uzerinden deterministik
// bir secim yapilir; rastgele olsaydi her render'da renk degisirdi.
const COLORS = ['#0F6E5C', '#7A5AF8', '#B26B00', '#2E7D4F', '#C0392B', '#2A6FB0'];

function colorFor(seed: string): string {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash * 31 + seed.charCodeAt(i)) % 100000;
  }
  return COLORS[hash % COLORS.length];
}

export function InitialsAvatar({ firstName, lastName, size = 36 }: Props) {
  const initials = `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase();

  return (
    <Avatar
      sx={{
        width: size,
        height: size,
        bgcolor: colorFor(`${firstName}${lastName}`),
        fontSize: size * 0.4,
        fontWeight: 600,
      }}
    >
      {initials}
    </Avatar>
  );
}
