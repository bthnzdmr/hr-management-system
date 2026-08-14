import { useEffect, useMemo, useState } from 'react';
import { Alert, Box, Button, Fade, Paper, Skeleton, Stack, Typography } from '@mui/material';
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined';
import { orgChartApi } from '../api/orgChart';
import type { OrgChart, OrgNode } from '../api/orgChart';
import { errorMessage } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { EmptyState } from '../components/EmptyState';
import {
  DepartmentLegend, OrgBubbleMap, OrgOutline, useDepartmentColors,
} from '../components/OrgBubbleMap';
import { DepartmentRail } from '../components/DepartmentRail';
import { departmentsOf, scopeToDepartment } from '../components/orgScope';

export function OrgChartPage() {
  const [chart, setChart] = useState<OrgChart | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [department, setDepartment] = useState<string | null>(null);
  /**
   * Kapali dugumler.
   *
   * Onceki tasarim bir dugume tiklandiginda o dalin ICINE giriyordu ve her
   * tiklama bir seviye daha derine indiriyordu -- kullanici nerede oldugunu
   * kaybediyordu. Acip kapatmak yerinde kalir: baglam hic degismez.
   */
  const [collapsed, setCollapsed] = useState<ReadonlySet<number>>(new Set());

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

  // Renk eslemesi TEK yerde uretilir: raf ile cizim ayrı ayrı hesaplasaydi
  // ikisi zamanla birbirinden ayrilirdi.
  const names = useMemo(() => departments.map((d) => d.name), [departments]);
  const colors = useDepartmentColors(names);

  // Departman kapsamindaki agac. Kirinti yolu bunun UZERINE biner, yani
  // departman degisince yol sifirlanmali -- baska bir departmanin kisisine
  // ait bir yol anlamsizdir.
  const scoped = useMemo(() => {
    if (!chart) return [];

    return department === null ? chart.roots : scopeToDepartment(chart.roots, department);
  }, [chart, department]);

  const toggle = (node: OrgNode) => {
    setCollapsed((current) => {
      const next = new Set(current);

      if (!next.delete(node.id)) next.add(node.id);

      return next;
    });
  };

  const selectDepartment = (next: string | null) => {
    setDepartment(next);
    // Baska bir departmanin dugumlerine ait kapali/acik durumu anlamsizdir.
    setCollapsed(new Set());
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
              colors={colors}
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
              <Stack
                direction="row"
                spacing={1.5}
                sx={{ flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between' }}
              >
                <Typography variant="caption" color="text.secondary">
                  Click a node to fold its team away — the chart stays where it is
                </Typography>

                {collapsed.size > 0 && (
                  <Button size="small" onClick={() => setCollapsed(new Set())}>
                    Expand all
                  </Button>
                )}
              </Stack>

              <Fade in key={department ?? 'all'}>
                <Box>
                  <OrgBubbleMap
                    roots={scoped}
                    colors={colors}
                    collapsed={collapsed}
                    onToggle={toggle}
                  />
                </Box>
              </Fade>

              {/* Renk anahtari yalnizca birden fazla departman gorunurken
                  anlamlidir; tek departman kapsaminda hepsi ayni renktir. */}
              {department === null && <DepartmentLegend names={names} colors={colors} />}

              <OrgOutline nodes={scoped} />
            </Stack>
          )}
        </Paper>
      </Stack>
    </Stack>
  );
}
