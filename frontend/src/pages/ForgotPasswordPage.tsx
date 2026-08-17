import { useState } from 'react';
import type { SyntheticEvent } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { Alert, Box, Button, Link, Stack, TextField } from '@mui/material';
import { passwordResetApi } from '../api/passwordReset';
import { errorMessage } from '../api/client';
import { AuthLayout } from '../components/AuthLayout';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await passwordResetApi.request(email);
      setSent(true);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setSubmitting(false);
    }
  };

  if (sent) {
    return (
      <AuthLayout
        title="Check your inbox"
        // Metin hesabin VAR OLDUGUNU soylemez. Soyleseydi arayuz, sunucunun
        // acikca kacindigi numaralandirma bilgisini geri sizdirirdi.
        description="If that address belongs to an account, a reset link is on its way."
      >
        <Stack spacing={2}>
          <Alert severity="success">
            The link works once and expires in about 30 minutes.
          </Alert>
          <Link component={RouterLink} to="/login" variant="body2">
            Back to sign in
          </Link>
        </Stack>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title="Forgot your password?"
      description="Give us the address you sign in with and we will send a reset link."
    >
      <form onSubmit={handleSubmit}>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}

          <TextField
            label="Email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="username"
            required
            fullWidth
          />
          <Button type="submit" variant="contained" disabled={submitting} fullWidth size="large">
            {submitting ? 'Sending…' : 'Send reset link'}
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
