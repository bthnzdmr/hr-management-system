// defineConfig vitest/config'ten gelir: vite'in kendi surumu test anahtarini
// tanimaz ve tip kontrolu basarisiz olur.
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],

  // Derleme damgasi pakete GOMULUR. Bilerek her derlemede degisiyor: amaci
  // yeniden uretilebilirlik degil, KIMLIK -- "ekranda gordugum sey hangi
  // yapi" sorusu tahminle degil okumayla cevaplanabilsin.
  define: {
    __BUILD_TIME__: JSON.stringify(new Date().toISOString()),
  },
  test: {
    // Testler tarayici API'lerine (localStorage, DOM) ihtiyac duyar;
    // jsdom bunlari Node icinde taklit eder.
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Testing Library'nin bekleme butcesi 3 sn (bkz. src/test/setup.ts) ve
    // Vitest'in varsayilan test butcesi 5 sn. Ikisi bu kadar yakin oldugunda
    // bir `waitFor` kendi suresini doldurmadan TESTIN butcesi biter: hata
    // "sunu bulamadim" yerine "test zaman asimina ugradi" diye rapor edilir
    // ve sebep gorunmez olur.
    //
    // Bir testin butcesi, ICERDIGI beklemeden belirgin sekilde buyuk olmali.
    testTimeout: 15000,
  },
});
