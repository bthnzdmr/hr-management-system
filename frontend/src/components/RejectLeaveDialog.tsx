import { useEffect, useState } from 'react';
import {
  Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, TextField,
} from '@mui/material';

interface Props {
  open: boolean;
  employeeName: string;
  busy: boolean;
  onClose: () => void;
  onConfirm: (reason: string | undefined) => void;
}

/** Ret gerekcesi. Talebin kendi notunun UZERINE YAZMAZ; ayri bir alanda durur. */
export function RejectLeaveDialog({ open, employeeName, busy, onClose, onConfirm }: Props) {
  const [reason, setReason] = useState('');

  // Durum props'tan yalnizca ilk kurulusta okunsaydi, pencere baska bir satir
  // icin acildiginda oncekinin metni kutuda kalirdi.
  useEffect(() => {
    if (open) setReason('');
  }, [open]);

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Reject this request?</DialogTitle>

      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          {employeeName} will keep the reason they gave; yours is recorded separately.
        </DialogContentText>

        <TextField
          label="Reason (optional)"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          multiline
          minRows={2}
          fullWidth
          autoFocus
          slotProps={{ htmlInput: { maxLength: 500 } }}
        />
      </DialogContent>

      {/* Dar ekranda birincil dugme ALTTA: parmaga en yakin yer orasi. */}
      <DialogActions sx={{ px: 3, pb: 2, flexDirection: { xs: 'column-reverse', sm: 'row' }, gap: 1 }}>
        <Button onClick={onClose} disabled={busy}>
          Keep it pending
        </Button>
        <Button
          variant="contained"
          color="error"
          disabled={busy}
          onClick={() => onConfirm(reason.trim() || undefined)}
        >
          Reject
        </Button>
      </DialogActions>
    </Dialog>
  );
}
