import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Alert, Box, Button, Chip, IconButton, MenuItem, Paper, Skeleton, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField, ToggleButton,
  ToggleButtonGroup, Typography, alpha, useMediaQuery, useTheme,
} from '@mui/material';
import EventBusyOutlinedIcon from '@mui/icons-material/EventBusyOutlined';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { leaveRequestApi } from '../api/leaveRequests';
import type { LeaveRequest, LeaveStatus } from '../api/leaveRequests';
import { employeeApi } from '../api/employees';
import { departmentApi } from '../api/departments';
import type { Department } from '../types/api';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/PageHeader';
import { EmptyState } from '../components/EmptyState';
import { LeaveFormDialog } from '../components/LeaveFormDialog';
import { EmployeePicker } from '../components/EmployeePicker';
import { DateField } from '../components/DateField';
import type { EmployeeOption } from '../components/EmployeePicker';
import { useSnackbar } from '../components/SnackbarProvider';
import { LeaveCard } from '../components/LeaveCard';
import { RejectLeaveDialog } from '../components/RejectLeaveDialog';
import { useBusyRows } from '../hooks/useBusyRows';
import { formatDay } from '../utils/formatDate';
import { LeaveCalendar } from '../components/LeaveCalendar';
import { currentMonth, monthWindow, shiftMonth } from '../components/leaveTimeline';

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

const VIEWS: { value: 'list' | 'calendar'; label: string }[] = [
  { value: 'list', label: 'List' },
  { value: 'calendar', label: 'Calendar' },
];

/**
 * Ay adlari SABIT bir dizide; `Intl` kullanilmiyor.
 *
 * Yerel ayara bakilsaydi ayni ekran her makinede farkli basardi ve test
 * edilemezdi -- tarih ve ucret bicimlendirmesinde ayni karar iki kez verildi.
 */
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'];

function monthLabel(month: string): string {
  const [year, index] = month.split('-').map(Number);

  return `${MONTHS[index - 1]} ${year}`;
}

/**
 * Bugun, YEREL takvime gore.
 *
 * `toISOString()` KULLANILMAZ: o degeri UTC'ye cevirir ve negatif ofsetli bir
 * saat diliminde "bugun" ekranda bir onceki gun olarak isaretlenirdi -- ayni
 * tuzak `formatDay`'de bir kez olculdu.
 */
