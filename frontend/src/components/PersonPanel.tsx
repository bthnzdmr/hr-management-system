import { Box, Chip, Divider, Link, Stack, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { InitialsAvatar } from './InitialsAvatar';
import { fullName } from './orgScope';
import type { OrgNode } from '../api/orgChart';

interface Props {
  person: OrgNode | null;
  /** Merkezden bu kisiye kadar olan zincir; kendisi dahil. */
  chain: OrgNode[];
  color?: string;
  onSelect: (person: OrgNode) => void;
}

/**
 * Secilen kisinin ayrintisi.
 *
 * <p><b>Neden yeni bir istek atilmiyor?</b> Sema zaten ad, unvan, departman ve
 * butun raporlama agacini tasiyor. Her secimde `GET /api/employees/{id}`
 * cagirmak yeni bir yaris durumu acardi: yavas donen bir cevap, kullanici
 * baskasini sectikten SONRA gelip yanlis kisinin bilgisini yazabilirdi --
 * projede bu hata bir kez yasandi. Ucret ve iletisim gibi semada olmayan
 * alanlar icin tam kayda baglanti veriliyor.
 */
export function PersonPanel({ person, chain, color, onSelect }: Props) {
  if (!person) {
    return (
      <Stack spacing={1} sx={{ py: 2 }}>
        <Typography variant="subtitle2">Nobody selected</Typography>
        <Typography variant="body2" color="text.secondary">
          Pick anyone in the chart to see their place in the organisation.
        </Typography>
      </Stack>
    );
  }

  // Zincir kokten kisiye dogru okunur; kisinin kendisi sonda durur.
  const managers = chain.slice(0, -1);

  return (
    <Stack spacing={2} aria-live="polite">
      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
        <InitialsAvatar firstName={person.firstName} lastName={person.lastName} size={44} />

        <Box sx={{ minWidth: 0 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600, lineHeight: 1.3 }}>
            {fullName(person)}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {person.jobTitle}
          </Typography>
        </Box>
      </Stack>

      <Chip
        size="small"
        label={person.departmentName}
        sx={{
          alignSelf: 'flex-start',
          // Semadaki daireyle ayni renk: hangi departmanin dali oldugu
          // aciklama gerektirmeden kuruluyor.
          ...(color ? { bgcolor: color, color: 'inherit' } : {}),
        }}
      />

      <Divider />

      <Stack spacing={0.5}>
        <Typography variant="overline" color="text.secondary">
          Reports to
        </Typography>

        {managers.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            Nobody — this person sits at the top of the chart.
          </Typography>
        ) : (
          // Zincirin tamami: tepeden asagi. Yalnizca dogrudan yoneticiyi
          // gostermek "bu kisi organizasyonun neresinde" sorusunu cevapsiz
          // birakirdi.
          <Stack spacing={0.25}>
            {managers.map((manager, index) => (
              <Link
                key={manager.id}
                component="button"
                type="button"
                variant="body2"
                onClick={() => onSelect(manager)}
                sx={{ textAlign: 'left', pl: index * 1.25, alignSelf: 'flex-start' }}
              >
                {fullName(manager)}
              </Link>
            ))}
          </Stack>
        )}
      </Stack>

      <Stack spacing={0.5}>
        <Typography variant="overline" color="text.secondary">
          Team
        </Typography>

        {person.reports.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            No direct reports.
          </Typography>
        ) : (
          <>
            <Typography variant="body2" color="text.secondary">
              {person.reports.length} direct
              {person.reports.length === 1 ? ' report' : ' reports'}
            </Typography>

            <Stack spacing={0.25} sx={{ mt: 0.5 }}>
              {person.reports.map((report) => (
                <Link
                  key={report.id}
                  component="button"
                  type="button"
                  variant="body2"
                  onClick={() => onSelect(report)}
                  sx={{ textAlign: 'left', alignSelf: 'flex-start' }}
                >
                  {fullName(report)}
                </Link>
              ))}
            </Stack>
          </>
        )}
      </Stack>

      <Divider />

      {/* Maas ve iletisim semada YOK; onlar icin tam kayda gidilir. */}
      <Link component={RouterLink} to={`/employees/${person.id}`} variant="body2">
        Open full record
      </Link>
    </Stack>
  );
}
