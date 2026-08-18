import { Box, Stack, Typography, alpha, useTheme } from '@mui/material';
import type { LeaveRequest } from '../api/leaveRequests';
import { formatDay } from '../utils/formatDate';
// Saf katman AYRI bir adda durur, `leaveCalendar.ts` degil: yalnizca buyuk
// kucuk harfle ayrilan iki dosya, harf duyarsiz bir dosya sisteminde (Windows,
// macOS) birbirine cozulur ve import sessizce yanlis modulu getirir. Linux'ta
// -- yani CI ve konteynerde -- ayni kod DOGRU cozulur, dolayisiyla ariza
// platforma gore degisir. Olculdu: bileseni `undefined` olarak import etti.
import { buildRows, cellsFor, monthDays } from './leaveTimeline';
import type { LeaveBar } from './leaveTimeline';

/**
 * Kisi x gun seridi.
 *
 * NEDEN AY IZGARASI DEGIL: bir izin ARALIKTIR. Ay izgarasinda alti gunluk bir
 * izin alti ayri hucreye bolunur ve "bu kisi bir hafta yok" bilgisi ancak
 * hucreler birlestirilerek CIKARSANIR. Serit onu dogrudan gosterir -- org
 * chart'ta ogrenilen dersin aynisi: kodlamayi verinin SEKLINE gore sec.
 *
 * NEDEN GERCEK BIR `table`: satirlar kisi, sutunlar gun; bu tam olarak bir
 * tablodur. Serit `colSpan` ile ifade edilince ekran okuyucu "Ada Lovelace,
 * 5 gun" diye okuyabiliyor ve klavye gezinmesi bedava geliyor. CSS izgarasi
 * ayni gorunumu verirdi ama iliskiyi yalnizca GORSEL olarak kurardi.
 */

const NAME_WIDTH = 180;
const DAY_WIDTH = 30;

const TYPE_LABELS: Record<LeaveRequest['type'], string> = {
  ANNUAL: 'Annual',
  SICK: 'Sick',
  UNPAID: 'Unpaid',
  PARENTAL: 'Parental',
};

interface Props {
  leaves: LeaveRequest[];
  month: string;
  /** Bugunu isaretlemek icin; yalnizca goruntulenen ay bu ayken kullanilir. */
  today?: string;
}

export function LeaveCalendar({ leaves, month, today }: Props) {
  const theme = useTheme();
  const rows = buildRows(leaves, month);
  const days = monthDays(month);
  const todayDay = today?.startsWith(`${month}-`) ? Number(today.slice(8, 10)) : null;

  const weekendTint = alpha(theme.palette.text.primary, 0.04);
  const todayTint = alpha(theme.palette.primary.main, 0.12);

  if (rows.length === 0) return null;

  return (
    // Kaydirma KENDI kabinda kalir; sayfa govdesi asla yatay kaymaz.
    <Box sx={{ overflowX: 'auto', minWidth: 0 }}>
      <Box
        component="table"
        sx={{
          borderCollapse: 'separate',
          borderSpacing: 0,
          minWidth: NAME_WIDTH + days.length * DAY_WIDTH,
          tableLayout: 'fixed',
        }}
      >
        <caption
          // Baslik gorseldir; tablonun adi ekran okuyucuya buradan gider.
          style={{
            captionSide: 'top',
            textAlign: 'left',
            padding: 0,
            height: 1,
            width: 1,
            overflow: 'hidden',
            clipPath: 'inset(50%)',
            position: 'absolute',
          }}
        >
          Leave by person for {month}
        </caption>

        {/* Hafta sonu ve bugun golgesi `col` katmaninda: hucreler birden cok
            gunu kapsadigi icin gunluk zemini hucreye yazmak IMKANSIZ. */}
        <colgroup>
          <col style={{ width: NAME_WIDTH }} />
          {days.map(({ day, weekend }) => (
            <col
              key={day}
              style={{
                width: DAY_WIDTH,
                backgroundColor:
                  day === todayDay ? todayTint : weekend ? weekendTint : undefined,
              }}
            />
          ))}
        </colgroup>

        <thead>
          <tr>
            <Box
              component="th"
              scope="col"
              sx={{
                position: 'sticky',
                left: 0,
                zIndex: 2,
                bgcolor: 'background.paper',
                textAlign: 'left',
                p: 1,
                borderBottom: 1,
                borderColor: 'divider',
              }}
            >
              <Typography variant="caption" color="text.secondary">Person</Typography>
            </Box>

            {days.map(({ day }) => (
              <Box
                component="th"
                scope="col"
                key={day}
                sx={{
                  p: 0.5,
                  borderBottom: 1,
                  borderColor: 'divider',
                  fontWeight: day === todayDay ? 700 : 400,
                }}
              >
                <Typography
                  variant="caption"
                  color={day === todayDay ? 'primary.main' : 'text.secondary'}
                >
                  {day}
                </Typography>
              </Box>
            ))}
          </tr>
        </thead>

        <tbody>
          {rows.map((row) => (
            <tr key={row.employeeId}>
              <Box
                component="th"
                scope="row"
                sx={{
                  position: 'sticky',
                  left: 0,
                  zIndex: 1,
                  bgcolor: 'background.paper',
                  textAlign: 'left',
                  p: 1,
                  borderBottom: 1,
                  borderColor: 'divider',
                  fontWeight: 400,
                }}
              >
                <Typography variant="body2" noWrap title={row.employeeName}>
                  {row.employeeName}
                </Typography>
              </Box>

              {cellsFor(row.bars, days.length).map((cell, index) =>
                'gap' in cell ? (
                  <Box
                    component="td"
                    key={`gap-${index}`}
                    colSpan={cell.gap}
                    sx={{ borderBottom: 1, borderColor: 'divider', height: 36 }}
                  />
                ) : (
                  <Box
                    component="td"
                    key={`bar-${cell.bar.leave.id}`}
                    colSpan={cell.bar.span}
                    sx={{ borderBottom: 1, borderColor: 'divider', p: '3px 2px' }}
                  >
                    <Bar bar={cell.bar} />
                  </Box>
                ),
              )}
            </tr>
          ))}
        </tbody>
      </Box>
    </Box>
  );
}