function todayIso(): string {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');

  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/** Aciklama kutucugu: serit BICIMININ ne anlama geldigini soyler. */
function Legend({ variant, label }: { variant: 'approved' | 'pending'; label: string }) {
  const pending = variant === 'pending';

  return (
    <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
      <Box
        sx={{
          width: 24,
          height: 14,
          borderRadius: 0.75,
          border: 1,
          borderStyle: pending ? 'dashed' : 'solid',
          bgcolor: (theme) => pending
            ? alpha(theme.palette.warning.main, 0.16)
            : alpha(theme.palette.primary.main, 0.22),
          borderColor: (theme) => pending ? theme.palette.warning.main : theme.palette.primary.main,
        }}
      />
      <Typography variant="caption" color="text.secondary">{label}</Typography>
    </Stack>
  );
}

/**
 * Takvim bir sayfa DEGIL bir aydir: o ayin butun izinleri gorunmelidir.
 * Sunucunun tavani 100 ve asilirsa sessizce kirpmak yerine uyari basiliyor --
 * eksik oldugunu soylemeyen bir takvim, eksik takvimden kotudur.
 */
const CALENDAR_LIMIT = 100;

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
  const departmentId = Number(params.get('department')) || undefined;
  const from = params.get('from') ?? '';
  const until = params.get('until') ?? '';
  const page = Number(params.get('page')) || 0;
  const view: 'list' | 'calendar' = params.get('view') === 'calendar' ? 'calendar' : 'list';
  // Ay adres cubugunda: takvimin belirli bir ayi baglanti olarak paylasilabilir.
  const month = /^\d{4}-\d{2}$/.test(params.get('month') ?? '')
    ? (params.get('month') as string)
    : currentMonth();
  const filtered = employeeId !== undefined || departmentId !== undefined
    || (view === 'list' && (from !== '' || until !== ''));

  const [rows, setRows] = useState<LeaveRequest[] | null>(null);
  const [total, setTotal] = useState(0);
  const [size, setSize] = useState(20);
  const [error, setError] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  /** Reddedilmek uzere secilen satir; gerekce penceresi bunun uzerinden acilir. */
  const [rejecting, setRejecting] = useState<LeaveRequest | null>(null);
  /** URL yalnizca id tasir; secim kutusunun gosterecegi ad buradan cozulur. */
  const [person, setPerson] = useState<EmployeeOption | null>(null);
  /**
   * Departmanlar referans verisi: az sayida ve nadiren degisir, bir kez
   * cekilir. Basarisiz olursa suzgec bos kalir ve SESSIZ kalir -- izin
   * listesini bir yardimci suzgec yuzunden hataya dusurmek yanlis olurdu.
   */
  const [departments, setDepartments] = useState<Department[]>([]);
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

  useEffect(() => {
    let active = true;

    departmentApi.list()
      .then((list) => { if (active) setDepartments(list); })
      .catch(() => { /* Suzgec gorunmez kalir; liste yine calisir. */ });

    return () => { active = false; };
  }, []);

  /** Her istege bir sira numarasi. */
  const requestId = useRef(0);

  const load = useCallback(async () => {
    const id = requestId.current + 1;
    requestId.current = id;

    setError(null);

    // Takvimde AY bir penceredir, listedeki tarih araligi ise bir suzgec.
    // Ikisi ayri kavram oldugu icin takvim listenin from/until degerlerini
    // ezmez; yalnizca kisi suzgeci ortaktir.
    const window = view === 'calendar' ? monthWindow(month) : null;

    try {
      const data = await leaveRequestApi.list({
        // Takvim "kim yerinde degil" sorusunu cevaplar: reddedilmis ve iptal
        // edilmis izinler devamsizlik degildir ve SUNUCUDA eleniyor -- yoksa
        // 100'luk butceyi cizilmeyecek satirlar yerdi.
        status: view === 'calendar'
          ? ['APPROVED', 'PENDING']
          : filter === 'PENDING' ? ['PENDING'] : undefined,
        // Adi degil ID'yi bekler: liste, secim kutusunun adi cozmesini
        // beklemeden yuklenmelidir.
        employeeId,
        departmentId,
        from: window ? window.from : from,
        until: window ? window.until : until,
        page: window ? 0 : page,
        size: window ? CALENDAR_LIMIT : size,
      });

      if (requestId.current !== id) return;

      setRows(data.content);
      setTotal(data.totalElements);
    } catch (cause) {
      if (requestId.current !== id) return;

      setError(errorMessage(cause));
      setRows([]);
    }
  }, [filter, employeeId, departmentId, from, until, page, size, view, month]);

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
  const empty = view === 'calendar'
    ? {
      title: `Nobody is off in ${monthLabel(month)}`,
      description: 'Approved and pending leave appears here as a bar per person.',
      // Cikis yolu AYI degistirmektir; filtreyi temizlemek bos ayi doldurmaz.
      //
      // Ad ustteki ok dugmesiyle AYNI olamaz: ayni ekranda ayni erisilebilir
      // adi tasiyan iki denetim, ekran okuyucuda ayirt edilemez. Ay adini
      // yazmak hem tekilestiriyor hem nereye gidildigini soyluyor.
      action: <Button onClick={() => setFilters({ month: shiftMonth(month, 1) })}>
        Show {monthLabel(shiftMonth(month, 1))}
      </Button>,
    }
    : filtered
    ? {
      title: 'No leave matches these filters',
      description: 'Nobody in your scope has leave in that range.',
      action: <Button onClick={() => setFilters({
        employee: undefined, department: undefined, from: undefined, until: undefined,
      })}>
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
          <Stack
            direction="row"
            spacing={1.5}
            sx={{ justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1.5 }}
          >
            {view === 'list' ? (
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
            ) : (
              <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                <IconButton
                  size="small"
                  aria-label="Previous month"
                  onClick={() => setFilters({ month: shiftMonth(month, -1) })}
                >
                  <ChevronLeftIcon />
                </IconButton>

                {/* Ay adi bir BASLIK degil canli bir deger: `aria-live` ile
                    okunur, yoksa ok tuslariyla gezinen biri ayin degistigini
                    hic duymazdi. */}
                <Typography variant="subtitle2" aria-live="polite" sx={{ minWidth: 140, textAlign: 'center' }}>
                  {monthLabel(month)}
                </Typography>

                <IconButton
                  size="small"
                  aria-label="Next month"
                  onClick={() => setFilters({ month: shiftMonth(month, 1) })}
                >
                  <ChevronRightIcon />
                </IconButton>

                {month !== currentMonth() && (
                  <Button size="small" onClick={() => setFilters({ month: undefined })}>
                    This month
                  </Button>
                )}
              </Stack>
            )}

            <ToggleButtonGroup
              exclusive
              size="small"
              value={view}
              onChange={(_, next) => {
                if (next === null) return;
                // Gorunum degisince sayfa ve ay sifirlanir: listenin 3.
                // sayfasindan takvime gecip geri donmek bos bir liste
                // gosterirdi.
                setFilters({ view: next === 'calendar' ? 'calendar' : undefined, month: undefined });
              }}
              aria-label="View"
            >
              {VIEWS.map((option) => (
                <ToggleButton key={option.value} value={option.value}>
                  {option.label}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
          </Stack>

          {/* Uc suzgec de AYNI boyda ve esit genislikte.
              Onceden kisi secici varsayilan `medium`, tarih alanlari `small`
              idi ve yan yana farkli yukseklikte duruyorlardi; ustelik "From"un
              yardim metni yoktu, dolayisiyla alt hizalari da tutmuyordu.
              `alignItems: flex-start` kutulari TEPEDEN hizalar, boylece yardim
              metni sarsa bile kutular kaymaz. */}
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            spacing={2}
            sx={{
              alignItems: { md: 'flex-start' },
              // Takvim paneli SABIT 320 px genisliginde ve alanin sol kenarina
              // yaslanir. 900'de her alan (900-32)/3 = 289 px kaliyordu, yani
              // panel alanin sagindan 31 px tasiyordu. 1024'te her alan 330 px
              // ve panel alanin icinde kaliyor.
              maxWidth: 1024,
              '& > *': { flex: 1, minWidth: 0 },
            }}
          >
            <EmployeePicker
              value={person}
              onChange={(next) => {
                setPerson(next);
                setFilters({ employee: next ? String(next.id) : undefined });
              }}
              label="Whose leave"
              helperText="Leave it empty to see everyone in your scope"
              size="small"
            />

            {/* Departman suzgeci HER IKI gorunumde de var: bu bir suzgec, ay
                gibi bir pencere degil. Liste bos kaldiginda gizlenmiyor --
                suzgec GORUNUR kalmali, yoksa kullanicinin temizleyecegi bir
                sey olmaz; ayni ders kisi suzgecinde bir kez ogrenildi. */}
            <TextField
              select
              size="small"
              label="Department"
              value={departments.length ? String(departmentId ?? '') : ''}
              onChange={(event) =>
                setFilters({ department: event.target.value || undefined })}
              helperText=" "
              disabled={departments.length === 0}
            >
              <MenuItem value="">All departments</MenuItem>
              {departments.map((department) => (
                <MenuItem key={department.id} value={String(department.id)}>
                  {department.name}
                </MenuItem>
              ))}
            </TextField>
            {/* Takvimde tarih araligi GOSTERILMEZ: pencereyi ay belirliyor ve
                iki ayri tarih denetimi birbiriyle celisirdi. */}
            {view === 'list' && (
              <>
                <DateField
                  label="From"
                  value={from}
                  onChange={(next) => setFilters({ from: next })}
                  size="small"
                  // Bos bir yardim satiri: yer AYRILIR, yoksa bu kutu digerlerinden
                  // bir satir kisa kalir.
                  helperText=" "
                />
                <DateField
                  label="Until"
                  value={until}
                  onChange={(next) => setFilters({ until: next })}
                  size="small"
                  helperText="Overlapping leave, not only leave starting here"
                />
              </>
            )}
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

          {rows !== null && rows.length > 0 && view === 'calendar' && (
            <>
              {total > rows.length && (
                <Alert severity="warning">
                  Showing {rows.length} of {total} leave records for this month. Filter by
                  person to see the rest.
                </Alert>
              )}

              <LeaveCalendar leaves={rows} month={month} today={todayIso()} />

              <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', gap: 1 }}>
                <Legend variant="approved" label="Approved" />
                <Legend variant="pending" label="Pending" />
              </Stack>
            </>
          )}

          {rows !== null && rows.length > 0 && view === 'list' && (
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
                          {/* Tek gunluk izinde tarih HAM basiliyordu; ayni
                              ifadenin iki dali farkli davraniyordu. */}
                          {leave.startDate === leave.endDate
                            ? formatDay(leave.startDate)
                            : `${formatDay(leave.startDate)} → ${formatDay(leave.endDate)}`}
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
