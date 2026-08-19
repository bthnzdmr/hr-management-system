import { api } from './client';

/**
 * Personel listesini CSV olarak indirir.
 *
 * Duz bir `<a href>` KULLANILAMAZ: uc kimlik dogrulamasi istiyor ve token
 * `Authorization` basliginda tasiniyor -- tarayici bir baglantiya baslik
 * eklemez. Bu yuzden dosya istemci uzerinden cekilip blob olarak veriliyor.
 *
 * Suzgecler listeleme ekraniyla AYNI: kullanicinin ekranda gordugu ile
 * indirdigi ayni olmali, yoksa dosya sessizce baska bir seyi anlatirdi.
 */
export const exportApi = {
  employees: (params: { search?: string; active?: boolean }) =>
    api
      .get<Blob>('/api/exports/employees', {
        params: {
          search: params.search?.trim() ? params.search.trim() : undefined,
          active: params.active,
        },
        responseType: 'blob',
      })
      .then((response) => {
        // Dosya adi sunucudan geliyor: iki yerde uretilseydi biri zamanla
        // digerinden ayrilirdi.
        const disposition = response.headers['content-disposition'] as string | undefined;
        const match = disposition?.match(/filename="([^"]+)"/);

        return { blob: response.data, filename: match?.[1] ?? 'employees.csv' };
      }),
};

/** Blob'u kullaniciya indirtir ve nesne adresini SERBEST BIRAKIR. */
export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');

  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();

  // Serbest birakilmazsa blob sekme kapanana kadar bellekte kalir; birkac
  // aktarma sonra bu gercek bir sizintidir.
  URL.revokeObjectURL(url);
}
