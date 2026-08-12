import { Box, Chip, IconButton, Paper, Stack, Tooltip, Typography } from '@mui/material';
import BlockIcon from '@mui/icons-material/Block';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import { InitialsAvatar } from './InitialsAvatar';
import type { Employee } from '../types/api';

interface Props {
  employee: Employee;
  canEdit: boolean;
  busy: boolean;
  onView: () => void;
  onEdit: () => void;
  onToggleStatus: () => void;
}

/**
 * Dar ekranda tablo satirinin yerini alan kart.
 *
 * Tabloyu daraltmak cozum degildi: yedi sutun telefonda yatay kaydirma
 * gerektiriyor ve yatay kaydirilan bir liste pratikte kullanilamaz. Ayni veri,
 * farkli bicim -- isim ve unvan birlikte, ikincil bilgi tek satirda.
 *
 * Dokunma hedefleri 44 px: masaustundeki 32 px'lik ikon dugmeleri parmak icin
 * kucuk kaliyordu.
 */
export function EmployeeCard({ employee, canEdit, busy, onView, onEdit, onToggleStatus }: Props) {
  const fullName = `${employee.firstName} ${employee.lastName}`;

  return (
    <Paper sx={{ p: 1.75, display: 'flex', flexDirection: 'column', gap: 1.25 }}>
      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
        <InitialsAvatar firstName={employee.firstName} lastName={employee.lastName} size={40} />

        <Box sx={{ minWidth: 0, flexGrow: 1 }}>
          <Typography variant="body2" sx={{ fontWeight: 620 }} noWrap>
            {fullName}
          </Typography>
          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
            {employee.jobTitle} · {employee.departmentName}
          </Typography>
        </Box>

        <Chip
          label={employee.active ? 'Active' : 'Inactive'}
          size="small"
          color={employee.active ? 'success' : 'default'}
          variant={employee.active ? 'filled' : 'outlined'}
        />
      </Stack>

      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <Typography variant="caption" color="text.secondary" noWrap sx={{ flexGrow: 1, minWidth: 0 }}>
          {employee.managerFullName
            ? `Reports to ${employee.managerFullName}`
            : 'No manager'}
        </Typography>

        {/* 44 px dokunma hedefi: parmak icin en kucuk guvenli olcu. */}
        <Tooltip title="View details">
          <IconButton aria-label="View details" onClick={onView} sx={{ width: 44, height: 44 }}>
            <VisibilityOutlinedIcon fontSize="small" />
          </IconButton>
        </Tooltip>

        {canEdit && (
          <>
            <Tooltip title="Edit">
              <IconButton aria-label="Edit" onClick={onEdit} sx={{ width: 44, height: 44 }}>
                <EditOutlinedIcon fontSize="small" />
              </IconButton>
            </Tooltip>

            <Tooltip title={employee.active ? 'Deactivate' : 'Reactivate'}>
              <span>
                <IconButton
                  aria-label={employee.active ? 'Deactivate' : 'Reactivate'}
                  disabled={busy}
                  onClick={onToggleStatus}
                  sx={{ width: 44, height: 44 }}
                >
                  {employee.active
                    ? <BlockIcon fontSize="small" />
                    : <RestartAltIcon fontSize="small" color="primary" />}
                </IconButton>
              </span>
            </Tooltip>
          </>
        )}
      </Stack>
    </Paper>
  );
}
