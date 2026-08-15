import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { api, revokeRefreshToken, setUnauthorizedHandler, tokenStorage } from '../api/client';
import { sessionEnded, useAppDispatch } from '../store';
import type { LoginRequest, LoginResponse, Role } from '../types/api';

interface AuthUser {
  email: string;
  roles: Role[];
}

/**
 * Arayuz ROL degil YETENEK sorar.
 *
 * Bilesenlerin icine "rol HR_SPECIALIST mi" diye yazsaydik, rol modeli her
 * degistiginde her ekran degisirdi -- nitekim ADMIN/USER'dan bes role gecerken
 * tam da bu oldu. Yetenek, ekranin gercekten ihtiyac duydugu sorudur:
 * "duzenle dugmesini gostereyim mi?"
 */
interface AuthContextValue {
  user: AuthUser | null;
  hasRole: (role: Role) => boolean;
  /** Personel kaydi olusturma, guncelleme, durum degistirme. */
  canEditEmployees: boolean;
  /** Hesap acma, rol verme, erisim yonetimi. */
  canManageAccounts: boolean;
  /** Maas okuma ve yazma. */
  canSeeSalaries: boolean;
  /** Toplu veri: kapsami sinirli kullanici gormez. */
  canViewDashboard: boolean;
  /** Izin isteklerini sonuclandirma. Hangi isteklere, sunucu karar verir. */
  canDecideLeave: boolean;
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
    // Roller DIZI olarak gelir. Dizi degilse token bizim uretmedigimiz bir
    // bicimdedir; rolsuz kabul etmek, yetkisiz gostermekten iyidir.
    const roles = Array.isArray(payload.roles) ? (payload.roles as Role[]) : [];

    return { email: payload.sub, roles };
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

  // Yalnizca yerel jetonlari silmek yetmez: yenileme jetonunun bir kopyasi
  // kalmissa oturum sunucuda yasamaya devam ederdi. Arayuz cikisi BEKLEMEZ --
  // sunucu cevap vermese bile kullanici cikmis olmalidir.
  const logout = useCallback(() => {
    void revokeRefreshToken();
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

    // api.post<T> bir TIP IDDIASIDIR, dogrulama degil. Sozlesme ihlalinde
    // localStorage.setItem(key, undefined) cagrilir ve depoya "undefined"
    // METNI yazilir -- truthy oldugu icin her 401, mahkum bir yenileme
    // denemesi baslatir ve oturum "girisli ama her yenileme bosa" haline
    // sessizce dusardi.
    if (typeof data.token !== 'string' || typeof data.refreshToken !== 'string') {
      tokenStorage.clear();
      throw new Error('Received an incomplete sign-in response');
    }

    tokenStorage.set(data.token);
    tokenStorage.setRefresh(data.refreshToken);

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
    () => {
      const roles = user?.roles ?? [];
      const hasRole = (role: Role) => roles.includes(role);

      return {
        user,
        hasRole,
        canEditEmployees: hasRole('HR_SPECIALIST'),
        canManageAccounts: hasRole('SYSTEM_ADMIN'),
        // Ucret ayri bir role gecti: ayni kisinin hem kayit acip hem maas
        // atamasi, sahte personel olusturmanin klasik yolu.
        canSeeSalaries: hasRole('PAYROLL_SPECIALIST'),
        // Toplu veri bireysel veriden farklidir: tek tek goremedigi kisilerin
        // toplamini da gormemeli. Kucuk bir grupta toplam, bireyi ele verir.
        canViewDashboard: hasRole('HR_SPECIALIST') || hasRole('SYSTEM_ADMIN'),
        // Yonetici yalnizca dogrudan astlarina, Ik herkese karar verir; bu
        // ayrimi sunucu yapar. Buradaki bayrak dugmeyi gostermek icin.
        canDecideLeave: hasRole('HR_SPECIALIST') || hasRole('MANAGER'),
        login,
        logout,
      };
    },
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
