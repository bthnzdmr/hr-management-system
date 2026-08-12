import { useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import {
  Alert, Box, Button, Chip, CircularProgress, Divider, Grid, Link, List, ListItemButton,
  ListItemText, Paper, Stack, Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import KeyOutlinedIcon from '@mui/icons-material/KeyOutlined';
import { employeeApi } from '../api/employees';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { InitialsAvatar } from '../components/InitialsAvatar';
import { UserCreateDialog } from '../components/UserCreateDialog';
import { useSnackbar } from '../components/SnackbarProvider';
import type { Employee } from '../types/api';

interface Loaded {
  employee: Employee;
  directReports: Employee[];
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body1">{value}</Typography>
    </Box>
  );
}

export function EmployeeDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { canEditEmployees, canManageAccounts } = useAuth();
  const { notify } = useSnackbar();
  const [creatingLogin, setCreatingLogin] = useState(false);

  const [data, setData] = useState<Loaded | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const employeeId = Number(id);
    if (!Number.isInteger(employeeId)) {
      setError('Invalid employee id');
      return undefined;
    }

    // Bilesen sokulduktan sonra gelen cevabin durumu yazmasini engeller;
    // aksi halde hizli gezinmede kapali bir ekrana yazmaya calisirdi.
    let active = true;

    setData(null);
    setError(null);

    Promise.all([employeeApi.getById(employeeId), employeeApi.getDirectReports(employeeId)])
      .then(([employee, directReports]) => {
        if (active) setData({ employee, directReports });
      })
      .catch((cause) => {
        if (active) setError(errorMessage(cause));
      });

    return () => {
      active = false;
    };
  }, [id]);

  if (error) {
    return (
      <Stack spacing={2}>
        <Alert severity="error">{error}</Alert>
        <Box>
          <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/employees')}>
            Back to employees
          </Button>
        </Box>
      </Stack>
    );
  }

  if (!data) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  const { employee, directReports } = data;

  return (
    <Stack spacing={2.5}>
      <Box>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/employees')}>
          Back to employees
        </Button>
      </Box>

      <Paper sx={{ p: 3 }}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}
        >
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
            <InitialsAvatar
              firstName={employee.firstName}
              lastName={employee.lastName}
              size={64}
            />
            <Box>
              <Typography variant="h5" component="h1">
                {employee.firstName} {employee.lastName}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {employee.jobTitle} · {employee.departmentName}
              </Typography>
              <Chip
                label={employee.active ? 'Active' : 'Inactive'}
                size="small"
                color={employee.active ? 'success' : 'default'}
                variant={employee.active ? 'filled' : 'outlined'}
                sx={{ mt: 1 }}
              />
            </Box>
          </Stack>

          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
            {/* Hesap acmak sistem yoneticisinin isi, personeli duzenlemek Ik
                uzmaninin: iki dugme iki farkli yetenege bagli. */}
            {canManageAccounts && employee.active && (
              <Button
                variant="outlined"
                startIcon={<KeyOutlinedIcon />}
                onClick={() => setCreatingLogin(true)}
              >
                Give a login
              </Button>
            )}

            {canEditEmployees && (
              <Button
                variant="outlined"
                startIcon={<EditOutlinedIcon />}
                onClick={() => navigate(`/employees/${employee.id}`)}
              >
                Edit
              </Button>
            )}
          </Stack>
        </Stack>
      </Paper>

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, md: 7 }}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="subtitle2" gutterBottom>
              Details
            </Typography>
            <Divider sx={{ mb: 2 }} />

            <Grid container spacing={2.5}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Field label="Email" value={employee.email} />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Field label="Phone" value={employee.phone ?? 'Not provided'} />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Field label="Department" value={employee.departmentName} />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Field label="Hire date" value={employee.hireDate} />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Manager
                  </Typography>
                  {employee.managerId && employee.managerFullName ? (
                    <Typography variant="body1">
                      <Link component={RouterLink} to={`/employees/${employee.managerId}/details`}>
                        {employee.managerFullName}
                      </Link>
                    </Typography>
                  ) : (
                    <Typography variant="body1">No manager</Typography>
                  )}
                </Box>
              </Grid>
            </Grid>

            {/* Maas bilerek yok: bu ekran her kullaniciya acik ve maas ayri
                bir uctan, yalnizca ADMIN'e servis ediliyor. */}
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, md: 5 }}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="subtitle2" gutterBottom>
              Direct reports ({directReports.length})
            </Typography>
            <Divider sx={{ mb: 1 }} />

            {directReports.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                Nobody reports to this employee.
              </Typography>
            ) : (
              <List dense disablePadding>
                {directReports.map((report) => (
                  <ListItemButton
                    key={report.id}
                    component={RouterLink}
                    to={`/employees/${report.id}/details`}
                    // Dokunma hedefi: liste ogesi parmakla secilebilir olmali.
                    sx={{ borderRadius: 2, minHeight: 56 }}
                  >
                    <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', width: '100%' }}>
                      <InitialsAvatar
                        firstName={report.firstName}
                        lastName={report.lastName}
                        size={32}
                      />
                      <ListItemText
                        primary={`${report.firstName} ${report.lastName}`}
                        secondary={report.jobTitle}
                      />
                    </Stack>
                  </ListItemButton>
                ))}
              </List>
            )}
          </Paper>
        </Grid>
      </Grid>
      <UserCreateDialog
        open={creatingLogin}
        onClose={() => setCreatingLogin(false)}
        onCreated={(account) => {
          setCreatingLogin(false);
          notify(`${account.email} can now sign in`);
        }}
        forEmployee={{
          id: employee.id,
          label: `${employee.firstName} ${employee.lastName}`,
          email: employee.email,
        }}
      />
    </Stack>
  );
}
