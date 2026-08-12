import { useCallback, useEffect, useState } from 'react';
import {
  Alert, Box, Button, Checkbox, Chip, IconButton, ListItemText, MenuItem, Paper, Skeleton, Stack,
  Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField,
  Tooltip, Typography,
} from '@mui/material';
import BlockIcon from '@mui/icons-material/Block';
import PersonAddAltOutlinedIcon from '@mui/icons-material/PersonAddAltOutlined';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import { userApi } from '../api/users';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useSnackbar } from '../components/SnackbarProvider';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { UserCreateDialog } from '../components/UserCreateDialog';
import { ASSIGNABLE_ROLES, ROLE_DESCRIPTIONS, ROLE_LABELS } from '../types/api';
import type { Role, User } from '../types/api';

const COLUMNS = ['Email', 'Role', 'Linked employee', 'Status', 'Actions'];

/**
 * Durum Redux'ta DEGIL, burada tutuluyor.
 *
 * Personel listesinin aksine burada arama, filtre ve siralama yok ve veriyi
 * baska hicbir bilesen okumuyor. Bu ekran icin bir dilim acmak, Redux'un
 * cozdugu bir problem olmadan Redux eklemek olurdu.
 */
export function UserListPage() {
  const { user: currentUser } = useAuth();
  const { notify } = useSnackbar();

  const [users, setUsers] = useState<User[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [pendingDeactivation, setPendingDeactivation] = useState<User | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await userApi.list(page, size);
      setUsers(result.content);
      setTotalElements(result.totalElements);
      setError(null);
    } catch (cause) {
      setError(errorMessage(cause));
      setUsers([]);
      setTotalElements(0);
    } finally {
      setLoading(false);
    }
  }, [page, size]);

  useEffect(() => {
    void load();
  }, [load]);

  const replace = (updated: User) =>
    setUsers((current) => current.map((item) => (item.id === updated.id ? updated : item)));

  const applyStatus = useCallback(
    async (target: User, active: boolean) => {
      setBusyId(target.id);
      try {
        replace(await userApi.changeStatus(target.id, active));
        notify(active ? `${target.email} activated` : `${target.email} deactivated`);
      } catch (cause) {
        // Sunucunun is kurali mesaji burada gorunur: "son yonetici kalmali" gibi.
        notify(errorMessage(cause), 'error');
      } finally {
        setBusyId(null);
      }
    },
    [notify],
  );

  const applyRoles = useCallback(
    async (target: User, roles: Role[]) => {
      // Bos kume gonderilmez: sunucu da reddediyor, ama kullaniciya hata
      // gostermek yerine hic gondermemek daha anlamli.
      if (roles.length === 0) {
        notify('An account must keep at least one role', 'warning');
        return;
      }

      setBusyId(target.id);
      try {
        replace(await userApi.changeRoles(target.id, roles));
        notify(`Roles updated for ${target.email}`);
      } catch (cause) {
        notify(errorMessage(cause), 'error');
      } finally {
        setBusyId(null);
      }
    },
    [notify],
  );

  return (
    <Stack spacing={2.5}>
      <Box sx={{
        display: 'flex',
        flexDirection: { xs: 'column', sm: 'row' },
        alignItems: { xs: 'stretch', sm: 'flex-start' },
        justifyContent: 'space-between',
        gap: 2,
      }}>
        <Box>
          <Typography variant="h5" component="h1">
            Accounts
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {totalElements} {totalElements === 1 ? 'account' : 'accounts'} that can sign in
          </Typography>
        </Box>

        <Button
          variant="contained"
          startIcon={<PersonAddAltOutlinedIcon />}
          onClick={() => setCreating(true)}
        >
          New account
        </Button>
      </Box>

      {error && <Alert severity="error">{error}</Alert>}

      <Paper>
        <TableContainer sx={{ overflowX: 'auto' }}>
          <Table>
            <TableHead>
              <TableRow>
                {COLUMNS.map((column) => (
                  <TableCell key={column} align={column === 'Actions' ? 'right' : 'left'}>
                    {column}
                  </TableCell>
                ))}
              </TableRow>
            </TableHead>

            <TableBody>
              {loading &&
                Array.from({ length: Math.min(size, 4) }).map((_, index) => (
                  <TableRow key={`skeleton-${index}`}>
                    {COLUMNS.map((column) => (
                      <TableCell key={column}>
                        <Skeleton variant="text" />
                      </TableCell>
                    ))}
                  </TableRow>
                ))}

              {!loading &&
                users.map((account) => {
                  // Kendi hesabini kapatmak veya rolunu dusurmek sunucuda da
                  // reddediliyor; burada gizlemek kullaniciya bosuna denetme
                  // yasatmamak icin.
                  const isSelf = account.email === currentUser?.email;

                  return (
                    <TableRow key={account.id} hover>
                      <TableCell>
                        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                          <Typography variant="body2">{account.email}</Typography>
                          {isSelf && <Chip label="You" size="small" variant="outlined" />}
                        </Stack>
                      </TableCell>

                      <TableCell>
                        <TextField
                          select
                          size="small"
                          value={account.roles.filter((role) => ASSIGNABLE_ROLES.includes(role))}
                          disabled={isSelf || busyId === account.id}
                          onChange={(event) =>
                            applyRoles(account, event.target.value as unknown as Role[])}
                          sx={{ minWidth: 190 }}
                          // Tabloyu dar tutmak icin gorunur etiket yok; ekran
                          // okuyucunun "hangi satirin rolleri" diyebilmesi icin
                          // erisilebilir ad e-postayla birlikte verilir.
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
                              <Checkbox
                                size="small"
                                checked={account.roles.includes(role)}
                                sx={{ mr: 0.5 }}
                              />
                              <ListItemText
                                primary={ROLE_LABELS[role]}
                                secondary={ROLE_DESCRIPTIONS[role]}
                              />
                            </MenuItem>
                          ))}
                        </TextField>
                      </TableCell>

                      <TableCell>
                        {account.employeeFullName ?? (
                          <Typography variant="body2" color="text.secondary">
                            Not linked
                          </Typography>
                        )}
                      </TableCell>

                      <TableCell>
                        <Chip
                          label={account.active ? 'Active' : 'Inactive'}
                          size="small"
                          color={account.active ? 'success' : 'default'}
                          variant={account.active ? 'filled' : 'outlined'}
                        />
                      </TableCell>

                      <TableCell align="right">
                        <Tooltip title={account.active ? 'Deactivate' : 'Activate'}>
                          <span>
                            <IconButton
                              size="small"
                              aria-label={account.active ? 'Deactivate' : 'Activate'}
                              disabled={isSelf || busyId === account.id}
                              onClick={() =>
                                account.active
                                  ? setPendingDeactivation(account)
                                  : applyStatus(account, true)
                              }
                            >
                              {account.active ? (
                                <BlockIcon fontSize="small" />
                              ) : (
                                <RestartAltIcon fontSize="small" color="primary" />
                              )}
                            </IconButton>
                          </span>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  );
                })}

              {!loading && users.length === 0 && (
                <TableRow>
                  <TableCell colSpan={COLUMNS.length} align="center" sx={{ py: 6 }}>
                    <Typography color="text.secondary">
                      {error ? 'Could not load accounts' : 'No accounts yet'}
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>

        <TablePagination
          component="div"
          count={totalElements}
          page={page}
          rowsPerPage={size}
          rowsPerPageOptions={[10, 20, 50]}
          onPageChange={(_, next) => setPage(next)}
          onRowsPerPageChange={(event) => {
            setSize(Number(event.target.value));
            setPage(0);
          }}
        />
      </Paper>

      <UserCreateDialog
        open={creating}
        onClose={() => setCreating(false)}
        onCreated={(created) => {
          notify(`${created.email} created`);
          setCreating(false);
          void load();
        }}
      />

      <ConfirmDialog
        open={pendingDeactivation !== null}
        title="Deactivate account"
        confirmLabel="Deactivate"
        confirmColor="error"
        busy={busyId !== null}
        description={
          <Stack spacing={1.5}>
            <Typography variant="body2">
              {pendingDeactivation?.email} will no longer be able to sign in.
            </Typography>
            <Alert severity="info">
              Any session this account already has is ended immediately.
            </Alert>
          </Stack>
        }
        onCancel={() => setPendingDeactivation(null)}
        onConfirm={() => {
          const target = pendingDeactivation;
          setPendingDeactivation(null);
          if (target) applyStatus(target, false);
        }}
      />
    </Stack>
  );
}
