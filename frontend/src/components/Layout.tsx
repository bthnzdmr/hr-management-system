import { Outlet } from 'react-router-dom';
import { AppBar, Box, Button, Chip, Container, Toolbar, Typography } from '@mui/material';
import { useAuth } from '../auth/AuthContext';

export function Layout() {
  const { user, isAdmin, logout } = useAuth();

  return (
    <Box>
      <AppBar position="static">
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            HR Management System
          </Typography>

          {user && (
            <>
              <Typography variant="body2" sx={{ mr: 1 }}>
                {user.email}
              </Typography>
              <Chip
                label={isAdmin ? 'ADMIN' : 'USER'}
                size="small"
                color={isAdmin ? 'secondary' : 'default'}
                sx={{ mr: 2 }}
              />
              <Button color="inherit" onClick={logout}>
                Sign out
              </Button>
            </>
          )}
        </Toolbar>
      </AppBar>

      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Outlet />
      </Container>
    </Box>
  );
}
