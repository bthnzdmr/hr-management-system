import { useState } from 'react';
import type { SyntheticEvent } from 'react';
import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField,
} from '@mui/material';
import { userApi } from '../api/users';
import { errorMessage } from '../api/client';
import { EmployeePicker } from './EmployeePicker';
import type { EmployeeOption } from './EmployeePicker';
import type { Role, User } from '../types/api';

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated: (user: User) => void;
}

// Sunucudaki kuralin aynisi. Istemci dogrulamasi yalnizca kolayliktir; karari
// sunucu verir ve bu deger orada da yazilidir.
const MIN_PASSWORD_LENGTH = 12;

export function UserCreateDialog({ open, onClose, onCreated }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>('USER');
  const [employee, setEmployee] = useState<EmployeeOption | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setEmail('');
    setPassword('');
    setRole('USER');
    setEmployee(null);
    setError(null);
  };

  const handleSubmit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      const created = await userApi.create({
        email,
        password,
        role,
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
        <DialogTitle>New account</DialogTitle>

        <DialogContent>
          <Stack spacing={2.5} sx={{ mt: 1 }}>
            {error && <Alert severity="error">{error}</Alert>}

            <TextField
              label="Email" type="email" value={email} required fullWidth
              onChange={(event) => setEmail(event.target.value)}
            />

            <TextField
              label="Password" type="password" value={password} required fullWidth
              slotProps={{ htmlInput: { minLength: MIN_PASSWORD_LENGTH } }}
              onChange={(event) => setPassword(event.target.value)}
              helperText={`At least ${MIN_PASSWORD_LENGTH} characters. Only the owner can change it later.`}
            />

            <TextField
              select label="Role" value={role} required fullWidth
              onChange={(event) => setRole(event.target.value as Role)}
              helperText={role === 'ADMIN'
                ? 'Can create, update and deactivate records'
                : 'Read-only access'}
            >
              <MenuItem value="USER">USER</MenuItem>
              <MenuItem value="ADMIN">ADMIN</MenuItem>
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
