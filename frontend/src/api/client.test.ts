import { describe, expect, it, vi, beforeEach } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import { api, errorMessage, setUnauthorizedHandler, tokenStorage } from './client';
import type { ProblemDetail } from '../types/api';

/** Interceptor'i gercek bir istek atmadan calistirir. */
async function runRequestInterceptor() {
  const handler = api.interceptors.request as unknown as {
    handlers: { fulfilled: (config: unknown) => unknown }[];
  };
  const config = { headers: new AxiosHeaders() };
  return (await handler.handlers[0].fulfilled(config)) as { headers: AxiosHeaders };
}

async function runResponseErrorInterceptor(status: number) {
  const handler = api.interceptors.response as unknown as {
    handlers: { rejected: (error: unknown) => Promise<unknown> }[];
  };
  const error = new AxiosError('failed');
  error.response = { status } as AxiosError['response'];

  return handler.handlers[0].rejected(error).catch(() => undefined);
}

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

describe('response interceptor', () => {
  beforeEach(() => tokenStorage.clear());

  it('clears the session when the server rejects the identity', async () => {
    tokenStorage.set('expired.token.value');
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);

    await runResponseErrorInterceptor(401);

    expect(tokenStorage.get()).toBeNull();
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('keeps the session when the identity is valid but the permission is missing', async () => {
    // 403 kimligin gecerli oldugunu soyler; kullaniciyi disari atmak yanlis olur.
    tokenStorage.set('valid.token.value');
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);

    await runResponseErrorInterceptor(403);

    expect(tokenStorage.get()).toBe('valid.token.value');
    expect(onUnauthorized).not.toHaveBeenCalled();
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
