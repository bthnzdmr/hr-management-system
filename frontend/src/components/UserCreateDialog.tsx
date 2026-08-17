import { useEffect, useState } from 'react';
import type { SyntheticEvent } from 'react';
import {
  Alert, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, ListItemText,
  MenuItem, Stack, TextField,
} from '@mui/material';
import { userApi } from '../api/users';
import { errorMessage } from '../api/client';
import { EmployeePicker } from './EmployeePicker';
import type { EmployeeOption } from './EmployeePicker';
import { ASSIGNABLE_ROLES, ROLE_DESCRIPTIONS, ROLE_LABELS } from '../types/api';
import type { Role, User } from '../types/api';

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated: (user: User) => void;
  /**
   * Personel detayindan gelindiginde e-posta ve baglanti hazir gelir.
   *
   * Ayni bilgiyi iki kez girmek gereksiz surtunmeydi: hesap acmak icin
   * personelin e-postasini yeniden yazip kendisini aramadan bulmak gerekiyordu.
   */
  forEmployee?: { id: number; label: string; email: string };
}

// Sunucudaki kuralin aynisi. Istemci dogrulamasi yalnizca kolayliktir; karari
// sunucu verir ve bu deger orada da yazilidir.

export function UserCreateDialog({ open, onClose, onCreated, forEmployee }: Props) {
  const [email, setEmail] = useState(forEmployee?.email ?? '');
  const [roles, setRoles] = useState<Role[]>(['EMPLOYEE']);
  const [employee, setEmployee] = useState<EmployeeOption | null>(
    forEmployee ? { id: forEmployee.id, label: forEmployee.label } : null,
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Durum props'tan YALNIZCA ilk kurulusta okunuyordu. Pencere kosulsuz render
  // ediliyor ve /employees/:id/details baska bir kisiye gecerken AYNI bileseni
  // yeniden kullaniyor -- yani baslik yeni kisiyi, kutular ONCEKI kisiyi
  // gosteriyordu ve hesap yanlis kisiye aciliyordu. Sunucu bunu yakalayamaz:
  // istek tamamen gecerlidir. SalaryDialog ayni sorunu bu kalipla cozuyor.
  useEffect(() => {
    if (!open) return;

    setEmail(forEmployee?.email ?? '');
    setRoles(['EMPLOYEE']);
    setEmployee(forEmployee ? { id: forEmployee.id, label: forEmployee.label } : null);
    setError(null);
  }, [open, forEmployee?.id, forEmployee?.email, forEmployee?.label]);

  const reset = () => {
    setEmail(forEmployee?.email ?? '');
    setRoles(['EMPLOYEE']);
    setEmployee(forEmployee ? { id: forEmployee.id, label: forEmployee.label } : null);
    setError(null);
  };

  const handleSubmit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      const created = await userApi.create({
        email,
        roles,
        employeeId: employee?.id ?? null,
      });
      reset();
      onCreated(created);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog
      open={open}
      onClose={submitting ? undefined : onClose}
      maxWidth="sm"
      fullWidth
    >
      <form onSubmit={handleSubmit}>
        <DialogTitle>
          {forEmployee ? `Give ${forEmployee.label} a login` : 'New account'}
        </DialogTitle>

        <DialogContent>
          <Stack spacing={2.5} sx={{ mt: 1 }}>
            {error && <Alert severity="error">{error}</Alert>}

            <TextField
              label="Email" type="email" value={email} required fullWidth
              onChange={(event) => setEmail(event.target.value)}
            />

            {/* Parola alani YOK ve bu kasitli: hesabi acan kisi parolayi
                belirleseydi onunla giris yapip kullanicinin kimligine
                burunebilir, denetim izinde de ayirt edilemezdi. */}
            <Alert severity="info">
              We email an invite link. Only the new user ever knows the password.
            </Alert>

            {/* Coklu secim: ayni kisi hem Ik uzmani hem sistem yoneticisi
                olabilir ve bunlar farkli islerdir. */}
            <TextField
              select label="Roles" value={roles} required fullWidth
              onChange={(event) => setRoles(event.target.value as unknown as Role[])}
              helperText="An account must have at least one role"
              slotProps={{
                select: {
                  multiple: true,
                  renderValue: (selected) => (selected as Role[])
                    .map((item) => ROLE_LABELS[item])
                    .join(', '),
                },
              }}
            >
              {ASSIGNABLE_ROLES.map((item) => (
                <MenuItem key={item} value={item}>
                  <Checkbox size="small" checked={roles.includes(item)} sx={{ mr: 0.5 }} />
                  <ListItemText primary={ROLE_LABELS[item]} secondary={ROLE_DESCRIPTIONS[item]} />
                </MenuItem>
              ))}
            </TextField>

            <EmployeePicker
              value={employee}
              onChange={setEmployee}
              label="Linked employee"
              helperText="Optional: system accounts have no employee record"
            />
          </Stack>
        </DialogContent>

        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={submitting}>
            {submitting ? 'Creating…' : 'Create account'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
