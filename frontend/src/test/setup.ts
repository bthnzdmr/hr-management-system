import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// Her test kendi durumundan baslamali: onceki testin DOM'u ve saklanan
// token'i bir sonrakine sizmamalidir.
afterEach(() => {
  cleanup();
  localStorage.clear();
});
