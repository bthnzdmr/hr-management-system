import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Box, Button, Chip, Divider, Grid, Paper, Skeleton, Stack, Tooltip, Typography,
} from '@mui/material';
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined';
import { dashboardApi } from '../api/dashboard';
import type { DashboardOverview } from '../api/dashboard';
import { errorMessage } from '../api/client';
import { BarRow } from '../components/BarRow';
import { TERMINATION_REASON_LABELS } from '../types/api';
import type { TerminationReason } from '../types/api';

function StatCard({ label, value, hint, accent }: {
  label: string;
  value: string | number;
  hint: string;
  accent?: 'default' | 'warning';
}) {
  return (
    <Paper sx={{ p: 2.5, height: '100%', display: 'flex', flexDirection: 'column', gap: 0.5 }}>
      <Typography variant="caption" sx={{ textTransform: 'uppercase', letterSpacing: '0.05em', fontWeight: 650 }} color="text.secondary">
        {label}
      </Typography>
      <Typography
        variant="h4"
        sx={{
          fontWeight: 680,
          letterSpacing: '-0.02em',
          fontVariantNumeric: 'tabular-nums',
          color: accent === 'warning' ? 'warning.main' : 'text.primary',
        }}
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
      <Box>
        <Typography variant="h5" component="h1">
          Overview
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Headcount, turnover and organisation structure
        </Typography>
      </Box>

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 6, md: 3 }}>
          <StatCard
            label="Active"
            value={headcount.active}
            hint={`${headcount.inactive} inactive on record`}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <StatCard
            label="Joined"
            value={headcount.hiredLast90Days}
            hint={`last 90 days · ${headcount.hiredLast30Days} in the last 30`}
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <StatCard
            label="Left"
            value={headcount.leftLast12Months}
            hint="last 12 months"
          />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <StatCard
            label="Turnover"
            value={`${headcount.turnoverRate}%`}
            hint="leavers ÷ active headcount"
            accent={headcount.turnoverRate >= 20 ? 'warning' : 'default'}
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
                      bgcolor: row.leavers > 0 ? 'primary.main' : 'action.disabledBackground',
                      borderRadius: 1,
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
