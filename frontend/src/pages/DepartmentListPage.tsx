import { useCallback, useEffect, useState } from 'react';
import type { SyntheticEvent } from 'react';
import {
  Alert, Box, Button, Chip, IconButton, Paper, Skeleton, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TextField, Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ApartmentOutlinedIcon from '@mui/icons-material/ApartmentOutlined';
import BlockIcon from '@mui/icons-material/Block';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import { departmentApi } from '../api/departments';
import { errorMessage } from '../api/client';
import type { Department } from '../types/api';
import { PageHeader } from '../components/PageHeader';
import { EmptyState } from '../components/EmptyState';
import { useSnackbar } from '../components/SnackbarProvider';

const COLUMNS = ['Department', 'Active employees', 'Status', 'Actions'];

export function DepartmentListPage() {
  const { notify } = useSnackbar();

  const [departments, setDepartments] = useState<Department[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [creating, setCreating] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      // Kapatilmis olanlar da istenir: geri acabilmek icin once gorebilmek gerekir.
      setDepartments(await departmentApi.list(true));
      setError(null);
    } catch (cause) {
      setError(errorMessage(cause));
      setDepartments([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleCreate = async (event: SyntheticEvent) => {
    event.preventDefault();
    setCreating(true);
    try {
      const created = await departmentApi.create(name.trim());
      notify(`${created.name} created`);
      setName('');
      await load();
    } catch (cause) {
      // Sunucunun is kurali mesaji burada gorunur: "bu adda bir departman var".
      notify(errorMessage(cause), 'error');
    } finally {
      setCreating(false);
    }
  };

  const changeStatus = async (department: Department, active: boolean) => {
    setBusyId(department.id);
    try {
      const updated = await departmentApi.changeStatus(department.id, active);
      setDepartments((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)));
      notify(active ? `${updated.name} reopened` : `${updated.name} closed`);
    } catch (cause) {
      // "Icinde hala N aktif personel var" mesaji buradan gelir ve NE YAPILMASI
      // gerektigini soyler.
      notify(errorMessage(cause), 'error');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <Stack spacing={2.5}>
      <PageHeader
        eyebrow="Directory"
        title="Departments"
        description="Reference data used by every employee record"
      />

      {error && <Alert severity="error">{error}</Alert>}

      <Paper sx={{ p: 2 }}>
        <Box
          component="form"
          onSubmit={handleCreate}
          sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center' }}
        >
          <TextField
            label="New department"
            value={name}
            onChange={(event) => setName(event.target.value)}
            required
            sx={{ flexGrow: 1, minWidth: { xs: '100%', sm: 260 } }}
            slotProps={{ htmlInput: { maxLength: 100 } }}
          />
          <Button
            type="submit"
            variant="contained"
            startIcon={<AddIcon />}
            disabled={creating || name.trim() === ''}
          >
            Add
          </Button>
        </Box>
      </Paper>

      <Paper>
        <TableContainer sx={{ overflowX: 'auto' }}>
          <Table>
            <TableHead>
              <TableRow>
                {COLUMNS.map((column) => (
                  <TableCell key={column} align={column === 'Actions' ? 'right' : 'left'}>
                    {column}
                  </TableCell>
                ))}
              </TableRow>
            </TableHead>

            <TableBody>
              {loading && Array.from({ length: 4 }).map((_, index) => (
                <TableRow key={`skeleton-${index}`}>
                  {COLUMNS.map((column) => (
                    <TableCell key={column}><Skeleton variant="text" /></TableCell>
                  ))}
                </TableRow>
              ))}

              {!loading && departments.map((department) => {
                // Sunucu da reddediyor; burada devre disi birakmak kullaniciya
                // kacinilmaz bir hata yasatmamak icin.
                const staffed = department.activeEmployeeCount > 0;

                return (
                  <TableRow key={department.id} hover>
                    <TableCell>
                      <Typography variant="body2">{department.name}</Typography>
                    </TableCell>

                    <TableCell>{department.activeEmployeeCount}</TableCell>

                    <TableCell>
                      <Chip
                        label={department.active ? 'Open' : 'Closed'}
                        size="small"
                        color={department.active ? 'success' : 'default'}
                        variant={department.active ? 'filled' : 'outlined'}
                      />
                    </TableCell>

                    <TableCell align="right">
                      {department.active ? (
                        <Tooltip title={staffed
                          ? 'Move its employees elsewhere before closing it'
                          : 'Close'}>
                          {/* Devre disi dugme ipucu tetiklemez; span sarmalayici sart. */}
                          <span>
                            <IconButton
                              size="small"
                              aria-label={`Close ${department.name}`}
                              disabled={staffed || busyId === department.id}
                              onClick={() => changeStatus(department, false)}
                            >
                              <BlockIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                      ) : (
                        <Tooltip title="Reopen">
                          <span>
                            <IconButton
                              size="small"
                              aria-label={`Reopen ${department.name}`}
                              disabled={busyId === department.id}
                              onClick={() => changeStatus(department, true)}
                            >
                              <RestartAltIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}

              {!loading && departments.length === 0 && (
                <TableRow sx={{ '&:hover': { backgroundColor: 'transparent' } }}>
                  <TableCell colSpan={COLUMNS.length} sx={{ p: 0 }}>
                    <EmptyState
                      icon={<ApartmentOutlinedIcon />}
                      title="No departments yet"
                      description="Every employee belongs to a department, so add the first one here."
                    />
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Stack>
  );
}
