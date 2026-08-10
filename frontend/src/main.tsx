import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material';
import { store } from './store';
import { AuthProvider } from './auth/AuthContext';
import App from './App';

const theme = createTheme({
  palette: { mode: 'light', primary: { main: '#1565c0' } },
});

// Saglayicilarin sirasi onemli: AuthProvider ici Router'in yonlendirme
// yeteneklerini kullanmaz ama korumali rotalar AuthProvider'i gerektirir.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <BrowserRouter>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </ThemeProvider>
    </Provider>
  </StrictMode>,
);
