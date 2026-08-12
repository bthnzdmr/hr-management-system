// Yazi tipleri KENDI paketimizden servis edilir, CDN'den degil: harici bir
// istek hem gizlilik hem de ilk boyama suresi demektir. Degisken surumler --
// tek dosya butun agirliklari tasir.
import '@fontsource-variable/inter';
import '@fontsource-variable/instrument-sans';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { store } from './store';
import { AuthProvider } from './auth/AuthContext';
import { ColorModeProvider } from './theme/ColorModeContext';
import { SnackbarProvider } from './components/SnackbarProvider';
import { ErrorBoundary } from './components/ErrorBoundary';
import App from './App';

// Saglayicilarin sirasi onemli: AuthProvider ici Router'in yonlendirme
// yeteneklerini kullanmaz ama korumali rotalar AuthProvider'i gerektirir.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <ColorModeProvider>
        {/* Hata sinirini temanin ICINE koyuyoruz: hata ekrani da uygulamanin
            renkleriyle cizilsin. Router'in DISINDA cunku yonlendirme
            kurulamadan da bir hata firlayabilir. */}
        <ErrorBoundary>
          <SnackbarProvider>
            <BrowserRouter>
              <AuthProvider>
                <App />
              </AuthProvider>
            </BrowserRouter>
          </SnackbarProvider>
        </ErrorBoundary>
      </ColorModeProvider>
    </Provider>
  </StrictMode>,
);
