import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import {
  Alert, Box, Button, Chip, Divider, Grid, Paper, Skeleton, Stack, Tooltip, Typography, alpha,
} from '@mui/material';
import AutorenewOutlinedIcon from '@mui/icons-material/AutorenewOutlined';
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import TrendingUpOutlinedIcon from '@mui/icons-material/TrendingUpOutlined';
import { dashboardApi } from '../api/dashboard';
import type { DashboardOverview } from '../api/dashboard';
import { errorMessage } from '../api/client';
import { BarRow } from '../components/BarRow';
import { HOVER_LIFT } from '../theme/theme';
import { TERMINATION_REASON_LABELS } from '../types/api';
import type { TerminationReason } from '../types/api';

type Tone = 'primary' | 'success' | 'error' | 'warning';

function StatCard({ label, value, hint, icon, tone }: {
  label: string;
  value: string | number;
  hint: string;
  icon: ReactNode;
  tone: Tone;
}) {
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
        '&:hover': {
          ...HOVER_LIFT['&:hover'],
          borderColor: (t) => alpha(t.palette[tone].main, 0.5),
        },
      }}
    >
      {/* Kosede sonuk bir isik: duz bir dikdortgeni yuzeye cevirir. */}
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          top: -40,
          right: -40,
          width: 140,
          height: 140,
          borderRadius: '50%',
          pointerEvents: 'none',
          background: (t) =>
            `radial-gradient(circle, ${alpha(t.palette[tone].main, 0.18)}, transparent 70%)`,
        }}
      />

      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <Box
          sx={{
            width: 30,
            height: 30,
            borderRadius: 2,
            display: 'grid',
            placeItems: 'center',
            color: `${tone}.main`,
            bgcolor: (t) => alpha(t.palette[tone].main, 0.14),
          }}
        >
          {icon}
        </Box>
        <Typography variant="overline" color="text.secondary" sx={{ fontSize: 11 }}>
          {label}
        </Typography>
      </Stack>

      <Typography
        variant="h3"
        sx={{ fontSize: 40, lineHeight: 1.1, fontVariantNumeric: 'tabular-nums', mt: 0.5 }}
      >
        {value}
      </Typography>
      <Typography variant="body2" color="text.secondary">
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

  const { headcount, byDepartment, turnoverByMonth, terminationReasons, spanOfControl, dataQuality } = data;

  const maxDepartment = Math.max(...byDepartment.map((row) => row.active), 1);
  const maxMonthly = Math.max(...turnoverByMonth.map((row) => row.leavers), 1);
  const maxReason = Math.max(...terminationReasons.map((row) => row.count), 1);
  const hasFindings = dataQuality.emptyDepartments > 0;

  return (
    <Stack spacing={2.5}>
      {/* Baslik bandi sayfanin en ustunde bir "yer" duygusu kurar ve paletin
          koyu tonlarini TEK yerde yogunlastirir. Gradyan sayfanin geri
          kalanina yayilmaz; her yere konan gradyan gurultudur. */}
      <Paper
        sx={{
          p: { xs: 2.5, md: 3.5 },
          position: 'relative',
          overflow: 'hidden',
          border: 'none',
          background: 'linear-gradient(135deg, #1E2631 0%, #3B4859 100%)',
        }}
      >
        <Box
          aria-hidden
          sx={{
            position: 'absolute',
            top: -80,
            right: -60,
            width: 300,
            height: 300,
            borderRadius: '50%',
            background: `radial-gradient(circle, ${alpha('#C8937E', 0.3)}, transparent 65%)`,
          }}
        />
        <Box
          aria-hidden
          sx={{
            position: 'absolute',
            bottom: -140,
            left: '35%',
            width: 280,
            height: 280,
            borderRadius: '50%',
            background: `radial-gradient(circle, ${alpha('#709995', 0.22)}, transparent 65%)`,
          }}
        />

        <Box sx={{ position: 'relative' }}>
          <Typography variant="overline" sx={{ color: alpha('#F8FAFC', 0.7), fontSize: 11 }}>
            Overview
          </Typography>
          <Typography variant="h4" component="h1" sx={{ color: '#F8FAFC', mt: 0.5 }}>
            {headcount.active} people on the team
          </Typography>
          <Typography variant="body2" sx={{ color: alpha('#F8FAFC', 0.7), mt: 0.5 }}>
            {headcount.hiredLast90Days} joined and {headcount.leftLast12Months} left
            over the past year
          </Typography>
        </Box>
      </Paper>

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
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <StatCard
            label="Left"
            value={headcount.leftLast12Months}
            hint="last 12 months"
            icon={<LogoutOutlinedIcon fontSize="small" />}
            tone="error"
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

        <Grid size={{ xs: 12, md: 7 }}>
          <Panel title="Departures by month" subtitle="Last 12 months">
            {/* Dikey sutunlar: zaman serisi soldan saga okunur ve aylar arasi
                karsilastirma yatay cubuklarda kaybolurdu. */}
            <Box
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
                      // Gradyan: duz bir dikdortgen yerine yukari dogru acilan
                      // bir kutle; sutunun tepesi vurgulanir.
                      background: (t) => (row.leavers > 0
                        ? `linear-gradient(180deg, ${t.palette.primary.main}, ${alpha(t.palette.primary.main, 0.4)})`
                        : t.palette.action.disabledBackground),
                      borderRadius: 1.5,
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

      <Box>
        <Button startIcon={<GroupsOutlinedIcon />} onClick={() => navigate('/employees')}>
          Open the employee list
        </Button>
      </Box>
    </Stack>
  );
}
