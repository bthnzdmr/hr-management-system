import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert, Button, Chip, Paper, Skeleton, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TablePagination, TableRow, ToggleButton, ToggleButtonGroup,
  Typography,
} from '@mui/material';
import EventBusyOutlinedIcon from '@mui/icons-material/EventBusyOutlined';
import { leaveRequestApi } from '../api/leaveRequests';
import type { LeaveRequest, LeaveStatus } from '../api/leaveRequests';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/PageHeader';
import { EmptyState } from '../components/EmptyState';
import { LeaveFormDialog } from '../components/LeaveFormDialog';
import { useSnackbar } from '../components/SnackbarProvider';

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
  const { canEditEmployees, canDecideLeave } = useAuth();
  const { notify } = useSnackbar();

  const [rows, setRows] = useState<LeaveRequest[] | null>(null);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [filter, setFilter] = useState<'PENDING' | 'ALL'>('PENDING');
  const [error, setError] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  /** Karar bekleyen SATIR; global bir bayrak butun satirlari kilitlerdi. */
  const [deciding, setDeciding] = useState<number | null>(null);

  /** Her istege bir sira numarasi. */
  const requestId = useRef(0);

  const load = useCallback(async () => {
    const id = requestId.current + 1;
    requestId.current = id;

    setError(null);

    try {
      const data = await leaveRequestApi.list({
        status: filter === 'PENDING' ? ['PENDING'] : undefined,
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
  }, [filter, page, size]);

  useEffect(() => {
    void load();
  }, [load]);

  const decide = async (leave: LeaveRequest, status: 'APPROVED' | 'REJECTED') => {
    setDeciding(leave.id);

    try {
      await leaveRequestApi.decide(leave.id, status);
      notify(`Request ${status === 'APPROVED' ? 'approved' : 'rejected'}`, 'success');
      await load();
    } catch (cause) {
      notify(errorMessage(cause), 'error');
    } finally {
      setDeciding(null);
    }
  };

  return (
    <Stack spacing={2.5}>
      <PageHeader
        eyebrow="People"
        title="Leave"
        description="Time off recorded for employees, and requests waiting for a decision"
        actions={canEditEmployees ? (
          <Button variant="contained" onClick={() => setFormOpen(true)}>
            Record leave
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
              setFilter(next);
              // Filtre degisince sayfa basa doner; yoksa 3. sayfada bos bir
              // liste gorunur ve kullanici "kayit yok" saniyordu.
              setPage(0);
            }}
            aria-label="Filter requests"
          >
            {FILTERS.map((option) => (
              <ToggleButton key={option.value} value={option.value}>
                {option.label}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>

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
              title={filter === 'PENDING' ? 'Nothing to decide' : 'No leave recorded yet'}
              description={filter === 'PENDING'
                ? 'Every request has been dealt with.'
                : 'Leave recorded for employees will appear here.'}
              action={filter === 'PENDING' ? (
                <Button onClick={() => setFilter('ALL')}>Show all requests</Button>
              ) : undefined}
            />
          )}

          {rows !== null && rows.length > 0 && (
            <>
              <TableContainer sx={{ overflowX: 'auto' }}>
                <Table>
                  <TableHead>
                    <TableRow>
                      {['Employee', 'Type', 'Dates', 'Days', 'Status', 'Decided by'].map((column) => (
                        <TableCell key={column}>{column}</TableCell>
                      ))}
                      {canDecideLeave && <TableCell align="right">Actions</TableCell>}
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
                        </TableCell>

                        {canDecideLeave && (
                          <TableCell align="right">
                            {leave.status === 'PENDING' ? (
                              <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
                                <Button
                                  size="small"
                                  // Mesgul bayragi SATIR BASINA: global olsaydi
                                  // bir satirin istegi surerken butun tablo
                                  // kilitlenirdi.
                                  disabled={deciding === leave.id}
                                  onClick={() => decide(leave, 'REJECTED')}
                                >
                                  Reject
                                </Button>
                                <Button
                                  size="small"
                                  variant="contained"
                                  disabled={deciding === leave.id}
                                  onClick={() => decide(leave, 'APPROVED')}
                                >
                                  Approve
                                </Button>
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

              <TablePagination
                component="div"
                count={total}
                page={page}
                rowsPerPage={size}
                rowsPerPageOptions={[10, 20, 50]}
                onPageChange={(_, next) => setPage(next)}
                onRowsPerPageChange={(event) => {
                  setSize(Number(event.target.value));
                  setPage(0);
                }}
              />
            </>
          )}
        </Stack>
      </Paper>

      <LeaveFormDialog
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false);
          notify('Leave recorded', 'success');
          void load();
        }}
      />
    </Stack>
  );
}
