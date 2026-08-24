import axios from 'axios';
import { api } from './client';

/** Reddedilen bir satir ve gerekcesi. */
export interface RowError {
  /** Dosyadaki satir numarasi; baslik 1'dir. */
  line: number;
  reason: string;
}

export interface ImportReport {
  imported: number;
  errors: RowError[];
  rejected: boolean;
}

/**
 * Personel listesini CSV'den yukler.
 *
 * HEPSI YA DA HICBIRI: tek gecersiz satir butun dosyayi reddeder ve sunucu
 * `422` doner. Bu bir istisna, "sonuc" degil -- hicbir sey yazilmamisken
 * basarili donmek, olmayan bir islemi bildirmek olurdu.
 */
export const importApi = {
  employees: (csv: string) =>
    api
      .post<ImportReport>('/api/imports/employees', csv, {
        headers: { 'Content-Type': 'text/csv' },
      })
      .then((r) => r.data),
};

/**
 * Reddin satir gerekcelerini cikarir; ret degilse `null`.
 *
 * Sunucu bunlari `ProblemDetail`in `errors` alaninda tasiyor ama SEKLI
 * dogrulama hatalarindan farkli (`line`/`reason`, `field`/`message` degil).
 * `ProblemDetail`in tipini genisletmek, dogrulama hatalari icin yalan
 * soylemek olurdu; bicim burada, kullanildigi yerde okunuyor.
 */
export function rowErrorsOf(cause: unknown): RowError[] | null {
  if (!axios.isAxiosError(cause) || cause.response?.status !== 422) {
    return null;
  }

  const errors = (cause.response.data as { errors?: unknown })?.errors;

  if (!Array.isArray(errors)) {
    return null;
  }

  // Bicim dogrulanir, VARSAYILMAZ: `api.post<T>` bir TIP IDDIASIDIR, calisma
  // zamani kontrolu degil. Ayni ders giris cevabinda bir kez alinmisti --
  // eksik bir alan sessizce `undefined` olarak akmisti.
  //
  // Bicim onemli cunku `ProblemDetail.errors` BASKA bir sekilde de gelir:
  // dogrulama hatalari `field`/`message` tasir. Suzgec olmasaydi onlar
  // "satir gerekcesi" sanilirdi.
  const rows = errors.filter((e): e is RowError =>
    typeof e === 'object' && e !== null
    && typeof (e as RowError).line === 'number'
    && typeof (e as RowError).reason === 'string');

  // BOS liste `null` doner, bos dizi DEGIL. Aksi halde cagiran taraf onu
  // "ret ama gerekce yok" sayar ve pencere BASARILI gorunurdu -- hicbir sey
  // yazilmamisken "0 kisi eklendi" demek, olmayan bir islemi bildirmektir.
  return rows.length > 0 ? rows : null;
}
