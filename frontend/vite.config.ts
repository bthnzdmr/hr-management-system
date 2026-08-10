// defineConfig vitest/config'ten gelir: vite'in kendi surumu test anahtarini
// tanimaz ve tip kontrolu basarisiz olur.
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    // Testler tarayici API'lerine (localStorage, DOM) ihtiyac duyar;
    // jsdom bunlari Node icinde taklit eder.
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
