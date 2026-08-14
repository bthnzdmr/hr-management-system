import { useEffect, useMemo, useState } from 'react';
import { Alert, Box, Fade, Paper, Skeleton, Stack, Typography } from '@mui/material';
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined';
import { orgChartApi } from '../api/orgChart';
import type { OrgChart, OrgNode } from '../api/orgChart';
import { errorMessage } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { EmptyState } from '../components/EmptyState';
import { OrgBubbleMap, OrgOutline, useDepartmentColors } from '../components/OrgBubbleMap';
import { DepartmentRail } from '../components/DepartmentRail';
import { departmentsOf, scopeToDepartment } from '../components/orgScope';
import { PersonPanel } from '../components/PersonPanel';

export function OrgChartPage() {
  const [chart, setChart] = useState<OrgChart | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [department, setDepartment] = useState<string | null>(null);
  /** Sagdaki panelde gosterilen kisi. */
  const [selected, setSelected] = useState<OrgNode | null>(null);

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

  const selectDepartment = (next: string | null) => {
    setDepartment(next);
    // Baska bir departmanin kisisini secili birakmak anlamsizdir.
    setSelected(null);
  };

  // Secilen kisinin merkezden kendisine kadar olan zinciri; panel bunu okur.
  const chain = useMemo(
    () => (selected ? pathTo(scoped, selected.id) : []),
    [scoped, selected],
  );

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
            width: { xs: '100%', md: 230 },
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

        {/* Cizim ve panel ORTAK bir sutunda.

            Ucu birden md'de satira girseydi -- ilk yazildigi gibi -- panel
            `flexShrink: 0` ile butun satiri kapar ve cizim sifira sikisirdi;
            tam olarak bu yasandi.

            Kirilma noktasi OLCULEREK secildi. Icerik `maxWidth: 1280` ve
            kenar bosluklariyla sinirli, yani lg'de (1200 px) kullanilabilir
            genislik 888 px. Uc sutun orada acilsaydi cizime 318 px kalirdi --
            33 dugum icin okunmaz. xl'de acilinca cizim her iki durumda da
            ~620-650 px: lg'de panel altta ve cizim 618, xl'de yan yana ve
            cizim 646. */}
        <Stack
          direction={{ xs: 'column', xl: 'row' }}
          spacing={2.5}
          sx={{ flexGrow: 1, minWidth: 0, width: '100%', alignItems: 'flex-start' }}
        >
        <Paper sx={{ p: { xs: 2, md: 3 }, flexGrow: 1, minWidth: 0, width: '100%' }}>
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
              <Typography variant="caption" color="text.secondary">
                Click anyone to see where they sit in the organisation
              </Typography>

              <Fade in key={department ?? 'all'}>
                <Box>
                  <OrgBubbleMap
                    roots={scoped}
                    colors={colors}
                    selectedId={selected?.id ?? null}
                    onSelect={setSelected}
                  />
                </Box>
              </Fade>

              <OrgOutline nodes={scoped} />
            </Stack>
          )}
        </Paper>
        <Paper
          sx={{
            p: 2.5,
            width: { xs: '100%', xl: 300 },
            flexShrink: 0,
            // Cizim uzun oldugunda panel ekranda kalir; secim yapip yukari
            // kaydirmak gerekmez.
            position: { xl: 'sticky' },
            top: { xl: 88 },
          }}
        >
          {!chart && <Skeleton variant="rounded" height={220} />}
          {chart && (
            // `key` ile yeniden kuruluyor: hizlica birkac kisiye tiklandiginda
            // gecisler ic ice girmesin. Ayni desen semada da kullaniliyor.
            <Fade in key={selected?.id ?? 'none'} timeout={220}>
              <Box>
                <PersonPanel
                  person={selected}
                  chain={chain}
                  color={selected ? colors.get(selected.departmentName) : undefined}
                  onSelect={setSelected}
                />
              </Box>
            </Fade>
          )}
        </Paper>
        </Stack>
      </Stack>
    </Stack>
  );
}

/** Koklerden verilen kisiye giden yol; kisinin kendisi sonda. */
function pathTo(roots: OrgNode[], id: number): OrgNode[] {
  for (const root of roots) {
    if (root.id === id) return [root];

    const below = pathTo(root.reports, id);
    if (below.length > 0) return [root, ...below];
  }

  return [];
}
