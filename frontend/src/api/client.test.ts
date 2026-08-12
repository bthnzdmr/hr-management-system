import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import type { AxiosAdapter, AxiosRequestConfig, AxiosResponse } from 'axios';
import { api, errorMessage, revokeRefreshToken, setUnauthorizedHandler, tokenStorage } from './client';
import type { ProblemDetail } from '../types/api';

/** Interceptor'i gercek bir istek atmadan calistirir. */
async function runRequestInterceptor() {
  const handler = api.interceptors.request as unknown as {
    handlers: { fulfilled: (config: unknown) => unknown }[];
  };
  const config = { headers: new AxiosHeaders() };
  return (await handler.handlers[0].fulfilled(config)) as { headers: AxiosHeaders };
}

function ok(config: AxiosRequestConfig, data: unknown): AxiosResponse {
  return {
    data, status: 200, statusText: 'OK', headers: {}, config: config as never,
  } as AxiosResponse;
}

function unauthorized(config: AxiosRequestConfig): AxiosError {
  const error = new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config as never);
  error.response = { status: 401, data: {} } as AxiosResponse;
  return error;
}

const originalAdapter = api.defaults.adapter;

describe('request interceptor', () => {
  beforeEach(() => tokenStorage.clear());

  it('attaches the stored token to every request', async () => {
    tokenStorage.set('abc.def.ghi');

    const config = await runRequestInterceptor();

    expect(config.headers.Authorization).toBe('Bearer abc.def.ghi');
  });

  it('sends no authorization header when there is no token', async () => {
    const config = await runRequestInterceptor();

    expect(config.headers.Authorization).toBeUndefined();
  });
});

