import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Snackbar } from '@mui/material';
import type { AlertColor } from '@mui/material';

interface Notification {
  message: string;
  severity: AlertColor;
}

interface SnackbarContextValue {
  notify: (message: string, severity?: AlertColor) => void;
}

const SnackbarContext = createContext<SnackbarContextValue | null>(null);

const AUTO_HIDE_MS = 4000;

/**
 * Tek bir geri bildirim yuvasi.
 *
 * Her sayfanin kendi Snackbar'ini tutmasi yerine tek yer: kayit kaydedildikten
 * sonra baska bir sayfaya gidildiginde bile mesaj gorunur kalir, cunku mesaji
 * tutan bilesen sokulmez.
 */
export function SnackbarProvider({ children }: { children: ReactNode }) {
  const [notification, setNotification] = useState<Notification | null>(null);

  const notify = useCallback((message: string, severity: AlertColor = 'success') => {
    setNotification({ message, severity });
  }, []);

  const value = useMemo(() => ({ notify }), [notify]);

  return (
    <SnackbarContext.Provider value={value}>
      {children}
      <Snackbar
        open={notification !== null}
        autoHideDuration={AUTO_HIDE_MS}
        onClose={() => setNotification(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        {/* Snackbar kapanirken icerigi hemen silinirse metin kaybolur ve
            gecis bos bir kutuyla tamamlanir; bu yuzden son mesaj korunur. */}
        <Alert
          severity={notification?.severity ?? 'info'}
          variant="filled"
          onClose={() => setNotification(null)}
          sx={{ width: '100%' }}
        >
          {notification?.message}
        </Alert>
      </Snackbar>
    </SnackbarContext.Provider>
  );
}

export function useSnackbar(): SnackbarContextValue {
  const context = useContext(SnackbarContext);
  if (!context) {
    throw new Error('useSnackbar must be used within SnackbarProvider');
  }
  return context;
}
