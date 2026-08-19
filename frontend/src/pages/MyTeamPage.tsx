import { useCallback, useEffect, useRef, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert, Box, Button, Chip, Divider, Link, Paper, Skeleton, Stack, Typography,
} from '@mui/material';
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined';
import { employeeApi } from '../api/employees';
import { leaveRequestApi } from '../api/leaveRequests';
import type { LeaveRequest } from '../api/leaveRequests';
import type { Employee } from '../types/api';
import { errorMessage } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { EmptyState } from '../components/EmptyState';
import { InitialsAvatar } from '../components/InitialsAvatar';
import { LeaveCalendar } from '../components/LeaveCalendar';
import { currentMonth, monthWindow } from '../components/leaveTimeline';
import { RejectLeaveDialog } from '../components/RejectLeaveDialog';
import { useBusyRows } from '../hooks/useBusyRows';
import { useSnackbar } from '../components/SnackbarProvider';
import { useAuth } from '../auth/AuthContext';
import { formatDay } from '../utils/formatDate';

/**
 * Yoneticinin kendi ekrani.
 *
 * NEDEN VAR: `MANAGER` rolu giris yapinca herkesle ayni personel listesine
 * dusuyordu. Oysa arkada tasarlanmis bir sey var -- TEAM kapsami, torun
 * sizintisinin kapatilmasi, "kendi iznine karar veremezsin" kurali -- ve
 * bunlarin arayuzde hicbir karsiligi yoktu. Bu sayfa yeni bir yetki
 * ACMIYOR; var olani gorunur kiliyor.
 *
 * YENI LISTE UCU YOK ve gerekmedi: olculdu, bir yonetici icin
 * `GET /api/employees` zaten tam olarak kendisi ve dogrudan astlarini
 * donduruyor. Kapsam ZATEN ekibin tanimi.
 */
