import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import {
  Alert, Box, Button, Chip, Divider, Grid, Paper, Skeleton, Stack, Tooltip, Typography, alpha,
  useTheme,
} from '@mui/material';
import AutorenewOutlinedIcon from '@mui/icons-material/AutorenewOutlined';
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined';
import { dashboardApi } from '../api/dashboard';
import type { DashboardOverview } from '../api/dashboard';
import { errorMessage } from '../api/client';
import { BarRow } from '../components/BarRow';
import { PageHeader } from '../components/PageHeader';
import { Sparkline } from '../components/Sparkline';
import { ManagerLoad, ManagerLoadLegend } from '../components/ManagerLoad';
import { HOVER_LIFT } from '../theme/theme';
import { TERMINATION_REASON_LABELS } from '../types/api';
import type { TerminationReason } from '../types/api';

type Tone = 'primary' | 'success' | 'error' | 'warning';

function StatCard({ label, value, hint, icon, tone, trend, trendLabel }: {
  label: string;
  value: string | number;
  hint: string;
  icon: ReactNode;
  tone: Tone;
  /** Son 12 ayin degerleri; yalnizca aylik serisi olan kutucuklarda dolu. */
  trend?: number[];
  trendLabel?: string;
}) {
  const theme = useTheme();
  return (
    <Paper
      sx={{
        p: 2.5,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        gap: 0.5,
        position: 'relative',
        overflow: 'hidden',
        ...HOVER_LIFT,
        '&:hover': { borderColor: (t) => alpha(t.palette[tone].main, 0.45) },
      }}
    >
      {/* Kosedeki radyal isik KALDIRILDI; yerine kartin tepesinde iki
          piksellik bir cizgi. Ayni bilgiyi (bu sayi hangi kategoriye ait)
          tasir ama renk sayfaya yayilmaz -- panelin ciddi durmasi, vurgunun
          KAPLADIGI ALANLA dogrudan ilgili. */}
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: 2,
          bgcolor: `${tone}.main`,
        }}
      />

      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', color: 'text.secondary' }}>
        {icon}
        <Typography variant="overline" sx={{ fontSize: 10.5 }}>
          {label}
        </Typography>
      </Stack>

      {/* Sayi ve egri AYNI satirda: egri sayinin altina konsaydi iki ayri
          bilgi gibi okunurdu, oysa biri digerinin baglami. */}
      <Stack
        direction="row"
        sx={{ alignItems: 'flex-end', justifyContent: 'space-between', gap: 1, mt: 0.5 }}
      >
        <Typography
          variant="h3"
          sx={{ fontSize: { xs: 32, md: 38 }, lineHeight: 1.05, fontVariantNumeric: 'tabular-nums' }}
        >
          {value}
        </Typography>

        {trend && trendLabel && (
          <Box sx={{ display: { xs: 'none', sm: 'block' }, pb: 0.5 }}>
            <Sparkline
              values={trend}
              color={theme.palette[tone].main}
              label={trendLabel}
            />
          </Box>
        )}
      </Stack>

      <Typography variant="caption" color="text.secondary">
        {hint}
      </Typography>
    </Paper>
  );
}

function Panel({ title, subtitle, children }: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}) {
  return (
    <Paper sx={{ p: 2.5, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Typography variant="subtitle2">{title}</Typography>
      {subtitle && (
        <Typography variant="caption" color="text.secondary">
          {subtitle}
        </Typography>
      )}
      <Divider sx={{ mt: 1.5, mb: 2 }} />
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, flexGrow: 1 }}>
        {children}
      </Box>
    </Paper>
  );
}

/** "2026-08" -> "Aug 26"; eksen etiketi dar ekranda da sigmali. */
function shortMonth(value: string): string {
  const [year, month] = value.split('-');
  const date = new Date(Number(year), Number(month) - 1, 1);

  return `${date.toLocaleString('en', { month: 'short' })} ${year.slice(2)}`;
}

