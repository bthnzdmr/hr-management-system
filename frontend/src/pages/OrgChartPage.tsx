import { useEffect, useMemo, useState } from 'react';
import { Alert, Box, Fade, Paper, Skeleton, Stack } from '@mui/material';
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined';
import { orgChartApi } from '../api/orgChart';
import type { OrgChart, OrgNode } from '../api/orgChart';
import { errorMessage } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { EmptyState } from '../components/EmptyState';
import { OrgBubbleMap, useDepartmentColors } from '../components/OrgBubbleMap';
import { DepartmentRail } from '../components/DepartmentRail';
import {
  departmentInitials, departmentsOf, groupByDepartment, isDepartmentNode, scopeToDepartment,
} from '../components/orgScope';
import type { Department } from '../components/orgScope';
import { PersonPanel } from '../components/PersonPanel';
import { OrgSearchField } from '../components/OrgSearchField';
import { search, splitByScope } from '../components/orgSearch';
import type { Match } from '../components/orgSearch';

export function OrgChartPage() {
  const [chart, setChart] = useState<OrgChart | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [department, setDepartment] = useState<string | null>(null);
  /** Sagdaki panelde gosterilen kisi. */
  const [selected, setSelected] = useState<OrgNode | null>(null);
  const [query, setQuery] = useState('');

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

  // Renk eslemesi TEK yerde uretilir: raf ile cizim ayri ayri hesaplasaydi
  // ikisi zamanla birbirinden ayrilirdi.
  const names = useMemo(() => departments.map((d) => d.name), [departments]);
  const colors = useDepartmentColors(names);

  // Departman kapsamindaki agac. Kirinti yolu bunun UZERINE biner, yani
  // departman degisince yol sifirlanmali -- baska bir departmanin kisisine
  // ait bir yol anlamsizdir.
  const scoped = useMemo(() => {
    if (!chart) return [];

    // Butun organizasyonda araya bir DEPARTMAN katmani giriyor: merkezde
    // kurum, cevresinde departmanlar, onlarin cevresinde calisanlar. Onceden
    // butun kisiler tek merkezden dagiliyordu ve departman yalnizca RENKTEN
    // okunuyordu; simdi "kim hangi departmanda" konumdan okunuyor -- renk bir
    // kodlama, konum ise yapinin kendisi.
    return department === null
      ? groupByDepartment(chart.roots)
      : scopeToDepartment(chart.roots, department);
  }, [chart, department]);

  /** Secim ac/kapa calisir. */
  const toggleSelect = (person: OrgNode) => {
    setSelected((current) => (current?.id === person.id ? null : person));
  };

  const selectDepartment = (next: string | null) => {
    setDepartment(next);
    // Baska bir departmanin kisisini secili birakmak anlamsizdir.
    setSelected(null);
  };

  // Merkezdeki isaret KAPSAMI anlatir. Sabit "HR" yaziliydi ve departmana
  // gecildiginde de ayni kaliyordu, yani merkez neyin merkezi oldugunu
  // soylemiyordu.
  //
  // Bilinen catisma: "Human Resources" departmaninin bas harfleri de HR,
  // dolayisiyla o departman secildiginde isaret ana gorunumdekiyle ayni
  // gorunuyor. Kullanici ana kisimda HR'i acikca istedi; ayirt etmek gerekirse
  // cozum burada.
  const hub = useMemo(
    () => (department === null
      // "Human Resources" departmaninin bas harfleri de HR. Cakisma BICIMLE
      // cozuluyor, harfle degil: merkez tek DOLU disk, departmanlar kalin
      // halkali. Merkeze kadro sayisi yazmak da denendi -- ilgisiz gorundu.
      ? { mark: 'HR', name: 'Whole organisation' }
      : {
        mark: departmentInitials(department),
        name: department,
        // Merkez dolu bir disk ve dolgusu departmanin rengi; harfler o rengin
        // uzerinde okunacak sekilde secilmis `ink`.
        swatch: colors.get(department),
      }),
    [department, colors],
  );

  // Secilen kisinin merkezden kendisine kadar olan zinciri; panel bunu okur.
  // Departman dugumleri ELENIR: onlar birer kisi degil ve panel onlari kisi
  // gibi cizerdi -- yonetim zinciri yalnizca gercek kisilerden olusur.
  const chain = useMemo(
    () => (selected ? pathTo(scoped, selected.id).filter((node) => !isDepartmentNode(node)) : []),
    [scoped, selected],
  );

  // Arama BUTUN kadroda calisir, yalnizca cizili kapsamda degil: "bu kisi
  // nerede" sorusunun cevabi cogu zaman baska bir departmandadir ve arama
  // yalnizca ekrandakine baksaydi var olma sebebini karsilamazdi.
  const matches = useMemo(
    () => (chart ? search(chart.roots, query) : []),
    [chart, query],
  );

  // Cizili olanlar semada vurgulanir; digerleri ancak TIKLANIRSA oraya
  // gecirir. Sema kendiliginden yeniden duzenlenmez.
  const { inScope, elsewhere } = useMemo(() => {
    const visible = new Set(scoped.flatMap(function ids(node): number[] {
      return [node.id, ...node.reports.flatMap(ids)];
    }));

    return splitByScope(matches, visible);
  }, [matches, scoped]);

  // `null` = arama yapilmiyor; bos kume = arandi ama kimse eslesmedi. Harita
  // ikisini FARKLI ele aliyor.
  const matchedIds = query.trim().length >= 2
    ? new Set(inScope.map((m) => m.node.id))
    : null;

  /** Kapsam disindaki bir eslesmeye gecer VE onu secer. */
  const jumpTo = (match: Match) => {
    setDepartment(match.node.departmentName);
    setSelected(match.node);
  };

  /** Enter: cizili kapsamdaki ilk eslesmeyi secer, yoksa ilk uzaktakine gider. */
  const selectFirstMatch = () => {
    if (inScope.length > 0) {
      setSelected(inScope[0].node);
      return;
    }
    if (elsewhere.length > 0) jumpTo(elsewhere[0]);
  };

  if (error) {
    return <Alert severity="error">{error}</Alert>;
  }

  return (
    <Stack spacing={2.5}>
      <PageHeader
        eyebrow="Structure"
        title="Organisation map"
        // Ayrac KAPALI: cerceveli uc panelin hemen ustune bir cizgi daha
        // binince sayfa ust uste seritlere bolunmus gorunuyordu.
        divider={false}
        // Aciklama kisaldi ve DUZELDI: "her halka bir yonetim seviyesi"
        // artik dogru degil, ilk halka departmanlarin.
        description={chart ? summary(chart, department, departments) : ' '}
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

        {/* Arama kutusu rafin YANINDA degil, cizimle ayni sutunda: raf bir
            SUZGEC (kapsami daraltir), arama ise bir BULMA araci (kapsami
            asar). Ikisini yan yana koymak ayni sey sanilmalarina yol acardi. */}

        {/* Cizim ve panel ORTAK bir sutunda.

            Ucu birden md'de satira girseydi -- ilk yazildigi gibi -- panel
            `flexShrink: 0` ile butun satiri kapar ve cizim sifira sikisirdi;
            tam olarak bu yasandi.

            Sayfa kabugun genislik sinirinin DISINDA (bkz. Layout'taki
            FULL_BLEED), dolayisiyla olcum de degisti. 1920 px ekranda:
            kenar cubugu daraltilmisken cizime 1218 px, acikken 1038 px
            kaliyor -- ikisi de 36 dugumun ismini tasiyacak genislikte.
            xl'in altinda panel altta duruyor ve cizim satirin tamamini alir. */}
        <Stack
          direction={{ xs: 'column', xl: 'row' }}
          spacing={2.5}
          sx={{ flexGrow: 1, minWidth: 0, alignItems: 'flex-start', alignSelf: 'stretch' }}
        >
        <Paper sx={{ p: { xs: 2, md: 3 }, flexGrow: 1, minWidth: 0, alignSelf: 'stretch' }}>
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
              <OrgSearchField
                query={query}
                onQueryChange={setQuery}
                inScope={inScope}
                elsewhere={elsewhere}
                onJump={jumpTo}
                onSelectFirst={selectFirstMatch}
              />

              {/* Buradaki "Click anyone to see where they sit" ipucu KALDIRILDI:
                  sagdaki panel bos haldeyken zaten birebir ayni seyi soyluyor
                  ("Pick anyone in the chart to see where they sit...") ve iki
                  yere yazilan bir cumlenin biri zamanla digerinden ayrilir. */}
              <Fade in key={department ?? 'all'}>
                <Box>
                  <OrgBubbleMap
                    roots={scoped}
                    colors={colors}
                    selectedId={selected?.id ?? null}
                    onSelect={toggleSelect}
                    // Departman balonuna tiklamak o departmana INER; rafta
                    // yapilan secimin aynisi. Balonu bir kisi gibi secilebilir
                    // yapmak paneli olmayan bir kisiyle doldururdu.
                    onSelectDepartment={selectDepartment}
                    hub={hub}
                    // Merkez DAIMA kapsamin kendisi. Bayrak olmadan merkez
                    // yalnizca birden fazla kok varken kapsami gosteriyor,
                    // tek kokte oraya o kisi oturuyordu -- ayni ekran
                    // departmandan departmana farkli davraniyordu.
                    alwaysHub
                    matchedIds={matchedIds}
                  />
                </Box>
              </Fade>
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

/** Basligin altindaki tek satirlik ozet; kapsam degistikce degisir. */
function summary(chart: OrgChart, department: string | null, departments: Department[]) {
  const count = department === null
    ? chart.placed
    : departments.find((entry) => entry.name === department)?.headcount ?? 0;

  const people = `${count} ${count === 1 ? 'person' : 'people'}`;

  return department === null
    ? `${people} across ${departments.length} departments`
    : `${people} in ${department}`;
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
