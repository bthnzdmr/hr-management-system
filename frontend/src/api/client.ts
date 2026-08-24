import axios, { AxiosError } from 'axios';
import type { AxiosRequestConfig } from 'axios';
import type { LoginResponse, ProblemDetail } from '../types/api';

const TOKEN_KEY = 'hr.token';
const REFRESH_KEY = 'hr.refreshToken';

/**
 * Jetonlar localStorage'da tutulur.
 *
 * Bedeli acikca bilinir: XSS varsa okunabilirler. En guvenlisi httpOnly
 * cerezdir ama o zaman CSRF korumasinin geri acilmasi gerekir -- cerezi
 * tarayici otomatik gonderdigi icin CSRF yeniden gecerli hale gelir.
 *
 * Erisim jetonu 15 dakikalik. Yenileme jetonu uzun omurlu ama SUNUCUDA
 * kayitli: calindigi anlasilirsa iptal edilebilir, erisim jetonunda bu
 * imkan yoktur.
 */
export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  getRefresh: () => localStorage.getItem(REFRESH_KEY),
  setRefresh: (token: string) => localStorage.setItem(REFRESH_KEY, token),
  clear: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
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

/** Oturum gercekten bitince uygulamanin haberdar olmasi icin. */
type UnauthorizedHandler = () => void;
let onUnauthorized: UnauthorizedHandler = () => {};

export function setUnauthorizedHandler(handler: UnauthorizedHandler) {
  onUnauthorized = handler;
}

const LOGIN_PATH = '/api/auth/login';
const REFRESH_PATH = '/api/auth/refresh';
const LOGOUT_PATH = '/api/auth/logout';

/** Yeniden denendigini isaretler: bir istek en fazla BIR kez tekrarlanir. */
interface RetriableConfig extends AxiosRequestConfig {
  _retried?: boolean;
}

/**
 * Suren yenileme istegi.
 *
 * Ayni anda bes istek 401 alirsa bes yenileme yapilmamalidir: ilki eskiyi
 * dondururken digerleri ARTIK GECERSIZ olan jetonu sunar, sunucu bunu tekrar
 * kullanim sayar ve kullanicinin butun oturumlarini kapatir. Yani sagligi
 * korumak icin degil, DOGRULUK icin tek ucus sart.
 */
let refreshInFlight: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const refreshToken = tokenStorage.getRefresh();
  if (!refreshToken) {
    throw new Error('No refresh token');
  }

  // Ayni ornek kullanilabilir: asagidaki interceptor kimlik uclarini zaten
  // disarida birakiyor, dolayisiyla yenilemenin 401'i yeni bir yenileme
  // tetiklemez ve sonsuz dongu olusmaz.
  const { data } = await api.post<LoginResponse>(REFRESH_PATH, { refreshToken });

  tokenStorage.set(data.token);
  // Sunucu her yenilemede YENI bir yenileme jetonu verir (rotation).
  // Eskisini saklamak, bir sonraki yenilemede tekrar kullanim alarmi demektir.
  tokenStorage.setRefresh(data.refreshToken);

  return data.token;
}

/**
 * Yenilemeyi SEKMELER ARASINDA da sirala.
 *
 * refreshInFlight modul duzeyinde bir degisken, yani yalnizca kendi sekmesini
 * korur. Iki sekme ayni localStorage'i paylasiyor: ikisinin de jetonu ayni
 * anda dolarsa ikisi de yenileme baslatir, biri dondurur, digerinin istegi
 * ARTIK IPTAL EDILMIS jetonla varir. Sunucu bunu -- kendi tasarimi geregi
 * dogru sekilde -- calinmis jeton tekrari sayip butun oturumlari kapatir.
 *
 * Web Locks API kilidi sekmeler arasinda paylasilir. Kilidi alan taraf
 * refreshAccessToken icinde jetonu YENIDEN OKUR, boylece yarisi kaybeden
 * sekme eskisini tekrarlamak yerine dondurulmus jetonu alir.
 */
function withCrossTabLock(run: () => Promise<string>): Promise<string> {
  // Eski tarayicida ve test ortaminda yok; o zaman sekme ici koruma kalir.
  if (!navigator.locks) {
    return run();
  }
  return navigator.locks.request('hr.token.refresh', run);
}

function refreshOnce(): Promise<string> {
  if (!refreshInFlight) {
    refreshInFlight = withCrossTabLock(refreshAccessToken).finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ProblemDetail>) => {
    const config = error.config as RetriableConfig | undefined;
    const url = config?.url ?? '';

    // Kimlik uclarinin kendisi disarida: yanlis parola da 401 doner ve bu,
    // baska bir sekmedeki GECERLI oturumu kapatmamalidir. Yenileme ucunun
    // 401'i ise zaten "oturum bitti" demektir.
    const isAuthCall = url.endsWith(LOGIN_PATH) || url.endsWith(REFRESH_PATH)
      || url.endsWith(LOGOUT_PATH);

    // 401: kimlik gecersiz. 403: kimlik gecerli ama yetki yok -> oturum durur,
    // sayfa hatayi gosterir; yenilemek hicbir seyi degistirmez.
    if (error.response?.status !== 401 || isAuthCall) {
      return Promise.reject(error);
    }

    // _retried: yenilenmis token ile de 401 aliniyorsa sorun token'in eskiligi
    // degildir; tekrar denemek sonsuz donguye girerdi.
    const canRetry = config !== undefined
      && !config._retried
      && tokenStorage.getRefresh() !== null;

    if (canRetry) {
      let token: string;

      // YALNIZCA yenileme cagrisi sarmalanir. Tekrarlanan istek de iceride
      // olsaydi, onun 401'i once kendi interceptor turunde oturumu kapatir,
      // sonra buradaki catch bir kez daha kapatirdi.
      try {
        token = await refreshOnce();
      } catch {
        tokenStorage.clear();
        onUnauthorized();
        return Promise.reject(error);
      }

      config._retried = true;
      config.headers = { ...config.headers, Authorization: `Bearer ${token}` };

      // Hatasi kendi turunde ele alinir: _retried isaretli oldugu icin
      // yeniden yenilemeye kalkismaz.
      return api.request(config);
    }

    tokenStorage.clear();
    onUnauthorized();
    return Promise.reject(error);
  },
);

/** Cikista sunucudaki yenileme jetonunu da iptal eder. */
export async function revokeRefreshToken(): Promise<void> {
  const refreshToken = tokenStorage.getRefresh();
  if (!refreshToken) return;

  try {
    await api.post(LOGOUT_PATH, { refreshToken });
  } catch {
    // Cikis ISTEMCI tarafinda her zaman basarilidir: sunucuya ulasilamasa
    // bile jetonlar siliniyor. Aksi halde kullanici cikamadan kalirdi.
  }
}

/**
 * Backend'in ProblemDetail cevabini kullaniciya gosterilecek metne cevirir.
 * Ic hata detayi sizmaz; backend zaten genel mesaj donuyor.
 */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError<ProblemDetail>(error)) {
    const problem = error.response?.data;

    // Yalnizca ALAN hatalari boyle yazilir. `errors` dizisi baska bir sekilde
    // de gelebiliyor -- ice aktarma reddi `line`/`reason` tasiyor -- ve
    // kosulsuz eslemek ekrana "undefined: undefined" basardi.
    const fieldErrors = problem?.errors?.filter(
      (e) => typeof e?.field === 'string' && typeof e?.message === 'string');

    if (fieldErrors?.length) {
      return fieldErrors.map((e) => `${e.field}: ${e.message}`).join(' · ');
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
