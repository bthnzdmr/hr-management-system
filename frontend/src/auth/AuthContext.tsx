import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { api, setUnauthorizedHandler, tokenStorage } from '../api/client';
import { sessionEnded, useAppDispatch } from '../store';
import type { LoginRequest, LoginResponse, Role } from '../types/api';

interface AuthUser {
  email: string;
  role: Role;
}

interface AuthContextValue {
  user: AuthUser | null;
  isAdmin: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * JWT'nin govdesini cozer.
 *
 * Token IMZALIDIR, SIFRELI DEGILDIR: icerigini herkes okuyabilir, garanti
 * edilen tek sey degistirilememesidir. Burada rolu okumamiz yalnizca arayuzu
 * sekillendirmek icindir -- yetki kararini SUNUCU verir. Kullanici token'i
 * elle degistirip kendini ADMIN gosterse bile sunucu imzayi dogrular ve
 * yazma isteklerini reddeder.
 */
function readToken(token: string): AuthUser | null {
  try {
    // base64url -> base64: JWT '+' ve '/' yerine '-' ve '_' kullanabilir,
    // atob bunlari kabul etmez.
    const encoded = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(encoded));

    // exp YOKSA gecersiz sayilir. Onceden eksik exp icin karsilastirma
    // NaN uretiyor, NaN karsilastirmasi false donuyor ve token sonsuza
    // kadar gecerli kabul ediliyordu.
    if (typeof payload.exp !== 'number' || Date.now() >= payload.exp * 1000) {
      return null;
    }
    if (typeof payload.sub !== 'string') {
      return null;
    }
    return { email: payload.sub, role: payload.role as Role };
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  // Sayfa yenilendiginde oturumun kaybolmamasi icin baslangic degeri
  // saklanan token'dan okunur.
  const [user, setUser] = useState<AuthUser | null>(() => {
    const token = tokenStorage.get();
    if (!token) return null;

    const parsed = readToken(token);
    if (!parsed) tokenStorage.clear();
    return parsed;
  });

  const dispatch = useAppDispatch();

  // Oturum bittiginde Redux store da sifirlanir; aksi halde ayni tarayicida
  // giris yapan ikinci kullanici oncekinin listesini gorur.
  const endSession = useCallback(() => {
    setUser(null);
    dispatch(sessionEnded());
  }, [dispatch]);

  const logout = useCallback(() => {
    tokenStorage.clear();
    endSession();
  }, [endSession]);

  // Sunucu token'i reddettiginde (401) oturum burada da kapanir; aksi halde
  // arayuz kullaniciyi giris yapmis sanmaya devam ederdi.
  useEffect(() => {
    setUnauthorizedHandler(endSession);

    // Temizlik sart: handler modul seviyesinde tek bir yuvada duruyor.
    // Birakilmazsa bu saglayici sokuldukten sonra bile eski setUser'i
    // tutmaya devam eder.
    return () => setUnauthorizedHandler(() => {});
  }, [endSession]);

  const login = useCallback(async (credentials: LoginRequest) => {
    const { data } = await api.post<LoginResponse>('/api/auth/login', credentials);

    tokenStorage.set(data.token);

    const parsed = readToken(data.token);
    if (!parsed) {
      tokenStorage.clear();
      throw new Error('Received an unreadable token');
    }
    setUser(parsed);
  }, []);

  // useMemo olmadan her render'da yeni bir nesne uretilir ve context'i
  // tuketen her bilesen sebepsiz yeniden render olur.
  const value = useMemo<AuthContextValue>(
    () => ({ user, isAdmin: user?.role === 'ADMIN', login, logout }),
    [user, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
