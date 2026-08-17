import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Alert, Button, Chip, Paper, Skeleton, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TablePagination, TableRow, ToggleButton, ToggleButtonGroup,
  TextField, Typography, useMediaQuery, useTheme,
} from '@mui/material';
import EventBusyOutlinedIcon from '@mui/icons-material/EventBusyOutlined';
import { leaveRequestApi } from '../api/leaveRequests';
import type { LeaveRequest, LeaveStatus } from '../api/leaveRequests';
import { employeeApi } from '../api/employees';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/PageHeader';
import { EmptyState } from '../components/EmptyState';
import { LeaveFormDialog } from '../components/LeaveFormDialog';
import { EmployeePicker } from '../components/EmployeePicker';
import type { EmployeeOption } from '../components/EmployeePicker';
import { useSnackbar } from '../components/SnackbarProvider';
import { LeaveCard } from '../components/LeaveCard';
import { RejectLeaveDialog } from '../components/RejectLeaveDialog';
import { useBusyRows } from '../hooks/useBusyRows';

/** Durum -> etiket ve renk. Tek tanim: iki yerde tutulsa biri geride kalirdi. */
const STATUS_LABELS: Record<LeaveStatus, { label: string; color: 'default' | 'success' | 'error' | 'warning' }> = {
  PENDING: { label: 'Pending', color: 'warning' },
  APPROVED: { label: 'Approved', color: 'success' },
  REJECTED: { label: 'Rejected', color: 'error' },
  CANCELLED: { label: 'Cancelled', color: 'default' },
};

const TYPE_LABELS: Record<LeaveRequest['type'], string> = {
  ANNUAL: 'Annual',
  SICK: 'Sick',
  UNPAID: 'Unpaid',
  PARENTAL: 'Parental',
};

/** Kullanicinin gordugu filtre secenekleri. */
const FILTERS: { value: 'PENDING' | 'ALL'; label: string }[] = [
  { value: 'PENDING', label: 'Awaiting a decision' },
  { value: 'ALL', label: 'All requests' },
];

