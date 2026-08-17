import { useEffect, useState } from 'react';
import { Autocomplete, CircularProgress, TextField } from '@mui/material';
import { employeeApi } from '../api/employees';

export interface EmployeeOption {
  id: number;
  label: string;
}

interface Props {
  value: EmployeeOption | null;
  onChange: (option: EmployeeOption | null) => void;
  label: string;
  helperText?: string;
  /** Listeden cikarilacak kayit; kimse kendi yoneticisi olamaz. */
  excludeId?: number;
  disabled?: boolean;
  /**
   * Alan boyu.
   *
   * Suzgec satirinda yanindaki tarih alanlari `small` iken bu kutu varsayilan
   * `medium` kaliyordu ve ucu yan yana farkli yukseklikte duruyordu.
   */
  size?: 'small' | 'medium';
}

const DEBOUNCE_MS = 300;
const MAX_OPTIONS = 10;

/**
 * Personeli ID yazarak degil, adiyla secmek icin.
 *
 * Onceki hali ham bir sayi kutusuydu: kullanicinin id'yi ezberlemesini ya da
 * baska bir ekrandan kopyalamasini gerektiriyordu. Liste SUNUCUDAN aranir --
 * tum personeli indirip tarayicida filtrelemek, kayit sayisi buyudugunde
 * calismayacak bir cozumdur.
 *
 * Once yalnizca yonetici secimi icindi; hesabi personele baglama ekrani ikinci
 * gercek kullanim olunca genellestirildi. Iki kullanim da "aktif personel ara,
 * birini sec" demek.
 */
export function EmployeePicker({
  value, onChange, label, helperText, excludeId, disabled = false, size = 'medium',
}: Props) {
  const [query, setQuery] = useState('');
  const [options, setOptions] = useState<EmployeeOption[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);

    const timer = setTimeout(() => {
      employeeApi
        .list({
          page: 0,
          size: MAX_OPTIONS,
          sort: 'lastName,asc',
          search: query.trim() || undefined,
          // Pasif bir kisi yonetici atanamaz; sunucu da bunu reddediyor.
          active: true,
        })
        .then((page) => {
          if (!active) return;
          setOptions(
            page.content
              .filter((employee) => excludeId === undefined || employee.id !== excludeId)
              .map((employee) => ({
                id: employee.id,
                label: `${employee.firstName} ${employee.lastName} — ${employee.jobTitle}`,
              })),
          );
        })
        .catch(() => {
          // Aday listesi alinamazsa kutu bos kalir; form yine kaydedilebilir.
          if (active) setOptions([]);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, DEBOUNCE_MS);

    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [query, excludeId]);

  return (
    <Autocomplete
      value={value}
      onChange={(_, option) => onChange(option)}
      onInputChange={(_, text, reason) => {
        // 'reset' secim sonrasi tetiklenir; onu sorgu saymak, secimin hemen
        // ardindan gereksiz bir arama istegi acardi.
        if (reason === 'input') setQuery(text);
      }}
      options={options}
      loading={loading}
      disabled={disabled}
      size={size}
      // Secenekler sunucudan zaten suzulmus geliyor; tarayici ikinci kez
      // suzerse sunucunun bulduklarini gizler.
      filterOptions={(x) => x}
      isOptionEqualToValue={(option, selected) => option.id === selected.id}
      noOptionsText="No matching employee"
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          helperText={helperText}
          slotProps={{
            ...params.slotProps,
            input: {
              ...params.slotProps.input,
              endAdornment: (
                <>
                  {loading ? <CircularProgress size={16} /> : null}
                  {params.slotProps.input.endAdornment}
                </>
              ),
            },
          }}
        />
      )}
    />
  );
}
