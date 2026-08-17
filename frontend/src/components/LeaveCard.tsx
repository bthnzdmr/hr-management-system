import { Box, Button, Chip, Paper, Stack, Typography } from '@mui/material';
import type { LeaveRequest, LeaveStatus } from '../api/leaveRequests';
import { formatDay } from '../utils/formatDate';

interface Props {
  leave: LeaveRequest;
  statusLabel: { label: string; color: 'default' | 'success' | 'error' | 'warning' };
  typeLabel: string;
  canDecide: boolean;
  busy: boolean;
  onDecide: (status: 'APPROVED' | 'REJECTED' | 'CANCELLED') => void;
  onReject: () => void;
}

/**
 * Dar ekranda tablo satirinin yerini alan kart.
 *
 * Yedi sutunlu izin tablosu telefonda yatay kaydirma gerektiriyordu ve yatay
 * kaydirilan bir liste pratikte kullanilamaz -- ayni kusur personel listesinde
 * bir kez olculmustu. Dokunma hedefleri 44 px: tablodaki "small" dugmeler
 * (~30 px) parmak icin kucuktu.
 */
export function LeaveCard({
  leave, statusLabel, typeLabel, canDecide, busy, onDecide, onReject,
}: Props) {
  const pending: LeaveStatus = 'PENDING';

  return (
    <Paper sx={{ p: 1.75, display: 'flex', flexDirection: 'column', gap: 1.25 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
        <Box sx={{ minWidth: 0, flexGrow: 1 }}>
          <Typography variant="body2" sx={{ fontWeight: 620 }} noWrap>
            {leave.employeeFullName}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
            {typeLabel} · {leave.days} {leave.days === 1 ? 'day' : 'days'}
          </Typography>
        </Box>

        <Chip size="small" label={statusLabel.label} color={statusLabel.color} />
      </Stack>

      <Typography variant="caption" color="text.secondary">
        {leave.startDate === leave.endDate
          ? leave.startDate
          : `${formatDay(leave.startDate)} → ${formatDay(leave.endDate)}`}
        {leave.decidedBy ? ` · decided by ${leave.decidedBy}` : ` · recorded by ${leave.recordedBy}`}
      </Typography>

      {leave.decisionNote && (
        <Typography variant="caption" color="text.secondary">
          {leave.decisionNote}
        </Typography>
      )}

      {canDecide && leave.status === pending && (
        <Stack direction="row" spacing={1}>
          <Button
            fullWidth
            disabled={busy}
            onClick={onReject}
            sx={{ minHeight: 44 }}
          >
            Reject
          </Button>
          <Button
            fullWidth
            variant="contained"
            disabled={busy}
            onClick={() => onDecide('APPROVED')}
            sx={{ minHeight: 44 }}
          >
            Approve
          </Button>
        </Stack>
      )}

      {leave.status === pending && (
        <Button
          size="small"
          color="inherit"
          disabled={busy}
          onClick={() => onDecide('CANCELLED')}
          sx={{ minHeight: 44 }}
        >
          Cancel request
        </Button>
      )}
    </Paper>
  );
}
