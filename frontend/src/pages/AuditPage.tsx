import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert, Chip, MenuItem, Paper, Skeleton, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TablePagination, TableRow, TextField, Typography,
} from '@mui/material';
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import { auditApi } from '../api/audit';
import type { AuditAction, AuditEntry } from '../api/audit';
import { errorMessage } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { EmptyState } from '../components/EmptyState';

/** Eylem -> etiket ve renk. Tek tanim: iki yerde tutulsa biri geride kalirdi. */
const ACTIONS: { value: AuditAction; label: string; color: 'default' | 'success' | 'error' | 'warning' }[] = [
  { value: 'ACCOUNT_CREATED', label: 'Account created', color: 'success' },
  { value: 'ROLES_CHANGED', label: 'Roles changed', color: 'warning' },
  { value: 'ACCOUNT_STATUS_CHANGED', label: 'Account status', color: 'warning' },
  { value: 'EMPLOYEE_CREATED', label: 'Employee created', color: 'success' },
  { value: 'EMPLOYEE_STATUS_CHANGED', label: 'Employee status', color: 'warning' },
  { value: 'SALARY_CHANGED', label: 'Salary changed', color: 'error' },
  { value: 'DEPARTMENT_CREATED', label: 'Department created', color: 'default' },
  { value: 'DEPARTMENT_STATUS_CHANGED', label: 'Department status', color: 'default' },
  { value: 'LEAVE_REQUESTED', label: 'Leave recorded', color: 'default' },
  { value: 'LEAVE_DECIDED', label: 'Leave decided', color: 'default' },
];

const LABELS = new Map(ACTIONS.map((a) => [a.value, a]));

export function AuditPage() {
  const [rows, setRows] = useState<AuditEntry[] | null>(null);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [actor, setActor] = useState('');
  const [action, setAction] = useState<AuditAction | ''>('');
  const [error, setError] = useState<string | null>(null);

  /** Her istege sira numarasi: bayat cevap guncel filtreyi ezmemeli. */
  const requestId = useRef(0);

  const load = useCallback(async () => {
    const id = requestId.current + 1;
    requestId.current = id;
    setError(null);

    try {
      const data = await auditApi.list({
        actor: actor.trim() || undefined,
        action: action || undefined,
        page,
        size,
      });

      if (requestId.current !== id) return;

      setRows(data.content);
      setTotal(data.totalElements);
    } catch (cause) {
      if (requestId.current !== id) return;

      setError(errorMessage(cause));
      setRows([]);
    }
  }, [actor, action, page, size]);

  useEffect(() => {
    // Yazarken her tusa istek atmamak icin kisa bir bekleme.
    const timer = setTimeout(() => void load(), 250);
    return () => clearTimeout(timer);
  }, [load]);

  return (
    <Stack spacing={2.5}>
      <PageHeader
        eyebrow="Administration"
        title="Activity"
        description="Who changed what, and when. Salary amounts are never written here."
      />

      {error && <Alert severity="error">{error}</Alert>}

      <Paper sx={{ p: { xs: 2, md: 2.5 } }}>
        <Stack spacing={2}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              label="Who"
              value={actor}
              onChange={(event) => {
                setActor(event.target.value);
                setPage(0);
              }}
              placeholder="email"
              size="small"
              sx={{ minWidth: 220 }}
            />
            <TextField
              select
              label="What"
              value={action}
              onChange={(event) => {
                setAction(event.target.value as AuditAction | '');
                setPage(0);
              }}
              size="small"
              sx={{ minWidth: 220 }}
            >
              <MenuItem value="">Everything</MenuItem>
              {ACTIONS.map((option) => (
                <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
              ))}
            </TextField>
          </Stack>

          {rows === null && (
            <Stack spacing={1}>
              {Array.from({ length: 6 }).map((_, index) => (
                <Skeleton key={index} variant="rounded" height={40} />
              ))}
            </Stack>
          )}

          {rows !== null && rows.length === 0 && error === null && (
            <EmptyState
              icon={<HistoryOutlinedIcon />}
              title="Nothing recorded yet"
              description="Changes to accounts, employees, departments and leave show up here."
            />
          )}

          {rows !== null && rows.length > 0 && (
            <>
              <TableContainer sx={{ overflowX: 'auto' }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      {['When', 'Who', 'What', 'Target', 'Detail'].map((column) => (
                        <TableCell key={column}>{column}</TableCell>
                      ))}
                    </TableRow>
                  </TableHead>

                  <TableBody>
                    {rows.map((entry) => (
                      <TableRow key={entry.id} hover>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>
                          {entry.occurredAt.replace('T', ' ').slice(0, 19)}
                        </TableCell>
                        <TableCell>{entry.actor}</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={LABELS.get(entry.action)?.label ?? entry.action}
                            color={LABELS.get(entry.action)?.color ?? 'default'}
                          />
                        </TableCell>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>
                          {entry.targetType}{entry.targetId ? ` #${entry.targetId}` : ''}
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" color="text.secondary">
                            {entry.detail ?? '—'}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>

              <TablePagination
                component="div"
                count={total}
                page={page}
                rowsPerPage={size}
                rowsPerPageOptions={[25, 50, 100]}
                onPageChange={(_, next) => setPage(next)}
                onRowsPerPageChange={(event) => {
                  setSize(Number(event.target.value));
                  setPage(0);
                }}
              />
            </>
          )}
        </Stack>
      </Paper>
    </Stack>
  );
}
