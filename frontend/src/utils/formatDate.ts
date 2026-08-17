/**
 * Tarihler tek bir yerden bicimlendirilir.
 *
 * Sunucu ISO gonderir (yil once) ve ekranlar bunu HAM basiyordu: "2026-08-17".
 * Dogru ama okunmasi zor.
 *
 * Bicim GUN-AY-YIL: "17-08-2026". Onceki hal ayi ADIYLA yaziyordu ("17 Aug
 * 2026") ve gerekcesi 03/04 belirsizligini hic olusturmamakti; kullanici
 * sayisal bicim istedi ve karar onun. Bilinen bedel kayda geciyor: 01-12-2023
 * ile 12-01-2023, bicimi bilmeyen bir okuyucu icin ayirt edilemez. Ayrac nokta
 * degil TIRE, cunku 01.12.2023 bazi yerlerde ondalik gibi okunuyor.
 *
 * Yerel ayara HIC bakilmiyor. `Intl` kullanilsaydi ayrac ve sira makinenin
 * yereline gore degisir, ayni ekran her makinede farkli basar ve test
 * edilemezdi -- ucret bicimlendirmesinde ayni karar 'en-US' olarak bir kez
 * verilmisti.
 */

const pad = (value: number) => String(value).padStart(2, '0');

/**
 * Yalnizca tarih tasiyan bir dizgeyi ("2026-08-17") bicimlendirir.
 *
 * Dizge PARCALANIYOR, `new Date` kurulmuyor. Sebep bir tuzak: `new Date(
 * '2026-08-17')` bu dizgeyi UTC GECE YARISI sayar ve negatif ofsetli bir saat
 * diliminde ekranda BIR ONCEKI GUN gorunur -- izin tarihlerinde bu, iznin bir
 * gun kaymasi demektir. Parcalari dogrudan kullanmak tuzagi tamamen kaldiriyor.
 */
export function formatDay(isoDate: string): string {
  const [year, month, day] = isoDate.split('-');

  // Beklenmedik bir deger sessizce bozulmasin: ham hali daha durust.
  if (!year || !month || !day) return isoDate;

  return `${day}-${month}-${year}`;
}

/** Zaman damgasini ("2026-08-17T09:15:30Z") tarih ve saat olarak bicimlendirir. */
export function formatDateTime(isoTimestamp: string): string {
  const at = new Date(isoTimestamp);

  // Gecersiz bir damga sessizce "Invalid Date" basardi; ham deger daha durust.
  if (Number.isNaN(at.getTime())) return isoTimestamp;

  // Damga bir ANDIR, gun degil: yerel saate cevrilmesi dogru davranis.
  return `${pad(at.getDate())}-${pad(at.getMonth() + 1)}-${at.getFullYear()}`
    + ` ${pad(at.getHours())}:${pad(at.getMinutes())}`;
}
