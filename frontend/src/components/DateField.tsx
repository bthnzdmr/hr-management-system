import dayjs from 'dayjs';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';

/**
 * Tarih secici.
 *
 * Yerlesik `<input type="date">` kullaniliyordu ve acilan takvim TARAYICININ
 * kendi bileseniydi: icini bicimlendirmek mumkun degil, gorunum tarayiciya
 * gore degisiyor ve alanin icinde tarih daima YIL-AY-GUN yaziyordu.
 *
 * Bu sarmalayici disariya ISO dizgesi ("2026-08-17") konusur, tam olarak
 * eskisi gibi. `dayjs` YALNIZCA burada biliniyor -- her ekrana bir tarih
 * kutuphanesi sizdirmak, sonradan degistirmesi imkansiz bir bagimlilik olurdu.
 * Donusum tek bir sinirda yapiliyor.
 */

/** Sunucunun ve durumun konustugu bicim. */
const ISO = 'YYYY-MM-DD';

/** Kullaniciya gosterilen bicim; `formatDay` ile AYNI olmali. */
const DISPLAY = 'DD-MM-YYYY';

interface Props {
  label: string;
  /** ISO "YYYY-MM-DD"; bos dizge "secilmemis" demektir. */
  value: string;
  onChange: (isoDate: string) => void;
  required?: boolean;
  error?: boolean;
  helperText?: string;
  size?: 'small' | 'medium';
  fullWidth?: boolean;
  /** Bu gunden oncesi secilemez; ISO dizgesi. */
  minDate?: string;
}

export function DateField({
  label, value, onChange, required, error, helperText, size, fullWidth = true, minDate,
}: Props) {
  // `dayjs("2026-08-17")` bu dizgeyi YEREL gece yarisi sayar; `new Date` ile
  // yasanan "bir gun geri kayma" tuzagi burada olusmuyor.
  const parsed = value ? dayjs(value) : null;
  const lower = minDate ? dayjs(minDate) : undefined;

  return (
    // Saglayici BILESENIN ICINDE.
    //
    // Once kokte duruyordu ve 28 test birden dustu: testler sayfalari kabuk
    // olmadan render ediyor, dolayisiyla saglayici yoktu ve secici patlayinca
    // butun sayfa cokuyordu. Kokte tutmak, "birileri saglayiciyi koymayi
    // hatirlarsa calisir" demektir; burada tutmak bileseni KENDI KENDINE
    // yeterli yapiyor.
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <DatePicker
        label={label}
        format={DISPLAY}
        value={parsed?.isValid() ? parsed : null}
        minDate={lower?.isValid() ? lower : undefined}
        // Yarim yazilmis bir tarih ("17-08-") gecersizdir ve BOS dizge olarak
        // disari verilir: yoksa "Invalid Date" sunucuya gonderilirdi.
        onChange={(next) => onChange(next?.isValid() ? next.format(ISO) : '')}
        slotProps={{
          textField: { required, error, helperText, size, fullWidth },
        }}
      />
    </LocalizationProvider>
  );
}
