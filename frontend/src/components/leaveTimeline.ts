import type { LeaveRequest } from '../api/leaveRequests';

/**
 * Takvimin SAF katmani: ay penceresi, satirlar ve seritler.
 *
 * Bilesenden ayri durur cunku burada sinanacak sey cizim degil ARITMETIKTIR --
 * hangi gunde basliyor, kac gun suruyor, ay disina tasan izin nerede kirpiliyor.
 */

/** Takvimde GORUNEN durumlar. */
const VISIBLE: LeaveRequest['status'][] = ['APPROVED', 'PENDING'];

export interface LeaveBar {
  leave: LeaveRequest;
  /** Ayin kacinci gununde basliyor (1 tabanli). */
  startDay: number;
  /** Kac gun suruyor -- ay penceresine KIRPILMIS hali. */
  span: number;
  /** Izin ayin basindan once basladi mi? Serit solda acik kalir. */
  clippedStart: boolean;
  clippedEnd: boolean;
}

export interface CalendarRow {
  employeeId: number;
  employeeName: string;
  bars: LeaveBar[];
}

/** `2026-08` -> ayin ilk ve son gunu, ISO. */
export function monthWindow(month: string): { from: string; until: string; days: number } {
  const [year, index] = month.split('-').map(Number);

  // Gun 0, BIR ONCEKI ayin son gunudur: ay uzunlugunu boylece tablo tutmadan
  // ogreniyoruz ve artik yil kendiliginden dogru cikiyor.
  const days = new Date(Date.UTC(year, index, 0)).getUTCDate();
  const pad = (value: number) => String(value).padStart(2, '0');

  return {
    from: `${year}-${pad(index)}-01`,
    until: `${year}-${pad(index)}-${pad(days)}`,
    days,
  };
}

/** Bugunun ayi, `YYYY-MM`. */
export function currentMonth(today = new Date()): string {
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`;
}

/** Ayi kaydirir; yil siniri kendiliginden asilir. */
export function shiftMonth(month: string, delta: number): string {
  const [year, index] = month.split('-').map(Number);
  const shifted = new Date(Date.UTC(year, index - 1 + delta, 1));

  return `${shifted.getUTCFullYear()}-${String(shifted.getUTCMonth() + 1).padStart(2, '0')}`;
}

/**
 * Ayin gunlerini haftanin gunu bilgisiyle birlikte verir.
 *
 * `new Date(y, m, d)` YEREL saat kurar; burada gun numarasi zaten elimizde
 * oldugu icin kayma riski yok, yalnizca haftanin gunu okunuyor.
 */
export function monthDays(month: string): { day: number; weekend: boolean }[] {
  const [year, index] = month.split('-').map(Number);
  const { days } = monthWindow(month);

  return Array.from({ length: days }, (_, offset) => {
    const day = offset + 1;
    const weekday = new Date(Date.UTC(year, index - 1, day)).getUTCDay();

    return { day, weekend: weekday === 0 || weekday === 6 };
  });
}

/** Tarihi ayin gun numarasina cevirir; ay disindaysa null. */
function dayInMonth(isoDate: string, month: string): number | null {
  return isoDate.startsWith(`${month}-`) ? Number(isoDate.slice(8, 10)) : null;
}

/**
 * Izinleri KISI BASINA gruplar ve her birini bir serite cevirir.
 *
 * Reddedilmis ve iptal edilmis izinler DISARIDA birakilir: takvim "kim
 * yerinde degil" sorusunu cevaplar ve iptal edilmis bir izin bir devamsizlik
 * degildir. Onlari cizmek, takvimi yalanci kilardi.
 *
 * Siralama ada gore: sunucunun donus sirasi tarihe gore ve satirlar her ay
 * yer degistirseydi goz ayni kisiyi takip edemezdi.
 */
export function buildRows(leaves: LeaveRequest[], month: string): CalendarRow[] {
  const { days } = monthWindow(month);
  const byEmployee = new Map<number, CalendarRow>();

  leaves
    .filter((leave) => VISIBLE.includes(leave.status))
    .forEach((leave) => {
      const start = dayInMonth(leave.startDate, month);
      const end = dayInMonth(leave.endDate, month);

      // Ay disina tasan uclar pencereye kirpilir. Sunucu ORTUSMEYE gore
      // suzdugu icin buraya gelen her izin ayla kesisiyor; yine de iki uc de
      // disaridaysa (ay tamamen iznin ICINDE) serit butun ayi kaplar.
      const startDay = start ?? 1;
      const endDay = end ?? days;

      const row = byEmployee.get(leave.employeeId) ?? {
        employeeId: leave.employeeId,
        employeeName: leave.employeeFullName,
        bars: [],
      };

      row.bars.push({
        leave,
        startDay,
        span: endDay - startDay + 1,
        clippedStart: start === null,
        clippedEnd: end === null,
      });

      byEmployee.set(leave.employeeId, row);
    });

  return [...byEmployee.values()]
    .map((row) => ({ ...row, bars: row.bars.sort((a, b) => a.startDay - b.startDay) }))
    .sort((a, b) => a.employeeName.localeCompare(b.employeeName));
}

export type CalendarCell = { bar: LeaveBar } | { gap: number };

/**
 * Bir satirin hucreleri: bosluklar ve seritler, gun sirasinda.
 *
 * DEGISMEZ: `colSpan` degerlerinin toplami AY UZUNLUGUNA esit olmalidir.
 * Tutmazsa tablo sessizce kayar -- baslik satirindaki gun numaralari ile
 * altindaki seritler hizasini kaybeder ve takvim yanlis gun gosterir.
 */
export function cellsFor(bars: LeaveBar[], days: number): CalendarCell[] {
  const cells: CalendarCell[] = [];
  let cursor = 1;

  bars.forEach((bar) => {
    // Ust uste binen izin OLAMAZ (veritabaninda dislama kisiti var), ama
    // kirpilmis bir serit oncekiyle ayni gunde baslayabilir; negatif bosluk
    // uretmemek icin ilerleme korunuyor.
    if (bar.startDay > cursor) {
      cells.push({ gap: bar.startDay - cursor });
    }

    if (bar.startDay >= cursor) {
      cells.push({ bar });
      cursor = bar.startDay + bar.span;
    }
  });

  if (cursor <= days) {
    cells.push({ gap: days - cursor + 1 });
  }

  return cells;
}
