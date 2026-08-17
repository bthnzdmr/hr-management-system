import { useEffect, useRef, useState } from 'react';
import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField,
} from '@mui/material';
import { leaveRequestApi } from '../api/leaveRequests';
import type { LeaveType } from '../api/leaveRequests';
import { leaveBalanceApi } from '../api/leaveBalances';
import type { LeaveBalance } from '../api/leaveBalances';
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
  const [balance, setBalance] = useState<LeaveBalance | null>(null);

  /**
   * Bakiye isteklerinin sira numarasi.
   *
   * Kisi A secilip hemen B'ye gecildiginde A'nin YAVAS donen cevabi B'nin
   * bakiyesini ezebilirdi. Bu proje ayni hatayi onay penceresinde ve
   * sayfalamada iki kez yasadi; kalibi tekrarliyoruz.
   */
  const balanceRequest = useRef(0);

  const reset = () => {
    setEmployee(null);
    setType('ANNUAL');
    setStartDate('');
    setEndDate('');
    setNote('');
    setError(null);
    setBalance(null);
  };

  const close = () => {
    reset();
    onClose();
  };

  // Sunucu da ayni kurali uygular ve son sozu odur; buradaki kontrol yalnizca
  // kullaniciyi bosuna bir gidis donuse sokmamak icin.
  const datesInvalid = Boolean(startDate && endDate && endDate < startDate);
  const canSubmit = Boolean(employee && startDate && endDate) && !datesInvalid && !saving;

  // Yil, girilen baslangic tarihinden okunur: 2027'ye izin isteyen kisinin
  // 2026 bakiyesini gormesi yaniltici olurdu. Yarim yazilmis bir tarih
  // ("20") gecerli bir yil degildir ve icinde bulunulan yila dusulur.
  const typedYear = startDate.slice(0, 4);
  const year = /^\d{4}$/.test(typedYear) ? Number(typedYear) : new Date().getFullYear();

  useEffect(() => {
    // Bakiye YALNIZCA yillik izinde anlamli; digerleri bu haktan dusmuyor.
    if (!employee || type !== 'ANNUAL') {
      setBalance(null);
      return;
    }

    const seq = balanceRequest.current + 1;
    balanceRequest.current = seq;

    leaveBalanceApi.get(employee.id, year)
      .then((next) => {
        if (seq === balanceRequest.current) setBalance(next);
      })
      .catch(() => {
        // Bakiye alinamazsa form calismaya devam eder; son sozu zaten sunucu
        // soyluyor ve talep orada reddedilir.
        if (seq === balanceRequest.current) setBalance(null);
      });
  }, [employee, type, year]);

  const requestedDays = startDate && endDate && !datesInvalid
    ? Math.round(
      (new Date(endDate).getTime() - new Date(startDate).getTime()) / 86400000,
    ) + 1
    : 0;

  const exceedsBalance = balance !== null && requestedDays > balance.availableDays;

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

          {/* Bakiye tarih alanlarindan SONRA: once kac gun istendigi belli
              olur, sonra yetip yetmedigi. Ters sirada kullanici sayiyi
              bagimsiz bir bilgi gibi okurdu. */}
          {balance && (
            <Alert severity={exceedsBalance ? 'warning' : 'info'} variant="outlined">
              <strong>{balance.availableDays}</strong>
              {' of '}
              {balance.entitledDays + balance.carriedOverDays}
              {` annual leave day(s) left in ${balance.year}`}
              {balance.reservedDays > 0 && ` — ${balance.reservedDays} awaiting a decision`}

              {/* Varsayilan bir KARAR degil, bir tahmindir; ayirt edilir. */}
              {balance.source === 'DEFAULT'
                && ' (no entitlement recorded yet, showing the default)'}

              {exceedsBalance && (
                <div>
                  This request needs <strong>{requestedDays}</strong> day(s) and will be rejected.
                </div>
              )}
            </Alert>
          )}

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
