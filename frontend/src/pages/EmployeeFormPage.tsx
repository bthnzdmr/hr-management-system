import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert, Box, Button, Card, CardContent, CircularProgress, Grid, Stack, TextField, Typography,
} from '@mui/material';
import { employeeApi } from '../api/employees';
import { errorMessage } from '../api/client';
import type { EmployeeCreateRequest } from '../types/api';

const EMPTY_FORM: EmployeeCreateRequest = {
  firstName: '',
  lastName: '',
  email: '',
  phone: null,
  departmentId: 1,
  managerId: null,
  jobTitle: '',
  hireDate: '',
  salary: null,
};

export function EmployeeFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEdit = Boolean(id);

  const [form, setForm] = useState<EmployeeCreateRequest>(EMPTY_FORM);
  const [loading, setLoading] = useState(isEdit);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;

    employeeApi
      .getById(Number(id))
      .then((employee) =>
        setForm({
          firstName: employee.firstName,
          lastName: employee.lastName,
          email: employee.email,
          phone: employee.phone,
          departmentId: employee.departmentId,
          managerId: employee.managerId,
          jobTitle: employee.jobTitle,
          hireDate: employee.hireDate,
          // Maas cevapta hic donmuyor; duzenlemede bos birakilir.
          salary: null,
        }),
      )
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, [id]);

  const update = <K extends keyof EmployeeCreateRequest>(
    field: K,
    value: EmployeeCreateRequest[K],
  ) => setForm((current) => ({ ...current, [field]: value }));

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      if (isEdit) {
        await employeeApi.update(Number(id), form);
      } else {
        await employeeApi.create(form);
      }
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
    <Card sx={{ maxWidth: 720, mx: 'auto' }}>
      <CardContent>
        <Typography variant="h5" component="h1" gutterBottom>
          {isEdit ? 'Edit employee' : 'New employee'}
        </Typography>

        <form onSubmit={handleSubmit}>
          <Stack spacing={2} sx={{ mt: 2 }}>
            {error && <Alert severity="error">{error}</Alert>}

            <Grid container spacing={2}>
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
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Phone" value={form.phone ?? ''} fullWidth
                  onChange={(e) => update('phone', e.target.value || null)}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Department ID" type="number" value={form.departmentId} required fullWidth
                  onChange={(e) => update('departmentId', Number(e.target.value))}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Manager ID" type="number" value={form.managerId ?? ''} fullWidth
                  onChange={(e) => update('managerId', e.target.value ? Number(e.target.value) : null)}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Job title" value={form.jobTitle} required fullWidth
                  onChange={(e) => update('jobTitle', e.target.value)}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Hire date" type="date" value={form.hireDate} required fullWidth
                  slotProps={{ inputLabel: { shrink: true } }}
                  onChange={(e) => update('hireDate', e.target.value)}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Salary" type="number" value={form.salary ?? ''} fullWidth
                  onChange={(e) => update('salary', e.target.value || null)}
                />
              </Grid>
            </Grid>

            <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
              <Button onClick={() => navigate('/employees')}>Cancel</Button>
              <Button type="submit" variant="contained" disabled={submitting}>
                {submitting ? 'Saving…' : 'Save'}
              </Button>
            </Box>
          </Stack>
        </form>
      </CardContent>
    </Card>
  );
}
