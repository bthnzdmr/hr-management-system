import { useEffect, useMemo, useState } from 'react';
import {
  Alert, Box, Breadcrumbs, Fade, Link, Paper, Skeleton, Stack, Typography,
} from '@mui/material';
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined';
import { orgChartApi } from '../api/orgChart';
import type { OrgChart, OrgNode } from '../api/orgChart';
import { errorMessage } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { EmptyState } from '../components/EmptyState';
import { OrgBubbleMap, OrgOutline } from '../components/OrgBubbleMap';
import { DepartmentRail } from '../components/DepartmentRail';
import { departmentsOf, fullName, scopeToDepartment } from '../components/orgScope';

export function OrgChartPage() {
  const [chart, setChart] = useState<OrgChart | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [department, setDepartment] = useState<string | null>(null);
  /** Icine girilen kisiler; son eleman su anda tepede duran kisidir. */
  const [trail, setTrail] = useState<OrgNode[]>([]);

  useEffect(() => {
    let active = true;

    orgChartApi.get()
      .then((data) => {
        if (active) setChart(data);
      })
      .catch((cause) => {
        if (active) setError(errorMessage(cause));
      });

    // Temizlik: bilesen sokulduktan sonra gelen cevap durumu yazmasin.
    return () => {
      active = false;
    };
  }, []);

  const departments = useMemo(
    () => (chart ? departmentsOf(chart.roots) : []),
    [chart],
  );

  // Departman kapsamindaki agac. Kirinti yolu bunun UZERINE biner, yani
  // departman degisince yol sifirlanmali -- baska bir departmanin kisisine
  // ait bir yol anlamsizdir.
  const scoped = useMemo(() => {
    if (!chart) return [];

    return department === null ? chart.roots : scopeToDepartment(chart.roots, department);
  }, [chart, department]);

  const visible = trail.length > 0 ? [trail[trail.length - 1]] : scoped;

  const selectDepartment = (next: string | null) => {
    setDepartment(next);
    setTrail([]);
  };

  if (error) {
    return <Alert severity="error">{error}</Alert>;
  }

  return (
    <Stack spacing={2.5}>
      <PageHeader
        eyebrow="Directory"
        title="Org chart"
        description={chart
          ? `${chart.placed} ${chart.placed === 1 ? 'person' : 'people'} — a circle inside another means that person reports to them`
          : 'Reporting lines across the organisation'}
      />

      {/* Ulasilamayan kisiler UYARI degil BILGI olarak gosterilir.
          Yoneticisi pasiflesmis personel agacta yer alamaz ve bu bir veri
          hatasi olmayabilir -- ama sessizce kaybolmasi da olmaz. */}
      {chart && chart.unreachable > 0 && (
        <Alert severity="info">
          {chart.unreachable} active {chart.unreachable === 1 ? 'person is' : 'people are'} not
          shown: their manager has left, so they cannot be connected to anyone above them.
        </Alert>
      )}

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2.5} sx={{ alignItems: 'flex-start' }}>
        <Paper
          sx={{
            p: 1,
            width: { xs: '100%', md: 250 },
            flexShrink: 0,
            // Cizim uzun oldugunda raf ekranda kalir; secim yapmak icin
            // yukari kaydirmak gerekmez.
            position: { md: 'sticky' },
            top: { md: 88 },
          }}
        >
          {!chart && <Skeleton variant="rounded" height={280} />}
          {chart && (
            <DepartmentRail
              departments={departments}
              selected={department}
              onSelect={selectDepartment}
              total={chart.placed}
            />
          )}
        </Paper>

        <Paper sx={{ p: { xs: 2, md: 3 }, flexGrow: 1, width: '100%', minWidth: 0 }}>
          {!chart && <Skeleton variant="rounded" sx={{ pt: '100%' }} />}

          {chart && chart.roots.length === 0 && (
            <EmptyState
              icon={<AccountTreeOutlinedIcon />}
              title="No reporting structure yet"
              description="Nobody sits at the top of the organisation, so there is no tree to draw."
            />
          )}

          {chart && chart.roots.length > 0 && (
            <Stack spacing={2}>
              <Trail
                department={department}
                trail={trail}
                onOpen={(depth) => setTrail(trail.slice(0, depth))}
              />

              {/* Kapsam degisince bilesen YENIDEN kurulur: giris animasyonu
                  bastan calisir ve gecis kesme yerine acilma gibi okunur. */}
              <Fade in key={`${department ?? 'all'}:${trail.map((n) => n.id).join('>')}`}>
                <Box>
                  <OrgBubbleMap
                    roots={visible}
                    onDrillDown={(node) => setTrail([...trail, node])}
                  />
                </Box>
              </Fade>

              <OrgOutline nodes={visible} />
            </Stack>
          )}
        </Paper>
      </Stack>
    </Stack>
  );
}

interface TrailProps {
  department: string | null;
  trail: OrgNode[];
  onOpen: (depth: number) => void;
}

/** Nereye kadar girildigini gosterir ve geri donusu tek tiklamaya indirir. */
function Trail({ department, trail, onOpen }: TrailProps) {
  const scopeLabel = department ?? 'Whole organisation';

  return (
    <Stack
      direction="row"
      spacing={1.5}
      sx={{ flexWrap: 'wrap', minHeight: 28, alignItems: 'center', justifyContent: 'space-between' }}
    >
      <Breadcrumbs aria-label="Open team" sx={{ fontSize: 14 }}>
        {trail.length === 0 ? (
          <Typography variant="body2" color="text.primary">{scopeLabel}</Typography>
        ) : (
          <Link component="button" type="button" variant="body2" onClick={() => onOpen(0)}>
            {scopeLabel}
          </Link>
        )}

        {trail.map((node, depth) => (depth === trail.length - 1 ? (
          <Typography key={node.id} variant="body2" color="text.primary">
            {fullName(node)}
          </Typography>
        ) : (
          <Link
            key={node.id}
            component="button"
            type="button"
            variant="body2"
            onClick={() => onOpen(depth + 1)}
          >
            {fullName(node)}
          </Link>
        )))}
      </Breadcrumbs>

      <Typography variant="caption" color="text.secondary">
        Click a circle with a team inside to open it
      </Typography>
    </Stack>
  );
}
