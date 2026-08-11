import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Box, Button, Chip, IconButton, LinearProgress, Paper, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TablePagination, TableRow, Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import BlockIcon from '@mui/icons-material/Block';
import { useAppDispatch, useAppSelector } from '../store';
import {
  deactivateEmployee, errorCleared, fetchEmployees, pageChanged, pageSizeChanged,
} from '../store/employeesSlice';
import { useAuth } from '../auth/AuthContext';

export function EmployeeListPage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { isAdmin } = useAuth();
  const { items, totalElements, page, size, status, error, deactivatingId } =
    useAppSelector((s) => s.employees);

  // Sayfa veya boyut degistiginde veri yeniden cekilir.
  useEffect(() => {
    dispatch(fetchEmployees({ page, size }));
  }, [dispatch, page, size]);

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h5" component="h1">
          Employees
        </Typography>

        {/* Yazma yetkisi yoksa dugme hic gosterilmez. Bu bir guvenlik onlemi
            degil, kullaniciya kacinilmaz bir 403 yasatmama tercihidir. */}
        {isAdmin && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate('/employees/new')}>
            New employee
          </Button>
        )}
      </Box>

      {error && (
        <Alert severity="error" onClose={() => dispatch(errorCleared())}>
          {error}
        </Alert>
      )}

      <Paper>
        {status === 'loading' && <LinearProgress />}

        <TableContainer sx={{ overflowX: 'auto' }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Email</TableCell>
                <TableCell>Department</TableCell>
                <TableCell>Job title</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.map((employee) => (
                <TableRow key={employee.id} hover>
                  <TableCell>{employee.firstName} {employee.lastName}</TableCell>
                  <TableCell>{employee.email}</TableCell>
                  <TableCell>{employee.departmentName}</TableCell>
                  <TableCell>{employee.jobTitle}</TableCell>
                  <TableCell>
                    <Chip
                      label={employee.active ? 'Active' : 'Inactive'}
                      size="small"
                      color={employee.active ? 'success' : 'default'}
                    />
                  </TableCell>
                  <TableCell align="right">
                    {isAdmin && (
                      <>
                        <Tooltip title="Edit">
                          <IconButton
                            size="small"
                            onClick={() => navigate(`/employees/${employee.id}`)}
                          >
                            <EditIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title={employee.active ? 'Deactivate' : 'Already inactive'}>
                          <span>
                            {/* Istek surerken devre disi: cift tiklama iki
                                DELETE gonderirdi. Sunucu artik idempotent
                                oldugu icin zarari yok, ama gereksiz. */}
                            <IconButton
                              size="small"
                              disabled={!employee.active || deactivatingId === employee.id}
                              onClick={() => dispatch(deactivateEmployee(employee.id))}
                            >
                              <BlockIcon fontSize="small" />
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
              {status !== 'loading' && items.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                    <Typography color="text.secondary">
                      {status === 'failed' ? 'Could not load employees' : 'No employees yet'}
                    </Typography>
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
    </Stack>
  );
}
