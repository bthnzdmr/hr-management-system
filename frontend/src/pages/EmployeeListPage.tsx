import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Box, Button, Chip, IconButton, InputAdornment, MenuItem, Paper, Skeleton, Stack, Table,
  TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TableSortLabel,
  TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography, useMediaQuery, useTheme,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import BlockIcon from '@mui/icons-material/Block';
import ClearIcon from '@mui/icons-material/Clear';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined';
import DensityMediumIcon from '@mui/icons-material/DensityMedium';
import DensitySmallIcon from '@mui/icons-material/DensitySmall';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import SearchIcon from '@mui/icons-material/Search';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import { useAppDispatch, useAppSelector } from '../store';
import {
  activeFilterChanged, changeEmployeeStatus, errorCleared, fetchEmployees, pageChanged,
  pageSizeChanged, searchChanged, sortChanged,
} from '../store/employeesSlice';
import type { ActiveFilter, SortField } from '../store/employeesSlice';
import { useAuth } from '../auth/AuthContext';
import { useSearchShortcut } from '../hooks/useSearchShortcut';
import { useDensity } from '../hooks/useDensity';
import { InitialsAvatar } from '../components/InitialsAvatar';
import { PageHeader } from '../components/PageHeader';
import { EmptyState } from '../components/EmptyState';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { EmployeeCard } from '../components/EmployeeCard';
import { useSnackbar } from '../components/SnackbarProvider';
import { employeeApi } from '../api/employees';
import { TERMINATION_REASONS, TERMINATION_REASON_LABELS } from '../types/api';
import type { Employee, TerminationReason } from '../types/api';

// Her tusa basista istek atmamak icin: kullanici yazmayi birakinca sorulur.
const SEARCH_DEBOUNCE_MS = 350;

const COLUMNS: { label: string; sortField?: SortField }[] = [
  { label: 'Employee', sortField: 'lastName' },
  { label: 'Email', sortField: 'email' },
  { label: 'Department' },
  { label: 'Manager' },
  { label: 'Hired', sortField: 'hireDate' },
  { label: 'Status' },
  { label: 'Actions' },
];

