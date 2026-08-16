import { useEffect, useState } from 'react';
import type { ReactNode, SyntheticEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert, Box, Button, CircularProgress, Divider, Grid, MenuItem, Paper, Stack, TextField,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { employeeApi } from '../api/employees';
import { departmentApi } from '../api/departments';
import { errorMessage } from '../api/client';
import { EmployeePicker } from '../components/EmployeePicker';
import type { EmployeeOption } from '../components/EmployeePicker';
import { PageHeader } from '../components/PageHeader';
import { useSnackbar } from '../components/SnackbarProvider';
import type { Department, EmployeeCreateRequest } from '../types/api';

const EMPTY_FORM: EmployeeCreateRequest = {
  firstName: '',
  lastName: '',
  email: '',
  phone: null,
  // 0 "henuz secilmedi" demektir; gecerli bir departman id'si degildir.
  departmentId: 0,
  managerId: null,
  jobTitle: '',
  hireDate: '',
};

function Section({ title, description, children }: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <Box>
      <Typography variant="subtitle2">{title}</Typography>
      <Typography variant="caption" color="text.secondary">
        {description}
      </Typography>
      <Divider sx={{ mt: 1, mb: 2.5 }} />
      {children}
    </Box>
  );
}

export function EmployeeFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { notify } = useSnackbar();
  const isEdit = Boolean(id);

  const [form, setForm] = useState<EmployeeCreateRequest>(EMPTY_FORM);
  const [manager, setManager] = useState<EmployeeOption | null>(null);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Departmanlar hem yeni kayitta hem duzenlemede gerekli.
  useEffect(() => {
    departmentApi
      .list()
      .then(setDepartments)
      .catch((err) => setError(errorMessage(err)))
      .finally(() => {
        if (!id) setLoading(false);
      });
  }, [id]);

  useEffect(() => {
    if (!id) return;

    // Ucret bu formda YOK: personel kaydini acan kisi ucreti belirleyemez.
    // Ucret kendi ucundan, personel detay ekranindan yazilir.
    employeeApi.getById(Number(id))
      .then((employee) => {
        setForm({
          firstName: employee.firstName,
          lastName: employee.lastName,
          email: employee.email,
          phone: employee.phone,
          departmentId: employee.departmentId,
          managerId: employee.managerId,
          jobTitle: employee.jobTitle,
          hireDate: employee.hireDate,
        });
        // Secim kutusu adi sunucudan gelen cevaptan doldurulur; aksi halde
        // mevcut yonetici alani bos gorunur ve kaydederken sessizce silinirdi.
        setManager(
          employee.managerId && employee.managerFullName
            ? { id: employee.managerId, label: employee.managerFullName }
            : null,
        );
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, [id]);

  const update = <K extends keyof EmployeeCreateRequest>(
    field: K,
    value: EmployeeCreateRequest[K],
  ) => setForm((current) => ({ ...current, [field]: value }));

  const handleSubmit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    const payload = { ...form, managerId: manager?.id ?? null };

    try {
      if (isEdit) {
        await employeeApi.update(Number(id), payload);
      } else {
        await employeeApi.create(payload);
      }
      notify(isEdit ? 'Employee updated' : 'Employee created');
      navigate('/employees');
    } catch (err) {
      // Sunucunun alan bazli dogrulama hatalari da burada gosterilir.
      // Istemci dogrulamasi yalnizca kolaylik; karari sunucu verir.
      setError(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Stack spacing={2.5} sx={{ maxWidth: 860, mx: 'auto' }}>
      <Box>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/employees')}>
          Back to employees
        </Button>
      </Box>

      <PageHeader
        eyebrow="Directory"
        title={isEdit ? 'Edit employee' : 'New employee'}
        description={isEdit
          ? 'Changes are announced to the notification service.'
          : 'The new record triggers a welcome notification.'}
      />

      {error && <Alert severity="error">{error}</Alert>}

      {/* Tarayici dogrulamasi acik birakilir: zorunlu bir alan bos kaldiginda
          sunucuya gidip donmeye gerek kalmadan uyari verir. Karari yine
          sunucu verir, bu yalnizca hizli geri bildirimdir. */}
      <form onSubmit={handleSubmit}>
        <Stack spacing={2.5}>
          <Paper sx={{ p: 3 }}>
            <Section title="Personal details" description="How we identify and reach this person.">
              <Grid container spacing={2.5}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    label="First name" value={form.firstName} required fullWidth
                    onChange={(e) => update('firstName', e.target.value)}
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    label="Last name" value={form.lastName} required fullWidth
                    onChange={(e) => update('lastName', e.target.value)}
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    label="Email" type="email" value={form.email} required fullWidth
                    onChange={(e) => update('email', e.target.value)}
                    helperText="Notifications are sent to this address"
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    label="Phone" value={form.phone ?? ''} fullWidth
                    onChange={(e) => update('phone', e.target.value || null)}
                    helperText="Optional"
                  />
                </Grid>
              </Grid>
            </Section>
          </Paper>

          <Paper sx={{ p: 3 }}>
            <Section title="Employment" description="Where this person sits in the organisation.">
              <Grid container spacing={2.5}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    select label="Department" required fullWidth
                    value={form.departmentId ? String(form.departmentId) : ''}
                    onChange={(e) => update('departmentId', Number(e.target.value))}
                    helperText={departments.length === 0 ? 'No departments available' : ' '}
                  >
                    {departments.map((department) => (
                      <MenuItem key={department.id} value={String(department.id)}>
                        {department.name}
                      </MenuItem>
                    ))}
                  </TextField>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    label="Job title" value={form.jobTitle} required fullWidth
                    onChange={(e) => update('jobTitle', e.target.value)}
                    helperText=" "
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    label="Hire date" type="date" value={form.hireDate} required fullWidth
                    slotProps={{ inputLabel: { shrink: true } }}
                    onChange={(e) => update('hireDate', e.target.value)}
                    helperText=" "
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <EmployeePicker
                    value={manager}
                    onChange={setManager}
                    label="Manager"
                    helperText="Leave empty if this employee reports to nobody"
                    excludeId={id ? Number(id) : undefined}
                  />
                </Grid>
              </Grid>
            </Section>
          </Paper>


          {/* Dar ekranda alt alta ve ters sirada: birincil eylem parmaga en
              yakin yerde, yani altta kalir. */}
          <Box sx={{
            display: 'flex',
            gap: 1,
            justifyContent: 'flex-end',
            flexDirection: { xs: 'column-reverse', sm: 'row' },
          }}>
            <Button onClick={() => navigate('/employees')} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" variant="contained" disabled={submitting}>
              {submitting ? 'Saving…' : 'Save'}
            </Button>
          </Box>
        </Stack>
      </form>
    </Stack>
  );
}
