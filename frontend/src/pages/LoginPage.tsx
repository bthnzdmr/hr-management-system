import { useState } from 'react';
import type { SyntheticEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Box, Button, Stack, TextField, Typography } from '@mui/material';
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
      const from = (location.state as { from?: Location })?.from?.pathname ?? '/';
      navigate(from, { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    // Iki bolgeli duzen. Onceki hali gradyan bir zemin uzerinde iki radyal
    // isik lekesiydi; parlayan daireler kurumsal bir arac yerine tuketici
    // uygulamasi hissi veriyordu. Kimlik artik ISIKLA degil, sayfayi ikiye
    // bolen keskin bir kenarla kuruluyor: solda koyu blok, sagda is.
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <Box
        sx={{
          // Dar ekranda tamamen kaybolur: telefonda ekranin yarisini dekora
          // ayirmak, formu ekranin disina itmek demektir.
          display: { xs: 'none', md: 'flex' },
          flexDirection: 'column',
          justifyContent: 'space-between',
          width: '42%',
          maxWidth: 520,
          p: 6,
          bgcolor: '#141A22',
          borderRight: '1px solid',
          borderColor: '#293340',
        }}
      >
        <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center' }}>
          <Box
            sx={{
              width: 30,
              height: 30,
              borderRadius: 1.5,
              display: 'grid',
              placeItems: 'center',
              fontSize: 13,
              fontWeight: 600,
              color: '#141A22',
              bgcolor: '#C68465',
            }}
          >
            HR
          </Box>
          <Typography variant="subtitle1" sx={{ color: '#F8FAFC' }}>
            People
          </Typography>
        </Stack>

        <Box>
          <Typography variant="h3" sx={{ color: '#F8FAFC', fontSize: 34, lineHeight: 1.2 }}>
            One record per person, kept current.
          </Typography>
          <Typography variant="body2" sx={{ color: '#9CA3AF', mt: 2, maxWidth: 360 }}>
            Directory, reporting lines and departures — with every change
            announced to the people who need to know.
          </Typography>
        </Box>

        <Typography variant="caption" sx={{ color: '#5D6875' }}>
          HR Management System
        </Typography>
      </Box>

      <Box sx={{ flexGrow: 1, display: 'grid', placeItems: 'center', px: 3, py: 6 }}>
        <Box sx={{ width: '100%', maxWidth: 380 }}>
          {/* Marka dar ekranda BURADA gorunur; genis ekranda soldaki blokta
              duruyor ve iki kez gosterilmesi gereksiz olurdu. */}
          <Box
            sx={{
              display: { xs: 'grid', md: 'none' },
              width: 38,
              height: 38,
              borderRadius: 1.5,
              placeItems: 'center',
              fontSize: 15,
              fontWeight: 600,
              mb: 3,
              color: 'primary.contrastText',
              bgcolor: 'primary.main',
            }}
          >
            HR
          </Box>

          <Typography variant="h4" component="h1" sx={{ fontSize: 27 }}>
            Sign in
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
            Use the account your administrator set up for you.
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
        </Box>
      </Box>
    </Box>
  );
}
