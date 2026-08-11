import { useEffect, useState } from 'react';
import type { SyntheticEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert, Box, Button, Card, CardContent, CircularProgress, Grid, MenuItem, Stack, TextField,
  Typography,
} from '@mui/material';
import { employeeApi } from '../api/employees';
import { departmentApi } from '../api/departments';
import { errorMessage } from '../api/client';
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
  salary: null,
};

export function EmployeeFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEdit = Boolean(id);

  const [form, setForm] = useState<EmployeeCreateRequest>(EMPTY_FORM);
  const [departments, setDepartments] = useState<Department[]>([]);
  // Maas ayri uca yazildigi icin degisip degismedigini bilmemiz gerekiyor:
  // degismediyse gereksiz bir istek ve gereksiz bir olay uretmeyiz.
  const [initialSalary, setInitialSalary] = useState<string | null>(null);
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

    // Maas ayri uctan gelir; genel personel cevabinda yer almaz.
    // Maas cagrisi basarisiz olursa form YINE acilir: ikincil bir bilgi
    // yuzunden birincil isi engellemek, projenin kendi ilkesine aykiri olurdu.
    Promise.all([
      employeeApi.getById(Number(id)),
      employeeApi.getSalary(Number(id)).catch(() => null),
    ])
      .then(([employee, salary]) => {
        // Sunucu sayi doner, girdi alani metin tutar. Ikisini AYNI bicime
        // cevirmek sart: aksi halde "95000" ile 95000 farkli gorunur ve
        // dokunulmamis maas guncellenmis sayilirdi.
        const asText = salary?.salary == null ? null : String(salary.salary);

        setForm({
          firstName: employee.firstName,
          lastName: employee.lastName,
          email: employee.email,
          phone: employee.phone,
          departmentId: employee.departmentId,
          managerId: employee.managerId,
          jobTitle: employee.jobTitle,
          hireDate: employee.hireDate,
          salary: asText,
        });
        setInitialSalary(asText);
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

    try {
      if (isEdit) {
        // salary bilerek ayriliyor: genel guncelleme onu tasimaz.
        const { salary, ...employeeFields } = form;
        await employeeApi.update(Number(id), employeeFields);

        // Metin karsilastirilir (ikisi de ayni bicimde), sunucuya SAYI gider.
        if (salary !== null && salary !== initialSalary) {
          await employeeApi.updateSalary(Number(id), { salary: Number(salary) });
        }
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
                  helperText={isEdit ? 'Saved separately; admins only' : ' '}
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
