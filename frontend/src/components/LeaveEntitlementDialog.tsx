import { useEffect, useState } from 'react';
import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField,
} from '@mui/material';
import { leaveEntitlementApi } from '../api/leaveEntitlements';
import type { LeaveBalance } from '../api/leaveBalances';
import { errorMessage } from '../api/client';

interface Props {
  open: boolean;
  employeeId: number;
  employeeName: string;
  current: LeaveBalance;
  onClose: () => void;
  onSaved: (entitledDays: number, carriedOverDays: number) => void;
}

/**
 * Yillik izin hakkini elle belirler.
 *
 * Tahakkuk isi eksik satirlari kidem merdivenine gore yaziyor; burada verilen
 * karar onun UZERINE yazar ve is bir daha o satira dokunmaz.
 */
export function LeaveEntitlementDialog({
  open, employeeId, employeeName, current, onClose, onSaved,
}: Props) {
  const [entitled, setEntitled] = useState('');
  const [carriedOver, setCarriedOver] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // Pencere her acilista guncel degerlerle kurulur. Durum props'tan yalnizca
  // ILK kuruluşta okunsaydi, detay sayfasi baska kisiye gectiginde baslik yeni
  // kisiyi, kutular oncekini gosterirdi -- olculmus bir hata.
  useEffect(() => {
    if (open) {
      setEntitled(String(current.entitledDays));
      setCarriedOver(String(current.carriedOverDays));
      setNote('');
      setError(null);
    }
  }, [open, current]);

  // Gerekce sunucuda ZORUNLU. Burada da isteniyor ki kullanici sebebini
  // sunucudan donen bir hatayla ogrenmesin.
  const canSubmit = entitled.trim() !== '' && carriedOver.trim() !== ''
    && note.trim() !== '' && !saving;

  const submit = async () => {
    setSaving(true);
    setError(null);

    try {
      const saved = await leaveEntitlementApi.set(employeeId, current.year, {
        entitledDays: Number(entitled),
        carriedOverDays: Number(carriedOver),
        note: note.trim(),
      });
      onSaved(saved.entitledDays, saved.carriedOverDays);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Set leave entitlement</DialogTitle>

      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}

          <TextField
            label="Entitled days"
            type="number"
            value={entitled}
            onChange={(event) => setEntitled(event.target.value)}
            helperText={`Annual entitlement for ${employeeName} in ${current.year}`}
            autoFocus
            fullWidth
          />

          <TextField
            label="Carried over days"
            type="number"
            value={carriedOver}
            onChange={(event) => setCarriedOver(event.target.value)}
            helperText="Unused days brought forward from last year"
            fullWidth
          />

          <TextField
            label="Reason"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            helperText="Required — explains why this differs from the accrued amount"
            multiline
            minRows={2}
            fullWidth
          />
        </Stack>
      </DialogContent>

      {/* Dar ekranda birincil dugme ALTTA kalir: parmaga en yakin yer orasi. */}
      <DialogActions sx={{ px: 3, pb: 2, flexDirection: { xs: 'column-reverse', sm: 'row' }, gap: 1 }}>
        <Button onClick={onClose} disabled={saving}>
          Cancel
        </Button>
        <Button variant="contained" onClick={submit} disabled={!canSubmit}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}