export function MyTeamPage() {
  const { canDecideLeave } = useAuth();
  const { notify } = useSnackbar();

  const [me, setMe] = useState<Employee | null>(null);
  const [team, setTeam] = useState<Employee[] | null>(null);
  const [pending, setPending] = useState<LeaveRequest[]>([]);
  const [offThisMonth, setOffThisMonth] = useState<LeaveRequest[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState<LeaveRequest | null>(null);

  const busyRows = useBusyRows();
  const month = currentMonth();
  const requestId = useRef(0);

  const load = useCallback(async () => {
    const id = requestId.current + 1;
    requestId.current = id;

    setError(null);

    try {
      const window = monthWindow(month);

      // Uc istek AYNI anda: sirali beklemek sayfayi uc kat yavaslatirdi ve
      // aralarinda bagimlilik yok.
      const [self, everyone, awaiting, off] = await Promise.all([
        employeeApi.getMe(),
        employeeApi.list({ page: 0, size: 100, active: true }),
        leaveRequestApi.list({ status: ['PENDING'], size: 50 }),
        leaveRequestApi.list({
          status: ['APPROVED', 'PENDING'],
          from: window.from,
          until: window.until,
          size: 100,
        }),
      ]);

      // Bayat cevap korumasi: kullanici sayfadan cikip donerse eski cevap
      // yenisinin uzerine yazmamali.
      if (requestId.current !== id) return;

      setMe(self);
      // Kapsam kendisini DE donduruyor; "bana rapor verenler" listesinde
      // kendini gormek sacma olurdu ve sayiyi bir fazla gosterirdi.
      setTeam(everyone.content.filter((person) => person.id !== self.id));
      setPending(awaiting.content);
      setOffThisMonth(off.content);
    } catch (cause) {
      if (requestId.current !== id) return;

      setError(errorMessage(cause));
      setTeam([]);
    }
  }, [month]);

  useEffect(() => {
    void load();
  }, [load]);

  const decide = async (leave: LeaveRequest, status: 'APPROVED' | 'REJECTED', note?: string) => {
    busyRows.start(leave.id);
    setRejecting(null);

    try {
      await leaveRequestApi.decide(leave.id, status, note);
      notify(status === 'APPROVED' ? 'Request approved' : 'Request rejected', 'success');
      await load();
    } catch (cause) {
      notify(errorMessage(cause), 'error');
    } finally {
      busyRows.finish(leave.id);
    }
  };

  return (
    <Stack spacing={2.5}>
      <PageHeader
        eyebrow="People"
        title="My team"
        description={me
          ? `${me.firstName} ${me.lastName} — ${me.jobTitle}`
          : 'The people who report to you, and what needs your decision'}
      />

      {error && <Alert severity="error">{error}</Alert>}

      {/* Karar bekleyenler EN USTTE: bu sayfanin varlik sebebi, yoneticinin
          uzerinde bekleyen isi gormesi. Liste bosken de gosteriliyor, cunku
          "bekleyen yok" bir cevaptir. */}
      <Paper sx={{ p: { xs: 2, md: 2.5 } }}>
        <Typography variant="subtitle1" sx={{ mb: 1.5 }}>
          Waiting for your decision
        </Typography>

        {pending.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            Nothing is waiting. Requests from your team appear here.
          </Typography>
        ) : (
          <Stack divider={<Divider flexItem />}>
            {pending.map((leave) => (
              <Stack
                key={leave.id}
                direction={{ xs: 'column', sm: 'row' }}
                spacing={1.5}
                sx={{ py: 1.25, alignItems: { sm: 'center' } }}
              >
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography variant="body2">{leave.employeeFullName}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {leave.startDate === leave.endDate
                      ? formatDay(leave.startDate)
                      : `${formatDay(leave.startDate)} → ${formatDay(leave.endDate)}`}
                    {' · '}{leave.days} {leave.days === 1 ? 'day' : 'days'}
                    {leave.note ? ` · ${leave.note}` : ''}
                  </Typography>
                </Box>

                {canDecideLeave && (
                  <Stack direction="row" spacing={1}>
                    <Button
                      size="small"
                      // Mesgul bayragi SATIR BASINA: global olsaydi bir
                      // satirin istegi surerken butun liste kilitlenirdi.
                      disabled={busyRows.isBusy(leave.id)}
                      onClick={() => setRejecting(leave)}
                    >
                      Reject
                    </Button>
                    <Button
                      size="small"
                      variant="contained"
                      disabled={busyRows.isBusy(leave.id)}
                      onClick={() => decide(leave, 'APPROVED')}
                    >
                      Approve
                    </Button>
                  </Stack>
                )}
              </Stack>
            ))}
          </Stack>
        )}
      </Paper>

      <Paper sx={{ p: { xs: 2, md: 2.5 } }}>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}
        >
          <Typography variant="subtitle1">
            Reporting to you{team ? ` (${team.length})` : ''}
          </Typography>
          <Link component={RouterLink} to="/leave" variant="body2">
            All leave
          </Link>
        </Stack>

        {team === null && (
          <Stack spacing={1}>
            {Array.from({ length: 3 }).map((_, index) => (
              <Skeleton key={index} variant="rounded" height={44} />
            ))}
          </Stack>
        )}

        {team !== null && team.length === 0 && error === null && (
          <EmptyState
            icon={<GroupsOutlinedIcon />}
            title="Nobody reports to you yet"
            description="When someone is assigned to you as their manager, they appear here."
          />
        )}

        {team !== null && team.length > 0 && (
          <Stack divider={<Divider flexItem />}>
            {team.map((person) => (
              <Stack
                key={person.id}
                direction="row"
                spacing={1.5}
                sx={{ py: 1.25, alignItems: 'center' }}
              >
                <InitialsAvatar firstName={person.firstName} lastName={person.lastName} />
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Link
                    component={RouterLink}
                    to={`/employees/${person.id}/details`}
                    variant="body2"
                  >
                    {person.firstName} {person.lastName}
                  </Link>
                  <Typography variant="caption" color="text.secondary" display="block">
                    {person.jobTitle} · {person.departmentName}
                  </Typography>
                </Box>
                <Chip
                  size="small"
                  variant="outlined"
                  label={person.active ? 'Active' : 'Left'}
                  color={person.active ? 'default' : 'error'}
                />
              </Stack>
            ))}
          </Stack>
        )}
      </Paper>

      {/* Ayni takvim bileseni, kapsami sunucu daraltiyor: yonetici burada
          yalnizca kendi ekibini gorur. Kural arayuzde TEKRARLANMIYOR. */}
      {offThisMonth.length > 0 && (
        <Paper sx={{ p: { xs: 2, md: 2.5 } }}>
          <Typography variant="subtitle1" sx={{ mb: 1.5 }}>
            Who is off this month
          </Typography>
          <LeaveCalendar leaves={offThisMonth} month={month} today={todayIso()} />
        </Paper>
      )}

      <RejectLeaveDialog
        open={rejecting !== null}
        employeeName={rejecting?.employeeFullName ?? ''}
        busy={rejecting !== null && busyRows.isBusy(rejecting.id)}
        onClose={() => setRejecting(null)}
        onConfirm={(reason) => {
          if (rejecting) void decide(rejecting, 'REJECTED', reason);
        }}
      />
    </Stack>
  );
}

/** Bugun, YEREL takvime gore; `toISOString()` UTC'ye cevirip gun kaydirirdi. */
function todayIso(): string {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');

  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}