export function DashboardPage() {
  const navigate = useNavigate();
  const [data, setData] = useState<DashboardOverview | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    dashboardApi.overview()
      .then((overview) => {
        if (active) setData(overview);
      })
      .catch((cause) => {
        if (active) setError(errorMessage(cause));
      });

    return () => {
      active = false;
    };
  }, []);

  if (error) {
    return <Alert severity="error">{error}</Alert>;
  }

  if (!data) {
    return (
      <Grid container spacing={2.5}>
        {Array.from({ length: 4 }).map((_, index) => (
          <Grid key={index} size={{ xs: 6, md: 3 }}>
            <Skeleton variant="rounded" height={120} />
          </Grid>
        ))}
        <Grid size={{ xs: 12, md: 7 }}>
          <Skeleton variant="rounded" height={280} />
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <Skeleton variant="rounded" height={280} />
        </Grid>
      </Grid>
    );
  }

  const {
    headcount, byDepartment, turnoverByMonth, hiresByMonth, terminationReasons,
    spanOfControl, dataQuality, managerLoad,
  } = data;

  const hireTrend = hiresByMonth.map((row) => row.hires);
  const leaverTrend = turnoverByMonth.map((row) => row.leavers);

  const maxDepartment = Math.max(...byDepartment.map((row) => row.active), 1);
  const maxMonthly = Math.max(...turnoverByMonth.map((row) => row.leavers), 1);
  const maxReason = Math.max(...terminationReasons.map((row) => row.count), 1);
  const hasFindings = dataQuality.emptyDepartments > 0;

  return (
    <Stack spacing={2.5}>
      {/* Gradyanli bant ve uzerindeki iki radyal isik KALDIRILDI.
          Sayfanin en ustunde duran renkli bir blok, altindaki her seyi
          dekorun devami gibi gosteriyordu. Baslik artik diger sayfalarla
          ayni bilesenden geliyor: hiyerarsiyi renk degil TIPOGRAFI kuruyor. */}
      <PageHeader
        eyebrow="Overview"
        title={`${headcount.active} people on the team`}
        description={`${headcount.hiredLast90Days} joined and ${headcount.leftLast12Months} left over the past year`}
        actions={(
          <Button
            variant="outlined"
            startIcon={<GroupsOutlinedIcon />}
            onClick={() => navigate('/employees')}
          >
            Employee list
          </Button>
        )}
      />

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 6, md: 3 }}>
          <StatCard
            label="Active"
            value={headcount.active}
            hint={`${headcount.inactive} inactive on record`}
            icon={<GroupsOutlinedIcon fontSize="small" />}
            tone="primary"
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <StatCard
            label="Joined"
            value={headcount.hiredLast90Days}
            hint={`last 90 days · ${headcount.hiredLast30Days} in the last 30`}
            icon={<TrendingUpOutlinedIcon fontSize="small" />}
            tone="success"
            trend={hireTrend}
            trendLabel={`Monthly hires over the last 12 months, ending at ${hireTrend[hireTrend.length - 1]}`}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <StatCard
            label="Left"
            value={headcount.leftLast12Months}
            hint="last 12 months"
            icon={<LogoutOutlinedIcon fontSize="small" />}
            tone="error"
            trend={leaverTrend}
            trendLabel={`Monthly departures over the last 12 months, ending at ${leaverTrend[leaverTrend.length - 1]}`}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <StatCard
            label="Turnover"
            value={`${headcount.turnoverRate}%`}
            hint="leavers ÷ active headcount"
            icon={<AutorenewOutlinedIcon fontSize="small" />}
            tone={headcount.turnoverRate >= 20 ? 'warning' : 'primary'}
          />
        </Grid>

        <Grid size={{ xs: 12, md: 7 }}>
          <Panel title="Headcount by department" subtitle="Active employees only">
            {byDepartment.map((row) => (
              <BarRow
                key={row.department}
                label={row.department}
                value={row.active}
                max={maxDepartment}
                muted={row.active === 0}
              />
            ))}
          </Panel>
        </Grid>

        <Grid size={{ xs: 12, md: 5 }}>
          <Panel title="Why people left" subtitle="All recorded departures">
            {terminationReasons.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                Nobody has left yet.
              </Typography>
            ) : (
              terminationReasons.map((row) => (
                <BarRow
                  key={row.reason}
                  label={TERMINATION_REASON_LABELS[row.reason as TerminationReason] ?? row.reason}
                  value={row.count}
                  max={maxReason}
                />
              ))
            )}
          </Panel>
        </Grid>

        <Grid size={{ xs: 12 }}>
          <Panel
            title="Manager load"
            subtitle="Circle area is the size of the team; the number is inside it"
          >
            {/* Balon BURADA dogru, org chart'ta degil: orada sorulan sey
                hiyerarsi seviyesi, burada ise karsilastirma. */}
            <ManagerLoadLegend managers={managerLoad} />
            <ManagerLoad managers={managerLoad} />
          </Panel>
        </Grid>

        <Grid size={{ xs: 12, md: 7 }}>
          <Panel title="Departures by month" subtitle="Last 12 months">
            {/* Dikey sutunlar: zaman serisi soldan saga okunur ve aylar arasi
                karsilastirma yatay cubuklarda kaybolurdu. */}
            {/* Degerler ONCEDEN yalnizca Tooltip icindeydi ve ipucu
                odaklanilamayan bir Box'i sariyordu: klavyeyle ulasilamiyor,
                erisilebilirlik agacinda hic gorunmuyordu. Grafigin kendisine
                bir ad verilir; ayni sayfadaki Sparkline zaten boyle yapiyor. */}
            <Box
              role="img"
              aria-label={`Departures by month: ${turnoverByMonth
                .map((row) => `${shortMonth(row.month)} ${row.leavers}`)
                .join(', ')}`}
              sx={{
                display: 'grid',
                gridTemplateColumns: 'repeat(12, 1fr)',
                alignItems: 'end',
                gap: 0.5,
                height: 150,
              }}
            >
              {turnoverByMonth.map((row) => (
                <Tooltip
                  key={row.month}
                  title={`${shortMonth(row.month)}: ${row.leavers} ${row.leavers === 1 ? 'person' : 'people'}`}
                >
                  <Box
                    sx={{
                      // Sifir olan ay da bir cizgi olarak gorunur: "veri yok"
                      // ile "kimse ayrilmadi" ayni sey degildir.
                      height: `${Math.max((row.leavers / maxMonthly) * 100, 3)}%`,
                      // Gradyan yerine DUZ renk. Yogunluk bilgi tasir: en
                      // yuksek ay tam doygunlukta, digerleri sonuk -- boylece
                      // zirve dekorla degil veriyle one cikar.
                      backgroundColor: (t) => (row.leavers > 0
                        ? alpha(t.palette.primary.main, row.leavers === maxMonthly ? 1 : 0.45)
                        : t.palette.action.disabledBackground),
                      borderRadius: 0.5,
                      // Zirve ay ONCEDEN yalnizca opaklikla belirtiliyordu;
                      // renk tek basina bilgi tasiyamaz. Ustteki ince cizgi
                      // ayni bilgiyi BICIMLE de veriyor.
                      borderTop: row.leavers === maxMonthly && row.leavers > 0 ? 2 : 0,
                      borderColor: 'text.primary',
                      transition: 'height 240ms ease',
                      '@media (prefers-reduced-motion: reduce)': { transition: 'none' },
                    }}
                  />
                </Tooltip>
              ))}
            </Box>

            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(12, 1fr)', gap: 0.5, mt: 1 }}>
              {turnoverByMonth.map((row, index) => (
                <Typography
                  key={row.month}
                  variant="caption"
                  color="text.secondary"
                  sx={{
                    textAlign: 'center',
                    fontSize: 10,
                    // Dar ekranda on iki etiket sigmaz; ikide bir gosterilir.
                    display: { xs: index % 3 === 0 ? 'block' : 'none', sm: 'block' },
                  }}
                >
                  {shortMonth(row.month).split(' ')[0]}
                </Typography>
              ))}
            </Box>
          </Panel>
        </Grid>

        <Grid size={{ xs: 12, md: 5 }}>
          <Stack spacing={2.5} sx={{ height: '100%' }}>
            <Panel title="Span of control" subtitle="How many people each manager carries">
              <Stack direction="row" spacing={3} sx={{ flexWrap: 'wrap' }}>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 680, fontVariantNumeric: 'tabular-nums' }}>
                    {spanOfControl.managerCount}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    managers
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 680, fontVariantNumeric: 'tabular-nums' }}>
                    {spanOfControl.averageDirectReports}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    reports on average
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 680, fontVariantNumeric: 'tabular-nums' }}>
                    {spanOfControl.largestTeam}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    largest team
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 680, fontVariantNumeric: 'tabular-nums' }}>
                    {dataQuality.activeWithoutManager}
                  </Typography>
                  <Tooltip title="Either the top of the organisation or a missing manager — the system cannot tell the two apart">
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ borderBottom: '1px dotted', cursor: 'help' }}
                    >
                      report to nobody
                    </Typography>
                  </Tooltip>
                </Box>
              </Stack>
            </Panel>

            <Panel title="Needs attention" subtitle="Findings you can act on">
              {!hasFindings && (
                <Typography variant="body2" color="text.secondary">
                  Nothing to flag right now.
                </Typography>
              )}

              {/* Yoneticisiz personel BURADA DEGIL: onlar cogunlukla
                  organizasyonun tepesidir ve uyari olarak gostermek yanlis
                  alarm uretir. Ayirt edecek bir bilgi saklamiyoruz. */}
              {dataQuality.emptyDepartments > 0 && (
                <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                  <Chip label={dataQuality.emptyDepartments} size="small" color="warning" />
                  <Typography variant="body2" sx={{ flexGrow: 1 }}>
                    departments have nobody in them
                  </Typography>
                  <Button size="small" onClick={() => navigate('/employees')}>
                    Review
                  </Button>
                </Stack>
              )}
            </Panel>
          </Stack>
        </Grid>
      </Grid>
    </Stack>
  );
}