/**
 * Seridin kendisi.
 *
 * Onaylanmis izin DOLU, bekleyen izin KESIKLI CERCEVE. Ikisini yalnizca renkle
 * ayirmak yeterli olmazdi: renk tek basina bilgi tasiyamaz -- ayni ders aylik
 * ayrilma grafiginde bir kez ogrenildi. Durum ayrica erisilebilir ADIN icinde.
 */
function Bar({ bar }: { bar: LeaveBar }) {
  const { leave, span, clippedStart, clippedEnd } = bar;
  const pending = leave.status === 'PENDING';

  const range = leave.startDate === leave.endDate
    ? formatDay(leave.startDate)
    : `${formatDay(leave.startDate)} to ${formatDay(leave.endDate)}`;

  const label = `${leave.employeeFullName}: ${TYPE_LABELS[leave.type]} leave, ${range}`
    + `, ${leave.days} ${leave.days === 1 ? 'day' : 'days'}`
    + (pending ? ', awaiting a decision' : '');

  return (
    <Stack
      // Kirpilmis uc DUZ birakilir: yuvarlak bir uc "izin burada bitti" der,
      // oysa ay penceresinin disina tasiyor.
      sx={{
        height: 30,
        px: 0.75,
        justifyContent: 'center',
        borderRadius: 1,
        borderTopLeftRadius: clippedStart ? 0 : undefined,
        borderBottomLeftRadius: clippedStart ? 0 : undefined,
        borderTopRightRadius: clippedEnd ? 0 : undefined,
        borderBottomRightRadius: clippedEnd ? 0 : undefined,
        overflow: 'hidden',
        bgcolor: (theme) => pending
          ? alpha(theme.palette.warning.main, 0.16)
          : alpha(theme.palette.primary.main, 0.22),
        border: 1,
        borderStyle: pending ? 'dashed' : 'solid',
        borderColor: (theme) => pending ? theme.palette.warning.main : theme.palette.primary.main,
      }}
    >
      <Typography
        variant="caption"
        noWrap
        // Tam metin ipucunda; dar seritte yazi sigmaz ve KIRPILIR, kaybolmaz.
        title={label}
        sx={{ lineHeight: 1.2 }}
      >
        {/* Erisilebilir ad her zaman TAM; gorunen metin yere gore kisalir. */}
        <Box component="span" sx={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clipPath: 'inset(50%)' }}>
          {label}
        </Box>
        <Box component="span" aria-hidden>
          {span >= 4 ? TYPE_LABELS[leave.type] : span >= 2 ? TYPE_LABELS[leave.type].slice(0, 3) : ''}
          {pending && span >= 7 ? ' · pending' : ''}
        </Box>
      </Typography>
    </Stack>
  );
}
