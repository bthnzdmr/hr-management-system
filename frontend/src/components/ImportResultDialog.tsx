import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography,
} from '@mui/material';
import type { RowError } from '../api/imports';

interface Props {
  open: boolean;
  /** Reddedilen satirlar; bos liste "dosya kabul edildi" demektir. */
  errors: RowError[];
  imported: number;
  onClose: () => void;
}

/**
 * Ice aktarmanin sonucu.
 *
 * Ret bir SNACKBAR ile bildirilemez: on satirlik gerekce oraya sigmaz ve
 * kullanicinin dosyayi duzeltebilmesi icin hepsini birden gormesi gerekir.
 * Ilk hatada durulsaydi zaten onlarca tur atardi.
 */
export function ImportResultDialog({ open, errors, imported, onClose }: Props) {
  const rejected = errors.length > 0;

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{rejected ? 'Nothing was imported' : 'Import finished'}</DialogTitle>

      <DialogContent>
        {rejected ? (
          <>
            {/* Hicbir seyin yazilmadigini ACIKCA soyler. "3 satir hatali"
                demek, digerlerinin girdigini sandirirdi. */}
            <Alert severity="error" sx={{ mb: 2 }}>
              The file was rejected, so no row was imported. Fix the lines below and try again.
            </Alert>

            <Box
              component="ol"
              sx={{
                m: 0,
                pl: 0,
                listStyle: 'none',
                maxHeight: 320,
                overflowY: 'auto',
                fontSize: '0.875rem',
              }}
            >
              {errors.map((error, index) => (
                <Box
                  component="li"
                  key={`${error.line}-${index}`}
                  sx={{ display: 'flex', gap: 1.5, py: 0.5 }}
                >
                  {/* Satir numarasi dosyayla ORTUSUR (baslik 1'dir);
                      kullanici dosyayi bir editorde acip dogrudan bulur. */}
                  <Typography
                    variant="body2"
                    sx={{ color: 'text.secondary', minWidth: 56, fontVariantNumeric: 'tabular-nums' }}
                  >
                    Line {error.line}
                  </Typography>
                  <Typography variant="body2">{error.reason}</Typography>
                </Box>
              ))}
            </Box>
          </>
        ) : (
          <Alert severity="success">
            {imported} {imported === 1 ? 'person was' : 'people were'} added.
          </Alert>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button variant="contained" onClick={onClose}>
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
}
