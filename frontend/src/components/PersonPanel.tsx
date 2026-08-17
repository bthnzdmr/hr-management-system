import type { ReactNode } from 'react';
import { Box, Chip, Divider, Link, Stack, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined';
import { InitialsAvatar } from './InitialsAvatar';
import { fullName } from './orgScope';
import type { Swatch } from './orgScope';
import type { OrgNode } from '../api/orgChart';

interface Props {
  person: OrgNode | null;
  /** Kokten bu kisiye kadar olan zincir; kisinin kendisi sonda. */
  chain: OrgNode[];
  color?: Swatch;
  onSelect: (person: OrgNode) => void;
}

/** Ray cizgisinin sol kenardan uzakligi (px). Nokta bunun uzerinde ortalanir. */
const RAIL = 7;

/** Secilen kisinin ayrintisi. */
export function PersonPanel({ person, chain, color, onSelect }: Props) {
  if (!person) {
    return (
      <Stack spacing={1.5} sx={{ alignItems: 'flex-start', py: 1 }}>
        <Box
          aria-hidden
          sx={{
            width: 40,
            height: 40,
            borderRadius: 1.5,
            display: 'grid',
            placeItems: 'center',
            bgcolor: 'action.hover',
            color: 'text.secondary',
          }}
        >
          <AccountTreeOutlinedIcon fontSize="small" />
        </Box>

        <Typography variant="subtitle2">Nobody selected</Typography>
        <Typography variant="body2" color="text.secondary">
          Pick anyone in the chart to see where they sit and who they work with.
        </Typography>
      </Stack>
    );
  }

  const managers = chain.slice(0, -1);
  const team = person.reports;

  return (
    <Stack spacing={2.5} aria-live="polite">
      <Stack spacing={1.5}>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <InitialsAvatar firstName={person.firstName} lastName={person.lastName} size={44} />

          <Box sx={{ minWidth: 0 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 600, lineHeight: 1.25 }}>
              {fullName(person)}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.35 }}>
              {person.jobTitle}
            </Typography>
          </Box>
        </Stack>

        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
          <Chip
            size="small"
            label={person.departmentName}
            sx={{
              // Semadaki daireyle AYNI renk: hangi dalin parcasi oldugu
              // aciklama gerektirmeden kuruluyor.
              ...(color ? { bgcolor: color.fill, color: color.ink } : {}),
            }}
          />
          {team.length > 0 && (
            <Chip
              size="small"
              variant="outlined"
              label={`${team.length} direct`}
              sx={{ fontVariantNumeric: 'tabular-nums' }}
            />
          )}
        </Stack>
      </Stack>

      <Divider />

      <Stack spacing={1}>
        <Label>Where they sit</Label>

        {/* Tek bir dikey cizgi: tepeden secili kisiye iner. Cizim ile panel
            ayni hiyerarsiyi iki farkli dilde anlatiyor. */}
        <Rail label="Where they sit">
          {managers.map((manager) => (
            <RailRow key={manager.id}>
              <Dot />
              <Link
                component="button"
                type="button"
                variant="body2"
                onClick={() => onSelect(manager)}
                sx={{ textAlign: 'left', minWidth: 0 }}
              >
                {fullName(manager)}
              </Link>
            </RailRow>
          ))}

          <RailRow current>
            <Dot color={color?.fill} filled />
            <Typography
              variant="body2"
              sx={{ fontWeight: 600, minWidth: 0, overflowWrap: 'anywhere' }}
            >
              {fullName(person)}
            </Typography>
          </RailRow>

          {/* Ekip ayni raydan bir kademe iceri dallanir: "altinda" olmak
              girintiyle anlatiliyor. */}
          {team.length > 0 && (
            <Box component="li" sx={{ pl: 2.5, listStyle: 'none' }}>
              <Rail dense label="Their team">
                {team.map((report) => (
                  <RailRow key={report.id}>
                    <Dot small />
                    <Link
                      component="button"
                      type="button"
                      variant="body2"
                      onClick={() => onSelect(report)}
                      sx={{ textAlign: 'left', minWidth: 0 }}
                    >
                      {fullName(report)}
                    </Link>
                  </RailRow>
                ))}
              </Rail>
            </Box>
          )}
        </Rail>

        {managers.length === 0 && team.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            Nobody above and nobody below — this person stands alone in the chart.
          </Typography>
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

function Label({ children }: { children: string }) {
  return (
    <Typography variant="overline" color="text.secondary" sx={{ fontSize: 10.5 }}>
      {children}
    </Typography>
  );
}

/** Dikey baglanti cizgisi; noktalar bunun uzerinde durur. */
function Rail({ children, dense, label }: { children: ReactNode; dense?: boolean; label?: string }) {
  return (
    <Stack
      component="ol"
      aria-label={label}
      sx={{
        listStyle: 'none',
        m: 0,
        p: 0,
        position: 'relative',
        '&::before': {
          content: '""',
          position: 'absolute',
          left: `${RAIL}px`,
          top: dense ? 4 : 12,
          bottom: 12,
          borderLeft: '1px solid',
          borderColor: 'divider',
        },
      }}
    >
      {children}
    </Stack>
  );
}

function RailRow({ children, current }: { children: ReactNode; current?: boolean }) {
  return (
    <Stack
      component="li"
      direction="row"
      spacing={1.25}
      // "page" degil "location": sayfa gezinmesi degil, semadaki KONUM.
      {...(current ? { 'aria-current': 'location' as const } : {})}
      sx={{ alignItems: 'center', minHeight: 26, minWidth: 0 }}
    >
      {children}
    </Stack>
  );
}

interface DotProps {
  color?: string;
  filled?: boolean;
  small?: boolean;
}

function Dot({ color, filled, small }: DotProps) {
  const size = small ? 5 : 7;

  return (
    <Box
      aria-hidden
      sx={{
        width: RAIL * 2 + 1,
        display: 'grid',
        placeItems: 'center',
        flexShrink: 0,
        // Zemin rengi cizgiyi noktanin arkasinda KESER; olmasaydi cizgi
        // noktanin icinden gecerdi.
        bgcolor: 'background.paper',
        py: 0.25,
      }}
    >
      <Box
        sx={{
          width: size,
          height: size,
          borderRadius: '50%',
          ...(filled
            ? { bgcolor: color ?? 'primary.main' }
            : { border: '1.5px solid', borderColor: 'text.disabled' }),
        }}
      />
    </Box>
  );
}