export function LeaveListPage() {
  const { canEditEmployees, canDecideLeave, canRequestLeave } = useAuth();
  const theme = useTheme();
  // TEK gorunum render edilir. Ikisini birden cizip birini gizlemek her
  // erisilebilir adi DOM'da iki kez birakirdi -- proje bunu bir kez olctu.
  const isNarrow = useMediaQuery(theme.breakpoints.down('md'));
  const { notify } = useSnackbar();

  // Filtreler adres cubugunda yasar, bilesende degil: baska bir sayfadan
  // gelen baglanti ancak URL uzerinden filtre kurabilir. Yan faydasi geri
  // dugmesi ve yer imi.
  const [params, setParams] = useSearchParams();
  const filter: 'PENDING' | 'ALL' = params.get('status') === 'ALL' ? 'ALL' : 'PENDING';
  const employeeId = Number(params.get('employee')) || undefined;
  const from = params.get('from') ?? '';
  const until = params.get('until') ?? '';
  const page = Number(params.get('page')) || 0;
  const filtered = employeeId !== undefined || from !== '' || until !== '';

  const [rows, setRows] = useState<LeaveRequest[] | null>(null);
  const [total, setTotal] = useState(0);
  const [size, setSize] = useState(20);
  const [error, setError] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  /** Reddedilmek uzere secilen satir; gerekce penceresi bunun uzerinden acilir. */
  const [rejecting, setRejecting] = useState<LeaveRequest | null>(null);
  /** URL yalnizca id tasir; secim kutusunun gosterecegi ad buradan cozulur. */
  const [person, setPerson] = useState<EmployeeOption | null>(null);
  /** Karar bekleyen SATIR; global bir bayrak butun satirlari kilitlerdi. */
  const busyRows = useBusyRows();

  /**
   * Sayfa icindeki filtre degisiklikleri gecmise yazilmaz (`replace`): tarih
   * kutusu her duzenlemede yeni bir kayit birakir ve geri dugmesi detay
   * sayfasina donmek icin defalarca basilmayi gerektirirdi.
   */
  const setFilters = (changes: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params);

    Object.entries(changes).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });

    // Filtre degisince sayfa basa doner; yoksa 3. sayfada bos bir liste
    // gorunur ve kullanici "kayit yok" saniyordu.
    if (!('page' in changes)) next.delete('page');

    setParams(next, { replace: true });
  };

  useEffect(() => {
    if (employeeId === undefined) {
      setPerson(null);
      return;
    }

    if (person?.id === employeeId) return;

    let active = true;

    employeeApi
      .getById(employeeId)
      .then((employee) => {
        if (active) {
          setPerson({
            id: employee.id,
            label: `${employee.firstName} ${employee.lastName} — ${employee.jobTitle}`,
          });
        }
      })
      .catch(() => {
        // Kapsam disindaki bir id elle yazilmis olabilir. Filtre yine de
        // GORUNUR kalmali, yoksa liste sebepsiz bos gorunurdu.
        if (active) setPerson({ id: employeeId, label: `Employee #${employeeId}` });
      });

    return () => {
      active = false;
    };
  }, [employeeId, person?.id]);

  /** Her istege bir sira numarasi. */
  const requestId = useRef(0);

  const load = useCallback(async () => {
    const id = requestId.current + 1;
    requestId.current = id;

    setError(null);

    try {
      const data = await leaveRequestApi.list({
        status: filter === 'PENDING' ? ['PENDING'] : undefined,
        // Adi degil ID'yi bekler: liste, secim kutusunun adi cozmesini
        // beklemeden yuklenmelidir.
        employeeId,
        from,
        until,
        page,
        size,
      });

      if (requestId.current !== id) return;

      setRows(data.content);
      setTotal(data.totalElements);
    } catch (cause) {
      if (requestId.current !== id) return;

      setError(errorMessage(cause));
      setRows([]);
    }
  }, [filter, employeeId, from, until, page, size]);

  useEffect(() => {
    void load();
  }, [load]);

  const DONE: Record<Exclude<LeaveStatus, 'PENDING'>, string> = {
    APPROVED: 'approved',
    REJECTED: 'rejected',
    CANCELLED: 'cancelled',
  };

  const decide = async (
    leave: LeaveRequest,
    status: Exclude<LeaveStatus, 'PENDING'>,
    reason?: string,
  ) => {
    busyRows.start(leave.id);
    setRejecting(null);

    try {
      await leaveRequestApi.decide(leave.id, status, reason);
      notify(`Request ${DONE[status]}`, 'success');
      await load();
    } catch (cause) {
      notify(errorMessage(cause), 'error');
    } finally {
      busyRows.finish(leave.id);
    }
  };

  /**
   * Bos listenin UC ayri hali vardir ve her birinin cevabi farklidir. Suzgecle
   * bosalan bir listeye cikis yolu birakmamak personel listesinde bir kez
   * olculmustu.
   */
  const empty = filtered
    ? {
      title: 'No leave matches these filters',
      description: 'Nobody in your scope has leave in that range.',
      action: <Button onClick={() => setFilters({ employee: undefined, from: undefined, until: undefined })}>
        Clear filters
      </Button>,
    }
    : filter === 'PENDING'
      ? {
        title: 'Nothing to decide',
        description: 'Every request has been dealt with.',
        action: <Button onClick={() => setFilters({ status: 'ALL' })}>Show all requests</Button>,
      }
      : {
        title: 'No leave recorded yet',
        description: 'Leave recorded for employees will appear here.',
        action: undefined,
      };

  return (
    <Stack spacing={2.5}>
      <PageHeader
        eyebrow="People"
        title="Leave"
        description="Time off recorded for employees, and requests waiting for a decision"
        actions={canRequestLeave ? (
          <Button variant="contained" onClick={() => setFormOpen(true)}>
            {canEditEmployees ? 'Record leave' : 'Request leave'}
          </Button>
        ) : undefined}
      />

      {error && <Alert severity="error">{error}</Alert>}

      <Paper sx={{ p: { xs: 2, md: 2.5 } }}>
        <Stack spacing={2}>
          <ToggleButtonGroup
            exclusive
            size="small"
            value={filter}
            onChange={(_, next) => {
              if (next === null) return;
              setFilters({ status: next === 'ALL' ? 'ALL' : undefined });
            }}
            aria-label="Filter requests"
          >
            {FILTERS.map((option) => (
              <ToggleButton key={option.value} value={option.value}>
                {option.label}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>

          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <EmployeePicker
              value={person}
              onChange={(next) => {
                setPerson(next);
                setFilters({ employee: next ? String(next.id) : undefined });
              }}
              label="Whose leave"
              helperText="Leave it empty to see everyone in your scope"
            />
            <TextField
              type="date"
              label="From"
              value={from}
              onChange={(event) => setFilters({ from: event.target.value })}
              slotProps={{ inputLabel: { shrink: true } }}
              size="small"
            />
            <TextField
              type="date"
              label="Until"
              value={until}
              onChange={(event) => setFilters({ until: event.target.value })}
              slotProps={{ inputLabel: { shrink: true } }}
              size="small"
              helperText="Overlapping leave, not only leave starting here"
            />
          </Stack>

          {rows === null && (
            <Stack spacing={1}>
              {Array.from({ length: 5 }).map((_, index) => (
                <Skeleton key={index} variant="rounded" height={44} />
              ))}
            </Stack>
          )}

          {/* Basarisiz YUKLEME ile GERCEKTEN BOS liste ayri hallerdir. Ayrilmasaydi
              403 alan bir kullanici ayni ekranda hem hatayi hem "karar bekleyen
              yok" yazisini gorurdu -- ikincisi yanlis bir guvence. */}
          {rows !== null && rows.length === 0 && error === null && (
            <EmptyState
              icon={<EventBusyOutlinedIcon />}
              title={empty.title}
              description={empty.description}
              action={empty.action}
            />
          )}

          {rows !== null && rows.length > 0 && (
            <>
              {isNarrow ? (
                <Stack spacing={1.25}>
                  {rows.map((leave) => (
                    <LeaveCard
                      key={leave.id}
                      leave={leave}
                      statusLabel={STATUS_LABELS[leave.status]}
                      typeLabel={TYPE_LABELS[leave.type]}
                      canDecide={canDecideLeave}
                      busy={busyRows.isBusy(leave.id)}
                      onDecide={(status) => decide(leave, status)}
                      onReject={() => setRejecting(leave)}
                    />
                  ))}
                </Stack>
              ) : (
              <TableContainer sx={{ overflowX: 'auto' }}>
                <Table>
                  <TableHead>
                    <TableRow>
                      {['Employee', 'Type', 'Dates', 'Days', 'Status', 'Decided by'].map((column) => (
                        <TableCell key={column}>{column}</TableCell>
                      ))}
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>

                  <TableBody>
                    {rows.map((leave) => (
                      <TableRow key={leave.id} hover>
                        <TableCell>{leave.employeeFullName}</TableCell>
                        <TableCell>{TYPE_LABELS[leave.type]}</TableCell>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>
                          {leave.startDate === leave.endDate
                            ? leave.startDate
                            : `${leave.startDate} → ${leave.endDate}`}
                        </TableCell>
                        <TableCell>{leave.days}</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={STATUS_LABELS[leave.status].label}
                            color={STATUS_LABELS[leave.status].color}
                          />
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" color="text.secondary">
                            {leave.decidedBy ?? '—'}
                          </Typography>
                          {leave.decisionNote && (
                            <Typography variant="caption" color="text.secondary">
                              {leave.decisionNote}
                            </Typography>
                          )}
                        </TableCell>

                        {(
                          <TableCell align="right">
                            {leave.status === 'PENDING' ? (
                              <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
                                {/* Geri cekmek karar vermek DEGILDIR: kisi
                                    kendi talebinden vazgecebilir. Calisanin
                                    listesi zaten yalnizca kendi kayitlarini
                                    tasidigi icin "gorunur + bekliyor" dogru
                                    olcuttur. */}
                                <Button
                                  size="small"
                                  color="inherit"
                                  disabled={busyRows.isBusy(leave.id)}
                                  onClick={() => decide(leave, 'CANCELLED')}
                                >
                                  Cancel
                                </Button>
                                {canDecideLeave && (
                                  <>
                                    <Button
                                      size="small"
                                      // Mesgul bayragi SATIR BASINA: global
                                      // olsaydi bir satirin istegi surerken
                                      // butun tablo kilitlenirdi.
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
                                  </>
                                )}
                              </Stack>
                            ) : (
                              <Typography variant="body2" color="text.disabled">—</Typography>
                            )}
                          </TableCell>
                        )}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              )}

              <TablePagination
                component="div"
                count={total}
                page={page}
                rowsPerPage={size}
                rowsPerPageOptions={[10, 20, 50]}
                onPageChange={(_, next) => setFilters({ page: next ? String(next) : undefined })}
                onRowsPerPageChange={(event) => {
                  setSize(Number(event.target.value));
                  setFilters({ page: undefined });
                }}
              />
            </>
          )}
        </Stack>
      </Paper>

      <RejectLeaveDialog
        open={rejecting !== null}
        employeeName={rejecting?.employeeFullName ?? ''}
        busy={rejecting !== null && busyRows.isBusy(rejecting.id)}
        onClose={() => setRejecting(null)}
        onConfirm={(reason) => {
          if (rejecting) void decide(rejecting, 'REJECTED', reason);
        }}
      />

      <LeaveFormDialog
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false);
          notify(canEditEmployees ? 'Leave recorded' : 'Leave requested', 'success');
          void load();
        }}
      />
    </Stack>
  );
}
