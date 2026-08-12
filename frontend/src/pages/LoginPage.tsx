import { useState } from 'react';
import type { SyntheticEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import {
  Alert, Box, Button, Card, CardContent, Stack, TextField, Typography, alpha,
} from '@mui/material';
import { useAuth } from '../auth/AuthContext';
import { errorMessage } from '../api/client';

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (user) {
    return <Navigate to="/employees" replace />;
  }

  const handleSubmit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await login({ email, password });
      // Korumali bir sayfadan yonlendirilmisse oraya geri don.
      const from = (location.state as { from?: Location })?.from?.pathname ?? '/employees';
      navigate(from, { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box
      sx={{
        display: 'grid',
        placeItems: 'center',
        minHeight: '100vh',
        px: 2,
        position: 'relative',
        overflow: 'hidden',
        // Giris ekrani uygulamanin ilk izlenimi. Tek duz kart yerine paletin
        // koyu zemini ve iki sicak isik: kimlik daha ilk saniyede kuruluyor.
        background: 'linear-gradient(160deg, #1E2631 0%, #293340 55%, #3B4859 100%)',
      }}
    >
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          top: '-15%',
          left: '-10%',
          width: 520,
          height: 520,
          borderRadius: '50%',
          background: `radial-gradient(circle, ${alpha('#C8937E', 0.22)}, transparent 62%)`,
        }}
      />
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          bottom: '-20%',
          right: '-8%',
          width: 460,
          height: 460,
          borderRadius: '50%',
          background: `radial-gradient(circle, ${alpha('#709995', 0.2)}, transparent 62%)`,
        }}
      />

      <Card
        sx={{
          width: '100%',
          maxWidth: 420,
          position: 'relative',
          p: 1,
          // Koyu zemin uzerinde kart hafif saydam: arkasindaki isik siziyor
          // ve kart yuzeye yapistirilmis degil, uzerinde duruyor gibi olur.
          backgroundColor: (t) => alpha(t.palette.background.paper, 0.94),
          backdropFilter: 'blur(8px)',
        }}
      >
        <CardContent>
          <Box
            sx={{
              width: 44,
              height: 44,
              borderRadius: 2.5,
              display: 'grid',
              placeItems: 'center',
              fontWeight: 700,
              fontSize: 17,
              mb: 2,
              color: '#1E2631',
              background: 'linear-gradient(135deg, #C8937E, #C48B8B)',
            }}
          >
            HR
          </Box>

          <Typography variant="h5" component="h1">
            Welcome back
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Sign in to the people directory
          </Typography>

          <form onSubmit={handleSubmit}>
            <Stack spacing={2}>
              {error && <Alert severity="error">{error}</Alert>}

              <TextField
                label="Email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="username"
                required
                fullWidth
              />
              <TextField
                label="Password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
                fullWidth
              />
              <Button
                type="submit"
                variant="contained"
                disabled={submitting}
                fullWidth
                size="large"
              >
                {submitting ? 'Signing in…' : 'Sign in'}
              </Button>
            </Stack>
          </form>
        </CardContent>
      </Card>
    </Box>
  );
}
