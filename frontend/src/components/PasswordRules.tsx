import { Box, Typography } from '@mui/material';

/**
 * Sunucudaki asgari uzunluk.
 *
 * Iki sayfa bunu AYRI AYRI tanimliyordu (`MIN_PASSWORD_LENGTH` ve `MIN_LENGTH`)
 * ve ikisi de yalnizca uzunluktan bahsediyordu. Ayni deger iki yerde
 * yasadiginda biri zamanla geride kalir -- bu projede defalarca olculdu.
 */
export const MIN_PASSWORD_LENGTH = 12;

/**
 * Kurallar KULLANICI YAZMADAN once gosterilir.
 *
 * Gorunmeyen bir kural, korlemesine carpilan bir kuraldir: kullanici parolayi
 * yazar, gonderir, reddedilir ve neden reddedildigini ancak deneyerek ogrenir.
 * Ayni gerekceyle arama kutusundaki kisayol da bir rozetle gosteriliyor.
 *
 * Liste sunucudaki `PasswordPolicy` ile AYNI kurallari anlatir; ikisi birlikte
 * degistirilmeli. Arayuz kurali UYGULAMAZ, yalnizca duyurur -- dogrulama
 * sunucudadir ve tek gecerli yer orasidir.
 */
export function PasswordRules() {
  const rules = [
    `At least ${MIN_PASSWORD_LENGTH} characters — length matters more than symbols`,
    'No common word such as "password" or "qwerty"',
    'Not your email address',
    'No long run such as "abcdef" or "123456"',
  ];

  return (
    <Box sx={{ mt: 1 }}>
      <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 600 }}>
        Your password needs
      </Typography>

      <Box
        component="ul"
        sx={{ m: 0, mt: 0.5, pl: 2.5, display: 'flex', flexDirection: 'column', gap: 0.25 }}
      >
        {rules.map((rule) => (
          <Typography key={rule} component="li" variant="caption" sx={{ color: 'text.secondary' }}>
            {rule}
          </Typography>
        ))}
      </Box>
    </Box>
  );
}