export function EmployeeListPage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { canEditEmployees } = useAuth();
  const theme = useTheme();
  // Kart mi tablo mu: olcum JS ile yapilir cunku ikisini birden render edip
  // birini gizlemek erisilebilirlik agacini kirletirdi.
  const isNarrow = useMediaQuery(theme.breakpoints.down('md'));
  const { notify } = useSnackbar();

  const {
    items, totalElements, page, size, search, activeFilter, sortField, sortDirection,
    status, error, statusChangingId,
  } = useAppSelector((s) => s.employees);

  // Yazi kutusu kendi durumunu tutar: her harf Redux'a yazilsaydi tum liste
  // her tusta yeniden render olurdu.
  const [searchInput, setSearchInput] = useState(search);
  const searchRef = useRef<HTMLInputElement>(null);
  const { density, setDensity } = useDensity();
  const [pendingDeactivation, setPendingDeactivation] = useState<Employee | null>(null);
  const [reportCount, setReportCount] = useState<number | null>(null);
  // Yalnizca EN SON istegin cevabi kabul edilir.
  const reportRequest = useRef(0);
  // Sunucu sebepsiz pasiflestirmeyi reddediyor; varsayilan secili gelir ki
  // kullanici her seferinde acik bir tercih yapsin ama akis tikanmasin.
  const [reason, setReason] = useState<TerminationReason>('RESIGNED');

  useEffect(() => {
    if (searchInput === search) return undefined;

    const timer = setTimeout(() => dispatch(searchChanged(searchInput)), SEARCH_DEBOUNCE_MS);
    // Temizlik sart: kullanici yazmaya devam ederse onceki zamanlayici iptal
    // edilir, aksi halde her harf icin bir istek birikirdi.
    return () => clearTimeout(timer);
  }, [searchInput, search, dispatch]);

  useEffect(() => {
    dispatch(fetchEmployees({ page, size, search, activeFilter, sortField, sortDirection }));
  }, [dispatch, page, size, search, activeFilter, sortField, sortDirection]);

  const askToDeactivate = useCallback(async (employee: Employee) => {
    // Her istege bir sira numarasi verilir ve cevap yazilmadan once "bu hala
    // bekledigim istek mi" diye sorulur.
    //
    // Olculen kusur: Alpha icin baslatilan yavas istek, kullanici Iptal edip
    // Beta'yi actiktan SONRA donuyor ve Beta'nin penceresine Alpha'nin ast
    // sayisini yaziyordu. Ters sirada daha kotu: 40 astli bir yonetici HIC
    // UYARI GORULMEDEN pasiflestirilebilirdi.
    //
    // Redux dilimi ayni korumaya requestId ile sahip; sayfanin kendi asenkronu
    // disarida kalmisti.
    const request = ++reportRequest.current;

    setPendingDeactivation(employee);
    setReportCount(null);

    try {
      const reports = await employeeApi.getDirectReports(employee.id);
      if (reportRequest.current !== request) return;
      setReportCount(reports.length);
    } catch {
      // Ast sayisi bir zenginlestirmedir, onay penceresinin on kosulu degil:
      // alinamazsa uyari satiri gosterilmez ama islem yine de yapilabilir.
      if (reportRequest.current !== request) return;
      setReportCount(null);
    }
  }, []);

  const applyStatus = useCallback(
    async (employee: Employee, active: boolean, terminationReason?: TerminationReason) => {
      const result = await dispatch(
        changeEmployeeStatus({ id: employee.id, active, terminationReason }));
      const name = `${employee.firstName} ${employee.lastName}`;

      if (changeEmployeeStatus.fulfilled.match(result)) {
        notify(active ? `${name} reactivated` : `${name} deactivated`);
      } else {
        notify(`Could not update ${name}`, 'error');
      }
    },
    [dispatch, notify],
  );

  const isLoading = status === 'loading';
  const isFiltered = search.trim() !== '' || activeFilter !== 'all';

  // "/" aramaya odaklanir, Escape kutuyu temizler.
  useSearchShortcut(searchRef, useCallback(() => setSearchInput(''), []));

  const clearFilters = () => {
    setSearchInput('');
    dispatch(searchChanged(''));
    dispatch(activeFilterChanged('all'));
  };

  // Uc ayri durum, uc ayri cevap. "Hic kayit yok" ile "filtreye uyan yok"
  // ayni sey degildir ve ikincisinde tek dogru cevap filtreyi temizlemektir.
  const emptyState = status === 'failed' ? (
    <EmptyState
      icon={<SearchIcon />}
      title="Could not load employees"
      description="The list could not be fetched. Check the connection and try again."
      action={(
        <Button
          size="small"
          variant="outlined"
          startIcon={<RestartAltIcon />}
          onClick={() => dispatch(fetchEmployees({
            page, size, search, activeFilter, sortField, sortDirection,
          }))}
        >
          Try again
        </Button>
      )}
    />
  ) : isFiltered ? (
    <EmptyState
      icon={<SearchIcon />}
      title="No employee matches these filters"
      description="Nothing here fits the current search and status filter."
      action={(
        <Button size="small" variant="outlined" startIcon={<ClearIcon />} onClick={clearFilters}>
          Clear filters
        </Button>
      )}
    />
  ) : (
    <EmptyState
      icon={<GroupsOutlinedIcon />}
      title="No employees yet"
      description={canEditEmployees
        ? 'Add the first person and the directory starts here.'
        : 'Nobody has been added to the directory yet.'}
      action={canEditEmployees && (
        <Button
          size="small"
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => navigate('/employees/new')}
        >
          New employee
        </Button>
      )}
    />
  );

  return (
    <Stack spacing={2.5}>
      <PageHeader
        eyebrow="Directory"
        title="Employees"
        description={`${totalElements} ${totalElements === 1 ? 'record' : 'records'}${
          isFiltered ? ' matching the current filters' : ''
        }`}
        // Yazma yetkisi yoksa dugme hic gosterilmez. Bu bir guvenlik onlemi
        // degil, kullaniciya kacinilmaz bir 403 yasatmama tercihidir.
        actions={canEditEmployees && (
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => navigate('/employees/new')}
          >
            New employee
          </Button>
        )}
      />

      {error && (
        <Alert severity="error" onClose={() => dispatch(errorCleared())}>
          {error}
        </Alert>
      )}

      <Paper sx={{ p: 2, display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center' }}>
        <TextField
          value={searchInput}
          onChange={(event) => setSearchInput(event.target.value)}
          placeholder="Search by name or email"
          // Dar ekranda tam genislik: 240 px'lik bir kutu telefonda
          // filtre dugmelerini asagi itip iki satir birden kaplardi.
          sx={{ flexGrow: 1, minWidth: { xs: '100%', sm: 240 } }}
          inputRef={searchRef}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
              endAdornment: searchInput ? (
                <InputAdornment position="end">
                  <IconButton size="small" aria-label="Clear search" onClick={() => setSearchInput('')}>
                    <ClearIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ) : (
                <InputAdornment position="end">
                  {/* Bilinmeyen kisayol, olmayan kisayoldur: tusun kendisi
                      kutunun icinde gosterilir. aria-hidden cunku bu bir
                      ipucu, ekran okuyucunun okuyacagi bir icerik degil. */}
                  <Box
                    aria-hidden
                    sx={{
                      display: { xs: 'none', sm: 'grid' },
                      placeItems: 'center',
                      minWidth: 18,
                      height: 18,
                      px: 0.5,
                      borderRadius: 0.75,
                      border: 1,
                      borderColor: 'divider',
                      color: 'text.secondary',
                      fontSize: 11,
                      lineHeight: 1,
                    }}
                  >
                    /
                  </Box>
                </InputAdornment>
              ),
            },
          }}
        />

        <ToggleButtonGroup
          size="small"
          exclusive
          value={activeFilter}
          onChange={(_, next: ActiveFilter | null) => {
            // null = secili dugmeye tekrar basildi. Bos filtre diye bir sey
            // yok; o durumda mevcut secim korunur.
            if (next !== null) dispatch(activeFilterChanged(next));
          }}
        >
          <ToggleButton value="all">All</ToggleButton>
          <ToggleButton value="active">Active</ToggleButton>
          <ToggleButton value="inactive">Inactive</ToggleButton>
        </ToggleButtonGroup>

        {/* Kart gorunumunde satir diye bir sey yok; anahtar da gorunmez. */}
        {!isNarrow && (
          <Tooltip title={density === 'compact' ? 'Comfortable rows' : 'Compact rows'}>
            <IconButton
              size="small"
              aria-label={density === 'compact' ? 'Switch to comfortable rows' : 'Switch to compact rows'}
              onClick={() => setDensity(density === 'compact' ? 'comfortable' : 'compact')}
            >
              {density === 'compact'
                ? <DensityMediumIcon fontSize="small" />
                : <DensitySmallIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        )}
      </Paper>

      {/* Dar ekranda kart, genis ekranda tablo. Ayni veri iki bicimde:
          yedi sutun telefonda yatay kaydirma gerektiriyordu ve yatay
          kaydirilan bir liste pratikte kullanilamaz.
          
          Ikisi birden render edilip biri CSS ile gizlenseydi DOM iki kat
          olur ve her erisilebilir ad iki kez bulunurdu -- ekran okuyucu
          ayni dugmeyi iki kere okurdu. */}
      {isNarrow && (
      <Stack spacing={1.25}>
        {isLoading &&
          Array.from({ length: Math.min(size, 4) }).map((_, index) => (
            <Skeleton key={`card-skeleton-${index}`} variant="rounded" height={104} />
          ))}

        {!isLoading && items.map((employee) => (
          <EmployeeCard
            key={employee.id}
            employee={employee}
            canEdit={canEditEmployees}
            busy={statusChangingId === employee.id}
            onView={() => navigate(`/employees/${employee.id}/details`)}
            onEdit={() => navigate(`/employees/${employee.id}`)}
            onToggleStatus={() =>
              employee.active ? askToDeactivate(employee) : applyStatus(employee, true)}
          />
        ))}

        {!isLoading && items.length === 0 && <Paper>{emptyState}</Paper>}
      </Stack>
      )}

      {!isNarrow && (
      <Paper>
        <TableContainer sx={{ overflowX: 'auto' }}>
          <Table size={density === 'compact' ? 'small' : 'medium'}>
            <TableHead>
              <TableRow>
                {COLUMNS.map((column) => (
                  <TableCell
                    key={column.label}
                    align={column.label === 'Actions' ? 'right' : 'left'}
                    sortDirection={column.sortField === sortField ? sortDirection : false}
                  >
                    {column.sortField ? (
                      <TableSortLabel
                        active={column.sortField === sortField}
                        direction={column.sortField === sortField ? sortDirection : 'asc'}
                        onClick={() => dispatch(sortChanged(column.sortField as SortField))}
                      >
                        {column.label}
                      </TableSortLabel>
                    ) : (
                      column.label
                    )}
                  </TableCell>
                ))}
              </TableRow>
            </TableHead>

            <TableBody>
              {/* Yukleme sirasinda iskelet satirlar: cizgisel gostergede tablo
                  once bosalir, sonra dolar ve sayfa ziplar. */}
              {isLoading &&
                Array.from({ length: Math.min(size, 5) }).map((_, index) => (
                  <TableRow key={`skeleton-${index}`}>
                    {COLUMNS.map((column) => (
                      <TableCell key={column.label}>
                        <Skeleton variant="text" />
                      </TableCell>
                    ))}
                  </TableRow>
                ))}

              {!isLoading &&
                items.map((employee) => (
                  <TableRow key={employee.id} hover>
                    <TableCell>
                      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                        <InitialsAvatar
                          firstName={employee.firstName}
                          lastName={employee.lastName}
                        />
                        <Box sx={{ minWidth: 0 }}>
                          <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
                            {employee.firstName} {employee.lastName}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" noWrap>
                            {employee.jobTitle}
                          </Typography>
                        </Box>
                      </Stack>
                    </TableCell>
                    <TableCell>{employee.email}</TableCell>
                    <TableCell>
                      <Chip label={employee.departmentName} size="small" variant="outlined" />
                    </TableCell>
                    <TableCell>
                      {employee.managerFullName ? (
                        <Typography variant="body2">{employee.managerFullName}</Typography>
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          No manager
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>{employee.hireDate}</TableCell>
                    <TableCell>
                      <Chip
                        label={employee.active ? 'Active' : 'Inactive'}
                        size="small"
                        color={employee.active ? 'success' : 'default'}
                        variant={employee.active ? 'filled' : 'outlined'}
                      />
                    </TableCell>
                    <TableCell align="right">
                      <Tooltip title="View details">
                        <IconButton
                          size="small"
                          aria-label="View details"
                          onClick={() => navigate(`/employees/${employee.id}/details`)}
                        >
                          <VisibilityOutlinedIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>

                      {canEditEmployees && (
                        <>
                          <Tooltip title="Edit">
                            <IconButton
                              size="small"
                              aria-label="Edit"
                              onClick={() => navigate(`/employees/${employee.id}`)}
                            >
                              <EditOutlinedIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>

                          {/* Pasiflestirme sorulur, aktiflestirme sorulmaz:
                              biri veriyi listelerden dusurur, digeri geri
                              getirir ve zarari yoktur. */}
                          <Tooltip title={employee.active ? 'Deactivate' : 'Reactivate'}>
                            <span>
                              <IconButton
                                size="small"
                                aria-label={employee.active ? 'Deactivate' : 'Reactivate'}
                                disabled={statusChangingId === employee.id}
                                onClick={() =>
                                  employee.active
                                    ? askToDeactivate(employee)
                                    : applyStatus(employee, true)
                                }
                              >
                                {employee.active ? (
                                  <BlockIcon fontSize="small" />
                                ) : (
                                  <RestartAltIcon fontSize="small" color="primary" />
                                )}
                              </IconButton>
                            </span>
                          </Tooltip>
                        </>
                      )}
                    </TableCell>
                  </TableRow>
                ))}

              {/* 'succeeded' yerine 'yuklenmiyor' kosulu: hata sonrasi kullanici
                  uyariyi kapattiginda status 'failed' kaliyor ve tablo tamamen
                  bos gorunuyordu -- ne satir, ne hata, ne de bir aciklama. */}
              {!isLoading && items.length === 0 && (
                <TableRow sx={{ '&:hover': { backgroundColor: 'transparent' } }}>
                  <TableCell colSpan={COLUMNS.length} sx={{ p: 0 }}>
                    {emptyState}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>

      </Paper>
      )}

      {/* Sayfalama iki gorunumde de gerekli; tablonun icinde kalsaydi dar
          ekranda kaybolurdu. */}
      <Paper>
        <TablePagination
          component="div"
          count={totalElements}
          page={page}
          rowsPerPage={size}
          rowsPerPageOptions={[10, 20, 50]}
          onPageChange={(_, next) => dispatch(pageChanged(next))}
          onRowsPerPageChange={(e) => dispatch(pageSizeChanged(Number(e.target.value)))}
        />
      </Paper>

      <ConfirmDialog
        open={pendingDeactivation !== null}
        title="Deactivate employee"
        confirmLabel="Deactivate"
        confirmColor="error"
        // Satir BASINA. Global oldugunda 1. satirin istegi surerken 3.
        // satirin penceresi acilirsa Iptal de Onayla da devre disi kalir,
        // Esc ve arka plan kapanir -- kullanici pencerede KILITLI kalirdi.
        busy={statusChangingId !== null && statusChangingId === pendingDeactivation?.id}
        description={
          <Stack spacing={1.5}>
            <Typography variant="body2">
              {pendingDeactivation?.firstName} {pendingDeactivation?.lastName} will no longer
              appear as an active employee. The record is kept and can be reactivated later.
            </Typography>
            {reportCount !== null && reportCount > 0 && (
              <Alert severity="warning">
                {reportCount} {reportCount === 1 ? 'person reports' : 'people report'} to this
                employee and will keep pointing at an inactive manager.
              </Alert>
            )}

            {/* Sebep zorunlu: sunucu eksik istegi reddeder. Devir oraninin en
                anlamli kirilimi bu alandir. */}
            <TextField
              select
              label="Reason for leaving"
              value={reason}
              onChange={(event) => setReason(event.target.value as TerminationReason)}
              fullWidth
              helperText="Recorded with today's date and used for turnover reporting"
            >
              {TERMINATION_REASONS.map((item) => (
                <MenuItem key={item} value={item}>
                  {TERMINATION_REASON_LABELS[item]}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        }
        onCancel={() => setPendingDeactivation(null)}
        onConfirm={() => {
          const target = pendingDeactivation;
          setPendingDeactivation(null);
          if (target) applyStatus(target, false, reason);
        }}
      />
    </Stack>
  );
}
