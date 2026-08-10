import axios, { AxiosError } from 'axios';
import type { ProblemDetail } from '../types/api';

const TOKEN_KEY = 'hr.token';

/**
 * Token localStorage'da tutulur.
 *
 * Bedeli acikca bilinir: XSS varsa token okunabilir. Bunu kabul edilebilir
 * kilan sey token'in 15 dakikalik olmasidir. En guvenlisi httpOnly cerezdir
 * ama o zaman CSRF korumasinin geri acilmasi gerekir.
 */
export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  timeout: 10_000,
});

// Token'i her istege elle eklemek yerine tek yerde ekliyoruz: unutma ihtimali
// kalmiyor ve saklama bicimi degisirse yalnizca burasi degisiyor.
api.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** Oturum gecersiz kalinca uygulamanin haberdar olmasi icin. */
type UnauthorizedHandler = () => void;
let onUnauthorized: UnauthorizedHandler = () => {};

export function setUnauthorizedHandler(handler: UnauthorizedHandler) {
  onUnauthorized = handler;
}

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ProblemDetail>) => {
    // 401: kimlik gecersiz -> oturumu kapat.
    // 403: kimlik gecerli ama yetki yok -> oturum durur, sayfa hata gosterir.
    if (error.response?.status === 401) {
      tokenStorage.clear();
      onUnauthorized();
    }
    return Promise.reject(error);
  },
);

/**
 * Backend'in ProblemDetail cevabini kullaniciya gosterilecek metne cevirir.
 * Ic hata detayi sizmaz; backend zaten genel mesaj donuyor.
 */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError<ProblemDetail>(error)) {
    const problem = error.response?.data;

    if (problem?.errors?.length) {
      return problem.errors.map((e) => `${e.field}: ${e.message}`).join(' · ');
    }
    if (problem?.detail) {
      return problem.detail;
    }
    if (error.code === 'ECONNABORTED') {
      return 'The server did not respond in time';
    }
    if (!error.response) {
      return 'The server could not be reached';
    }
  }
  return 'An unexpected error occurred';
}
