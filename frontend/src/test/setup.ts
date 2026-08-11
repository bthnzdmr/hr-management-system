import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

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
