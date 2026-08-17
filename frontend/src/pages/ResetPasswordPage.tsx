import { useState } from 'react';
import type { SyntheticEvent } from 'react';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Box, Button, Link, Stack, TextField } from '@mui/material';
import { passwordResetApi } from '../api/passwordReset';
import { errorMessage } from '../api/client';
import { AuthLayout } from '../components/AuthLayout';

const MIN_LENGTH = 12;

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get('token') ?? '';

  const [password, setPassword] = useState('');
  const [repeated, setRepeated] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Jetonsuz gelen biri formu doldurup ancak GONDERDIKTEN sonra hata gorurdu;
  // olmayan bir seyi denetmek zaman kaybi.
  if (!token) {
    return (
      <AuthLayout
        title="This link is incomplete"
        description="The address is missing its reset code."
      >
        <Stack spacing={2}>
          <Alert severity="error">Open the link from the email exactly as it was sent.</Alert>
          <Link component={RouterLink} to="/forgot-password" variant="body2">
            Ask for a new link
          </Link>
        </Stack>
      </AuthLayout>
    );
  }

  const mismatch = repeated !== '' && password !== repeated;

  const handleSubmit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await passwordResetApi.confirm(token, password);
      // Oturum acilmaz: sifirlama butun oturumlari kapatir ve kullanici yeni
      // parolayla girmelidir -- parolayi gercekten bildigini gosteren tek adim.
      navigate('/login', { replace: true, state: { passwordReset: true } });
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout title="Choose a new password" description="Then sign in with it.">
      <form onSubmit={handleSubmit}>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}

          <TextField
            label="New password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="new-password"
            required
            fullWidth
            helperText={`At least ${MIN_LENGTH} characters`}
          />
          <TextField
            label="Repeat new password"
            type="password"
            value={repeated}
            onChange={(event) => setRepeated(event.target.value)}
            autoComplete="new-password"
            required
            fullWidth
            error={mismatch}
            helperText={mismatch ? 'The two entries do not match' : ' '}
          />
          <Button
            type="submit"
            variant="contained"
            fullWidth
            size="large"
            disabled={submitting || mismatch || password.length < MIN_LENGTH}
          >
            {submitting ? 'Saving…' : 'Set password'}
          </Button>
        </Stack>
      </form>

      <Box sx={{ mt: 2.5 }}>
        <Link component={RouterLink} to="/login" variant="body2">
          Back to sign in
        </Link>
      </Box>
    </AuthLayout>
  );
}
