import { Box, Stack, Typography } from '@mui/material';
import type { Department } from './orgScope';

interface Props {
  departments: Department[];
  /** null = butun organizasyon. */
  selected: string | null;
  onSelect: (department: string | null) => void;
  total: number;
}

/**
 * Departman secimi.
 *
 * <p>Acilir liste yerine hepsi bir arada duruyor: secenek sayisi az ve sabit,
 * ve yanlarindaki kadro sayisi tek bakista karsilastirilabiliyor. Acilir liste
 * bu karsilastirmayi bir tiklamanin arkasina saklardi.
 */
export function DepartmentRail({ departments, selected, onSelect, total }: Props) {
  const largest = Math.max(...departments.map((d) => d.headcount), 1);

  return (
    <Stack spacing={0.5} role="group" aria-label="Filter by department">
      <Typography
        variant="overline"
        color="text.secondary"
        sx={{ px: 1.25, letterSpacing: '0.09em' }}
      >
        Departments
      </Typography>

      <RailButton
        label="Whole organisation"
        count={total}
        ratio={1}
        selected={selected === null}
        onClick={() => onSelect(null)}
      />

      {departments.map((department) => (
        <RailButton
          key={department.name}
          label={department.name}
          count={department.headcount}
          ratio={department.headcount / largest}
          selected={selected === department.name}
          onClick={() => onSelect(department.name)}
        />
      ))}
    </Stack>
  );
}

interface RailButtonProps {
  label: string;
  count: number;
  ratio: number;
  selected: boolean;
  onClick: () => void;
}

function RailButton({ label, count, ratio, selected, onClick }: RailButtonProps) {
  return (
    <Box
      component="button"
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      sx={{
        position: 'relative',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 1,
        width: '100%',
        // Dokunma hedefi: parmak icin en kucuk guvenli olcu.
        minHeight: 44,
        px: 1.25,
        py: 1,
        border: 0,
        borderRadius: 1,
        font: 'inherit',
        textAlign: 'left',
        cursor: 'pointer',
        overflow: 'hidden',
        color: selected ? 'text.primary' : 'text.secondary',
        bgcolor: selected ? 'action.selected' : 'transparent',
        transition: 'background-color 160ms ease, color 160ms ease',
        '&:hover': { bgcolor: 'action.hover', color: 'text.primary' },
        '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 2 },
      }}
    >
      {/* Secili olani TEK BASINA renk anlatmaz: solda bir isaret cubugu da var. */}
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          left: 0,
          top: 6,
          bottom: 6,
          width: 3,
          borderRadius: 3,
          bgcolor: 'primary.main',
          transform: selected ? 'scaleY(1)' : 'scaleY(0)',
          transformOrigin: 'center',
          transition: 'transform 220ms cubic-bezier(0.16, 1, 0.3, 1)',
        }}
      />

      <Typography variant="body2" sx={{ fontWeight: selected ? 600 : 400, minWidth: 0 }} noWrap>
        {label}
      </Typography>

      <Stack direction="row" spacing={1} sx={{ flexShrink: 0, alignItems: 'center' }}>
        {/* Kadro buyuklugu ayrica cubuk olarak: sayiyi okumadan siralama gorunur. */}
        <Box
          aria-hidden
          sx={{
            width: 34,
            height: 3,
            borderRadius: 3,
            bgcolor: 'divider',
            overflow: 'hidden',
          }}
        >
          <Box
            sx={{
              width: `${Math.round(ratio * 100)}%`,
              height: '100%',
              bgcolor: selected ? 'primary.main' : 'text.disabled',
              transition: 'background-color 160ms ease',
            }}
          />
        </Box>

        <Typography
          variant="caption"
          sx={{ fontVariantNumeric: 'tabular-nums', color: 'text.secondary' }}
        >
          {count}
        </Typography>
      </Stack>
    </Box>
  );
}
