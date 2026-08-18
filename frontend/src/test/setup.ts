import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup, configure } from '@testing-library/react';

// Rotalar TEMBEL yuklendigi icin bir sayfayi gormek en az iki dinamik import
// sinirini gecmeyi gerektiriyor (once kabuk, sonra sayfa). Testing Library'nin
// varsayilan 1 sn'lik beklemesi en agir sayfada sinirda kaliyordu ve gecmesi
// makinenin o anki hizina bagliydi.
//
// Bu bir yavaslik ortbasi degil: olculen sey gercek bir gecikme degil, jsdom'da
// modul cozumlemesinin bedeli. Sinir tek bir teste degil KURULUMA konuyor,
// cunku artik butun rotalar icin gecerli.
configure({ asyncUtilTimeout: 3000 });

// jsdom matchMedia'yi uygulamaz; duyarli yerlesim ve koyu tema tercihi bunu
// okur. Genislik sorgulari (min-width) EVET, digerleri HAYIR cevabi alir:
// boylece testler masaustu yerlesiminde ve acik temada calisir. Dar ekran
// yerlesiminde cekmece kapali bir modal icinde kalir ve icindeki baglantilar
// erisilebilirlik agacinda gorunmez.
vi.stubGlobal('matchMedia', (query: string) => ({
  matches: query.includes('min-width'),
  media: query,
  onchange: null,
  addEventListener: () => {},
  removeEventListener: () => {},
  addListener: () => {},
  removeListener: () => {},
  dispatchEvent: () => false,
}));

// Her test kendi durumundan baslamali: onceki testin DOM'u ve saklanan
// token'i bir sonrakine sizmamalidir.
afterEach(() => {
  cleanup();
  localStorage.clear();
});
