import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Box, Button, Chip, IconButton, InputAdornment, Paper, Skeleton, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TablePagination, TableRow, TableSortLabel, TextField,
  ToggleButton, ToggleButtonGroup, Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import BlockIcon from '@mui/icons-material/Block';
import ClearIcon from '@mui/icons-material/Clear';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
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
import { InitialsAvatar } from '../components/InitialsAvatar';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { useSnackbar } from '../components/SnackbarProvider';
import { employeeApi } from '../api/employees';
import type { Employee } from '../types/api';

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
  const { isAdmin } = useAuth();
  const { notify } = useSnackbar();

  const {
    items, totalElements, page, size, search, activeFilter, sortField, sortDirection,
    status, error, statusChangingId,
  } = useAppSelector((s) => s.employees);

  // Yazi kutusu kendi durumunu tutar: her harf Redux'a yazilsaydi tum liste
  // her tusta yeniden render olurdu.
  const [searchInput, setSearchInput] = useState(search);
  const [pendingDeactivation, setPendingDeactivation] = useState<Employee | null>(null);
  const [reportCount, setReportCount] = useState<number | null>(null);

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
    setPendingDeactivation(employee);
    setReportCount(null);

    try {
      const reports = await employeeApi.getDirectReports(employee.id);
      setReportCount(reports.length);
    } catch {
      // Ast sayisi bir zenginlestirmedir, onay penceresinin on kosulu degil:
      // alinamazsa uyari satiri gosterilmez ama islem yine de yapilabilir.
      setReportCount(null);
    }
  }, []);

  const applyStatus = useCallback(
    async (employee: Employee, active: boolean) => {
      const result = await dispatch(changeEmployeeStatus({ id: employee.id, active }));
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

  return (
    <Stack spacing={2.5}>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2 }}>
        <Box>
          <Typography variant="h5" component="h1">
            Employees
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {totalElements} {totalElements === 1 ? 'record' : 'records'}
            {isFiltered ? ' matching the current filters' : ''}
          </Typography>
        </Box>

        {/* Yazma yetkisi yoksa dugme hic gosterilmez. Bu bir guvenlik onlemi
            degil, kullaniciya kacinilmaz bir 403 yasatmama tercihidir. */}
        {isAdmin && (
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => navigate('/employees/new')}
          >
            New employee
          </Button>
        )}
      </Box>

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
          sx={{ flexGrow: 1, minWidth: 240 }}
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
              ) : null,
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
      </Paper>

      <Paper>
        <TableContainer sx={{ overflowX: 'auto' }}>
          <Table>
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

                      {isAdmin && (
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
                <TableRow>
                  <TableCell colSpan={COLUMNS.length} align="center" sx={{ py: 6 }}>
                    <Typography color="text.secondary">
                      {status === 'failed'
                        ? 'Could not load employees'
                        : isFiltered
                          ? 'No employee matches these filters'
                          : 'No employees yet'}
                    </Typography>
                    {isFiltered && status !== 'failed' && (
                      <Button
                        size="small"
                        sx={{ mt: 1 }}
                        onClick={() => {
                          setSearchInput('');
                          dispatch(searchChanged(''));
                          dispatch(activeFilterChanged('all'));
                        }}
                      >
                        Clear filters
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>

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
        busy={statusChangingId !== null}
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
          </Stack>
        }
        onCancel={() => setPendingDeactivation(null)}
        onConfirm={() => {
          const target = pendingDeactivation;
          setPendingDeactivation(null);
          if (target) applyStatus(target, false);
        }}
      />
    </Stack>
  );
}
