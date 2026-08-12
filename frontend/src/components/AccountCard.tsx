import {
  Box, Checkbox, Chip, IconButton, ListItemText, MenuItem, Paper, Stack, TextField, Tooltip,
  Typography,
} from '@mui/material';
import BlockIcon from '@mui/icons-material/Block';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import { ASSIGNABLE_ROLES, ROLE_DESCRIPTIONS, ROLE_LABELS } from '../types/api';
import type { Role, User } from '../types/api';

interface Props {
  account: User;
  isSelf: boolean;
  busy: boolean;
  onRolesChange: (roles: Role[]) => void;
  onToggleStatus: () => void;
}

/**
 * Dar ekranda hesap tablosunun satirinin yerini alan kart.
 *
 * Personel listesindekiyle ayni gerekce: bes sutunlu tablo telefonda yatay
 * kaydirma gerektiriyor. Rol secimi burada tam genislik alir; tabloda
 * sikistirilmis bir kutuydu.
 */
export function AccountCard({ account, isSelf, busy, onRolesChange, onToggleStatus }: Props) {
  return (
    <Paper sx={{ p: 1.75, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <Box sx={{ minWidth: 0, flexGrow: 1 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Typography variant="body2" sx={{ fontWeight: 620 }} noWrap>
              {account.email}
            </Typography>
            {isSelf && <Chip label="You" size="small" variant="outlined" />}
          </Stack>

          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
            {account.employeeFullName ?? 'Not linked to an employee'}
          </Typography>
        </Box>

        <Chip
          label={account.active ? 'Active' : 'Inactive'}
          size="small"
          color={account.active ? 'success' : 'default'}
          variant={account.active ? 'filled' : 'outlined'}
        />
      </Stack>

      <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-end' }}>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          {/* Etiket GORUNUR bir metin, TextField'in label'i degil: label
              kullanildiginda MUI aria-labelledby bagliyor ve erisilebilir ad
              her kartta "Roles" oluyor -- birden fazla hesapta ayirt edilemez
              hale gelirdi. */}
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.25 }}>
            Roles
          </Typography>

          <TextField
            select
            size="small"
            fullWidth
            value={account.roles.filter((role) => ASSIGNABLE_ROLES.includes(role))}
            disabled={isSelf || busy}
            onChange={(event) => onRolesChange(event.target.value as unknown as Role[])}
            slotProps={{
              select: {
                multiple: true,
                renderValue: (selected) => (selected as Role[])
                  .map((role) => ROLE_LABELS[role])
                  .join(', '),
                SelectDisplayProps: { 'aria-label': `Roles for ${account.email}` },
              },
            }}
          >
            {ASSIGNABLE_ROLES.map((role) => (
              <MenuItem key={role} value={role}>
                <Checkbox size="small" checked={account.roles.includes(role)} sx={{ mr: 0.5 }} />
                <ListItemText primary={ROLE_LABELS[role]} secondary={ROLE_DESCRIPTIONS[role]} />
              </MenuItem>
            ))}
          </TextField>
        </Box>

        {/* 44 px dokunma hedefi. */}
        <Tooltip title={account.active ? 'Deactivate' : 'Activate'}>
          <span>
            <IconButton
              aria-label={account.active ? 'Deactivate' : 'Activate'}
              disabled={isSelf || busy}
              onClick={onToggleStatus}
              sx={{ width: 44, height: 44 }}
            >
              {account.active
                ? <BlockIcon fontSize="small" />
                : <RestartAltIcon fontSize="small" color="primary" />}
            </IconButton>
          </span>
        </Tooltip>
      </Stack>
    </Paper>
  );
}
