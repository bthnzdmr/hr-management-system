import { useEffect, useState } from 'react';
import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField,
} from '@mui/material';
import { employeeApi } from '../api/employees';
import { errorMessage } from '../api/client';

interface Props {
  open: boolean;
  employeeId: number;
  employeeName: string;
  current: number | null;
  onClose: () => void;
  onSaved: (salary: number | null) => void;
}

/** Ucret yazma. Personel formundan AYRI: kaydi acan ile ucreti belirleyen ayni kisi olmamali. */
export function SalaryDialog({
  open, employeeId, employeeName, current, onClose, onSaved,
}: Props) {
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // Pencere her acilista guncel degerle kurulur; kapatilip baska bir kisi
  // icin acildiginda oncekinin tutari kutuda kalmamali.
  useEffect(() => {
    if (open) {
      setValue(current === null ? '' : String(current));
      setError(null);
    }
  }, [open, current]);

  // Sunucu maas silmeyi desteklemiyor, o yuzden bos deger sessizce yutulmaz.
  const emptied = current !== null && value.trim() === '';
  const canSubmit = value.trim() !== '' && !saving;

  const submit = async () => {
    setSaving(true);
    setError(null);

    try {
      const saved = await employeeApi.updateSalary(employeeId, { salary: Number(value) });
      onSaved(saved.salary);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Set salary</DialogTitle>

      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}

          <TextField
            label="Salary"
            type="number"
            value={value}
            onChange={(event) => setValue(event.target.value)}
            error={emptied}
            helperText={emptied
              ? 'Salary cannot be removed, only changed'
              : `Annual gross for ${employeeName}`}
            autoFocus
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
