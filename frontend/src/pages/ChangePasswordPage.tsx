import { useState } from 'react';
import type { SyntheticEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Box, Button, Paper, Stack, TextField } from '@mui/material';
import { userApi } from '../api/users';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/PageHeader';
import { useSnackbar } from '../components/SnackbarProvider';

const MIN_PASSWORD_LENGTH = 12;

export function ChangePasswordPage() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const { notify } = useSnackbar();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Tekrar alani yalnizca ARAYUZDE var: sunucunun iki kez ayni sey gonderilip
  // gonderilmedigiyle isi yok, amac yazim hatasiyla kilitlenmeyi onlemek.
  const mismatch = confirmation.length > 0 && confirmation !== newPassword;

  const handleSubmit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await userApi.changeOwnPassword({ currentPassword, newPassword });

      // Parola degisince sunucu TUM oturumlari iptal ediyor -- bu oturum da
      // dahil. Kullaniciyi burada birakmak, her istegin sessizce basarisiz
      // oldugu bir ekranda birakmak olurdu.
      notify('Password changed. Please sign in again.');
      logout();
      navigate('/login', { replace: true });
    } catch (cause) {
      setError(errorMessage(cause));
      setSubmitting(false);
    }
  };

  return (
    <Stack spacing={2.5} sx={{ maxWidth: 520, mx: 'auto' }}>
      <PageHeader
        eyebrow="Your account"
        title="Change password"
        description={`Signed in as ${user?.email ?? ''}`}
      />

      <Paper sx={{ p: 3 }}>
        <form onSubmit={handleSubmit}>
          <Stack spacing={2.5}>
            {error && <Alert severity="error">{error}</Alert>}

            <TextField
              label="Current password" type="password" value={currentPassword} required fullWidth
              onChange={(event) => setCurrentPassword(event.target.value)}
              helperText="Asked so that a stolen session cannot take over the account"
            />

            <TextField
              label="New password" type="password" value={newPassword} required fullWidth
              slotProps={{ htmlInput: { minLength: MIN_PASSWORD_LENGTH } }}
              onChange={(event) => setNewPassword(event.target.value)}
              helperText={`At least ${MIN_PASSWORD_LENGTH} characters`}
            />

            <TextField
              label="Repeat new password" type="password" value={confirmation} required fullWidth
              onChange={(event) => setConfirmation(event.target.value)}
              error={mismatch}
              helperText={mismatch ? 'The two passwords do not match' : ' '}
            />

            <Alert severity="info">
              Changing your password signs you out everywhere, including this device.
            </Alert>

            <Box sx={{
              display: 'flex',
              gap: 1,
              justifyContent: 'flex-end',
              flexDirection: { xs: 'column-reverse', sm: 'row' },
            }}>
              <Button onClick={() => navigate(-1)} disabled={submitting}>
                Cancel
              </Button>
              <Button
                type="submit"
                variant="contained"
                disabled={submitting || mismatch || confirmation.length === 0}
              >
                {submitting ? 'Saving…' : 'Change password'}
              </Button>
            </Box>
          </Stack>
        </form>
      </Paper>
    </Stack>
  );
}
