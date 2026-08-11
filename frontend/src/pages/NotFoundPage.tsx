import { useNavigate } from 'react-router-dom';
import { Box, Button, Paper, Stack, Typography } from '@mui/material';
import SearchOffIcon from '@mui/icons-material/SearchOff';

export function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }}>
      <Paper sx={{ p: 4, maxWidth: 460, textAlign: 'center' }}>
        <Stack spacing={2} sx={{ alignItems: 'center' }}>
          <SearchOffIcon color="disabled" sx={{ fontSize: 48 }} />
          <Typography variant="h6">Page not found</Typography>
          <Typography variant="body2" color="text.secondary">
            The address you opened does not match any page in this application.
          </Typography>
          <Button variant="contained" onClick={() => navigate('/employees')}>
            Go to employees
          </Button>
        </Stack>
      </Paper>
    </Box>
  );
}
