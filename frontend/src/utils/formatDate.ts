/**
 * Tarihler tek bir yerden bicimlendirilir.
 *
 * Sunucu ISO gonderir (yil once) ve uc ekran bunu HAM basiyordu: "2026-08-17".
 * Dogru ama okunmasi zor -- ve gun/ay sirasi ulkeye gore ters okunabilir.
 *
 * Ay ADIYLA yaziliyor: "17 Aug 2026". Boylece 03/04 belirsizligi hic olusmuyor,
 * yani bicim "gun once mi ay once mi" tartismasindan bagimsiz.
 *
 * Yerel ayar SABIT ('en-GB'). Makinenin yereline birakilsaydi ayni ekran her
 * makinede farkli basar ve test edilemezdi -- ucret bicimlendirmesinde ayni
 * karar 'en-US' olarak bir kez verilmisti.
 */
const LOCALE = 'en-GB';

const DAY = new Intl.DateTimeFormat(LOCALE, {
  day: '2-digit', month: 'short', year: 'numeric',
});

const DAY_AND_TIME = new Intl.DateTimeFormat(LOCALE, {
  day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
});

/**
 * Yalnizca tarih tasiyan bir dizgeyi ("2026-08-17") bicimlendirir.
 *
 * `new Date('2026-08-17')` bu dizgeyi UTC GECE YARISI sayar; negatif ofsetli
 * bir saat diliminde ekranda BIR ONCEKI GUN gorunurdu. Izin tarihlerinde bu,
 * iznin bir gun kaymasi demek olurdu. Parcalari elle vererek yerel gun kurulur.
 */
export function formatDay(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number);

  if (!year || !month || !day) return isoDate;

  return DAY.format(new Date(year, month - 1, day));
}

/** Zaman damgasini ("2026-08-17T09:15:30Z") tarih ve saat olarak bicimlendirir. */
export function formatDateTime(isoTimestamp: string): string {
  const at = new Date(isoTimestamp);

  // Gecersiz bir damga sessizce "Invalid Date" basardi; ham deger daha durust.
  return Number.isNaN(at.getTime()) ? isoTimestamp : DAY_AND_TIME.format(at);
}