describe('token refresh', () => {
  beforeEach(() => {
    tokenStorage.clear();
    setUnauthorizedHandler(() => {});
  });

  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  /**
   * Sunucuyu taklit eder: erisim jetonu 'fresh' olana kadar 401 doner,
   * /api/auth/refresh cagrisi yeni bir cift verir.
   */
  function serverThatAcceptsOnly(validToken: string) {
    const adapter = vi.fn<AxiosAdapter>(async (config) => {
      if (config.url?.endsWith('/api/auth/refresh')) {
        return ok(config, {
          token: validToken,
          tokenType: 'Bearer',
          expiresInSeconds: 900,
          refreshToken: 'rotated-refresh',
        });
      }

      const sent = new AxiosHeaders(config.headers).get('Authorization');
      if (sent === `Bearer ${validToken}`) {
        return ok(config, { ok: true });
      }
      throw unauthorized(config);
    });

    api.defaults.adapter = adapter;
    return adapter;
  }

  it('refreshes the access token and retries the failed request', async () => {
    tokenStorage.set('expired');
    tokenStorage.setRefresh('valid-refresh');
    serverThatAcceptsOnly('fresh');

    const response = await api.get('/api/employees');

    expect(response.data).toEqual({ ok: true });
    expect(tokenStorage.get()).toBe('fresh');
  });

  it('stores the rotated refresh token, not the old one', async () => {
    // Sunucu her yenilemede yeni bir jeton veriyor. Eskisi saklanirsa bir
    // sonraki yenileme TEKRAR KULLANIM sayilir ve tum oturumlar kapatilir.
    tokenStorage.set('expired');
    tokenStorage.setRefresh('valid-refresh');
    serverThatAcceptsOnly('fresh');

    await api.get('/api/employees');

    expect(tokenStorage.getRefresh()).toBe('rotated-refresh');
  });

  it('serialises the refresh across tabs, not just within one', async () => {
    // refreshInFlight modul duzeyinde: yalnizca KENDI sekmesini korur.
    // Iki sekme ayni depoyu paylasir; ikisi de yenilemeye kalkarsa biri
    // iptal edilmis jetonu sunar ve sunucu tum oturumlari kapatir.
    // Web Locks kilidi sekmeler arasinda paylasilir.
    const held: string[] = [];
    const locks = {
      request: vi.fn(async (name: string, run: () => Promise<string>) => {
        held.push(name);
        return run();
      }),
    };
    vi.stubGlobal('navigator', { ...navigator, locks });

    tokenStorage.set('expired');
    tokenStorage.setRefresh('valid-refresh');
    serverThatAcceptsOnly('fresh');

    await api.get('/api/employees');

    expect(locks.request).toHaveBeenCalledTimes(1);
    expect(held).toEqual(['hr.token.refresh']);
    expect(tokenStorage.get()).toBe('fresh');

    vi.unstubAllGlobals();
  });

  it('still refreshes where the browser has no Web Locks support', async () => {
    // Kilit yoksa sekme ici koruma kalir; yenileme calismaya devam etmeli.
    vi.stubGlobal('navigator', { ...navigator, locks: undefined });

    tokenStorage.set('expired');
    tokenStorage.setRefresh('valid-refresh');
    serverThatAcceptsOnly('fresh');

    await api.get('/api/employees');

    expect(tokenStorage.get()).toBe('fresh');

    vi.unstubAllGlobals();
  });

  it('refreshes only once when several requests fail at the same time', async () => {
    // Bu bir hiz meselesi degil DOGRULUK meselesi: ikinci yenileme, ilkinin
    // cop ettigi jetonu sunar ve sunucu bunu tekrar kullanim sayip kullanicinin
    // butun oturumlarini kapatir.
    tokenStorage.set('expired');
    tokenStorage.setRefresh('valid-refresh');
    const adapter = serverThatAcceptsOnly('fresh');

    await Promise.all([
      api.get('/api/employees'),
      api.get('/api/departments'),
      api.get('/api/employees/1'),
    ]);

    const refreshCalls = adapter.mock.calls
      .filter(([config]) => config.url?.endsWith('/api/auth/refresh'));
    expect(refreshCalls).toHaveLength(1);
  });

  it('ends the session when there is no refresh token to use', async () => {
    tokenStorage.set('expired');
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    serverThatAcceptsOnly('fresh');

    await expect(api.get('/api/employees')).rejects.toThrow();

    expect(tokenStorage.get()).toBeNull();
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('ends the session when the refresh itself is rejected', async () => {
    tokenStorage.set('expired');
    tokenStorage.setRefresh('revoked-refresh');
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);

    // Yenileme ucu de 401 doner: jeton iptal edilmis.
    api.defaults.adapter = vi.fn<AxiosAdapter>(async (config) => {
      throw unauthorized(config);
    });

    await expect(api.get('/api/employees')).rejects.toThrow();

    expect(tokenStorage.get()).toBeNull();
    expect(tokenStorage.getRefresh()).toBeNull();
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('does not try to refresh a failed sign-in', async () => {
    // Yanlis parola da 401 doner; bunu oturum sona erdi sanmak yanlistir.
    const adapter = vi.fn<AxiosAdapter>(async (config) => {
      throw unauthorized(config);
    });
    api.defaults.adapter = adapter;

    await expect(api.post('/api/auth/login', {})).rejects.toThrow();

    expect(adapter).toHaveBeenCalledOnce();
  });

  it('gives up instead of looping when the refreshed token is also rejected', async () => {
    tokenStorage.set('expired');
    tokenStorage.setRefresh('valid-refresh');
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);

    // Yenileme calisiyor ama yeni jeton da reddediliyor.
    api.defaults.adapter = vi.fn<AxiosAdapter>(async (config) => {
      if (config.url?.endsWith('/api/auth/refresh')) {
        return ok(config, {
          token: 'still-rejected', tokenType: 'Bearer', expiresInSeconds: 900,
          refreshToken: 'rotated-refresh',
        });
      }
      throw unauthorized(config);
    });

    await expect(api.get('/api/employees')).rejects.toThrow();

    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('keeps the session when the identity is valid but the permission is missing', async () => {
    // 403 kimligin gecerli oldugunu soyler; kullaniciyi disari atmak yanlis olur.
    tokenStorage.set('valid');
    tokenStorage.setRefresh('valid-refresh');
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);

    api.defaults.adapter = vi.fn<AxiosAdapter>(async (config) => {
      const error = new AxiosError('Forbidden', 'ERR_BAD_REQUEST', config as never);
      error.response = { status: 403, data: {} } as AxiosResponse;
      throw error;
    });

    await expect(api.get('/api/employees/1/salary')).rejects.toThrow();

    expect(tokenStorage.get()).toBe('valid');
    expect(onUnauthorized).not.toHaveBeenCalled();
  });
});

describe('sign out', () => {
  beforeEach(() => tokenStorage.clear());
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('asks the server to revoke the refresh token', async () => {
    tokenStorage.setRefresh('to-be-revoked');
    const adapter = vi.fn<AxiosAdapter>(async (config) => ok(config, null));
    api.defaults.adapter = adapter;

    await revokeRefreshToken();

    expect(adapter).toHaveBeenCalledOnce();
    expect(adapter.mock.calls[0][0].url).toContain('/api/auth/logout');
  });

  it('does not fail when the server cannot be reached', async () => {
    // Cikis istemci tarafinda her zaman basarilidir; aksi halde kullanici
    // sunucu kapaliyken oturumda kilitli kalirdi.
    tokenStorage.setRefresh('to-be-revoked');
    api.defaults.adapter = vi.fn<AxiosAdapter>(async () => {
      throw new AxiosError('Network Error');
    });

    await expect(revokeRefreshToken()).resolves.toBeUndefined();
  });

  it('calls nothing when there is no refresh token', async () => {
    const adapter = vi.fn<AxiosAdapter>(async (config) => ok(config, null));
    api.defaults.adapter = adapter;

    await revokeRefreshToken();

    expect(adapter).not.toHaveBeenCalled();
  });
});

describe('errorMessage', () => {
  function problemError(problem: Partial<ProblemDetail>): AxiosError<ProblemDetail> {
    const error = new AxiosError<ProblemDetail>('failed');
    error.response = { data: problem as ProblemDetail } as AxiosError<ProblemDetail>['response'];
    return error;
  }

  it('lists field errors returned by validation', () => {
    const message = errorMessage(
      problemError({
        detail: 'Request contains invalid fields',
        errors: [
          { field: 'email', message: 'Email format is invalid' },
          { field: 'jobTitle', message: 'Job title is required' },
        ],
      }),
    );

    expect(message).toContain('email: Email format is invalid');
    expect(message).toContain('jobTitle: Job title is required');
  });

  it('falls back to the problem detail when there are no field errors', () => {
    expect(errorMessage(problemError({ detail: 'Email already registered' })))
      .toBe('Email already registered');
  });

  it('explains an unreachable server instead of showing a raw axios message', () => {
    expect(errorMessage(new AxiosError('Network Error'))).toBe('The server could not be reached');
  });

  it('never leaks an unknown error object to the user', () => {
    expect(errorMessage(new Error('java.lang.NullPointerException at line 42')))
      .toBe('An unexpected error occurred');
  });
});
