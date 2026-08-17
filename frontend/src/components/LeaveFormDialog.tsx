import { useState } from 'react';
import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField,
} from '@mui/material';
import { leaveRequestApi } from '../api/leaveRequests';
import type { LeaveType } from '../api/leaveRequests';
import { errorMessage } from '../api/client';
import { EmployeePicker } from './EmployeePicker';
import { DateField } from './DateField';
import type { EmployeeOption } from './EmployeePicker';

interface Props {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}

const TYPES: { value: LeaveType; label: string }[] = [
  { value: 'ANNUAL', label: 'Annual' },
  { value: 'SICK', label: 'Sick' },
  { value: 'UNPAID', label: 'Unpaid' },
  { value: 'PARENTAL', label: 'Parental' },
];

/** Personel adina izin girme. */
export function LeaveFormDialog({ open, onClose, onSaved }: Props) {
  const [employee, setEmployee] = useState<EmployeeOption | null>(null);
  const [type, setType] = useState<LeaveType>('ANNUAL');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const reset = () => {
    setEmployee(null);
    setType('ANNUAL');
    setStartDate('');
    setEndDate('');
    setNote('');
    setError(null);
  };

  const close = () => {
    reset();
    onClose();
  };

  // Sunucu da ayni kurali uygular ve son sozu odur; buradaki kontrol yalnizca
  // kullaniciyi bosuna bir gidis donuse sokmamak icin.
  const datesInvalid = Boolean(startDate && endDate && endDate < startDate);
  const canSubmit = Boolean(employee && startDate && endDate) && !datesInvalid && !saving;

  const submit = async () => {
    if (!employee) return;

    setSaving(true);
    setError(null);

    try {
      await leaveRequestApi.create({
        employeeId: employee.id,
        type,
        startDate,
        endDate,
        note: note.trim() || undefined,
      });

      reset();
      onSaved();
    } catch (cause) {
      // Cakisma hatasi burada gorunur kalmali: kullanici tarihleri
      // degistirmeden tekrar denerse ayni cevabi alir.
      setError(errorMessage(cause));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="sm">
      <DialogTitle>Record leave</DialogTitle>

      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}

          <EmployeePicker
            value={employee}
            onChange={setEmployee}
            label="Employee"
            helperText="You can only pick people you are allowed to see"
          />

          <TextField
            select
            label="Type"
            value={type}
            onChange={(event) => setType(event.target.value as LeaveType)}
          >
            {TYPES.map((option) => (
              <MenuItem key={option.value} value={option.value}>
                {option.label}
              </MenuItem>
            ))}
          </TextField>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <DateField
              label="First day"
              value={startDate}
              onChange={setStartDate}
              helperText=" "
            />
            <DateField
              label="Last day"
              value={endDate}
              onChange={setEndDate}
              error={datesInvalid}
              helperText={datesInvalid ? 'Cannot be before the first day' : 'This day is included'}
              fullWidth
            />
          </Stack>

          <TextField
            label="Note"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            multiline
            minRows={2}
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        </Stack>
      </DialogContent>

      {/* Dar ekranda birincil dugme ALTTA kalir: parmaga en yakin yer orasi. */}
      <DialogActions sx={{ px: 3, pb: 2, flexDirection: { xs: 'column-reverse', sm: 'row' }, gap: 1 }}>
        <Button onClick={close} disabled={saving} fullWidth={false}>
          Cancel
        </Button>
        <Button variant="contained" onClick={submit} disabled={!canSubmit}>
          Record leave
        </Button>
      </DialogActions>
    </Dialog>
  );
}
