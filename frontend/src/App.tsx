import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { CapabilityRoute } from './auth/CapabilityRoute';
import { LoginPage } from './pages/LoginPage';
import { useAuth } from './auth/AuthContext';

/**
 * Sayfalar TEMBEL yuklenir; her rota kendi parcasina gider ve ancak
 * ziyaret edilince iner.
 *
 * Onceden butun uygulama TEK bir dosyaydi (960 kB): giris ekranini acan biri
 * organizasyon haritasini, d3'u, tarih secicilerini, panoyu ve denetim
 * ekranini da indiriyordu -- oysa o ekranda iki kutu ve bir dugme var.
 *
 * `LoginPage` BILEREK istekli birakildi. Ilk boyamada gorunen tek ekran o;
 * tembellestirmek, kullaniciyi karsilayan seyin onune fazladan bir gidis
 * donus koyardi. Kabuk (Layout) da istekli: zaten her korumali sayfada var.
 *
 * `lazy` yalnizca VARSAYILAN disa aktarimi (default export) alir; bu sayfalar
 * adlandirilmis disa aktarim kullaniyor, o yuzden her biri modulden secilip
 * `default` olarak sarmalaniyor.
 */
// Kabuk da tembel: giris yapmamis bir ziyaretci kenar cubugunu, cekmeceyi ve
// menuleri hic gormeyecek. Kimlik dogrulanmis kullanici icin bedeli yok --
// kabuk ile sayfa parcasi ayni anda, paralel iniyor.
const Layout = lazy(() =>
  import('./components/Layout').then((m) => ({ default: m.Layout })));

const ForgotPasswordPage = lazy(() =>
  import('./pages/ForgotPasswordPage').then((m) => ({ default: m.ForgotPasswordPage })));
const ResetPasswordPage = lazy(() =>
  import('./pages/ResetPasswordPage').then((m) => ({ default: m.ResetPasswordPage })));
const EmployeeListPage = lazy(() =>
  import('./pages/EmployeeListPage').then((m) => ({ default: m.EmployeeListPage })));
const LeaveListPage = lazy(() =>
  import('./pages/LeaveListPage').then((m) => ({ default: m.LeaveListPage })));
const EmployeeDetailPage = lazy(() =>
  import('./pages/EmployeeDetailPage').then((m) => ({ default: m.EmployeeDetailPage })));
const EmployeeFormPage = lazy(() =>
  import('./pages/EmployeeFormPage').then((m) => ({ default: m.EmployeeFormPage })));
const UserListPage = lazy(() =>
  import('./pages/UserListPage').then((m) => ({ default: m.UserListPage })));
const AuditPage = lazy(() =>
  import('./pages/AuditPage').then((m) => ({ default: m.AuditPage })));
const ChangePasswordPage = lazy(() =>
  import('./pages/ChangePasswordPage').then((m) => ({ default: m.ChangePasswordPage })));
const DashboardPage = lazy(() =>
  import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const OrgChartPage = lazy(() =>
  import('./pages/OrgChartPage').then((m) => ({ default: m.OrgChartPage })));
const DepartmentListPage = lazy(() =>
  import('./pages/DepartmentListPage').then((m) => ({ default: m.DepartmentListPage })));
const NotFoundPage = lazy(() =>
  import('./pages/NotFoundPage').then((m) => ({ default: m.NotFoundPage })));

/**
 * Kok adres, kullanicinin gerceklestirebilecegi ilk ekrana gider.
 *
 * Herkesi panele gondermek olmazdi: EMPLOYEE paneli goremez ve acilista
 * kacinilmaz bir yonlendirme yer.
 */
function LandingRedirect() {
  const { canViewDashboard } = useAuth();

  return <Navigate to={canViewDashboard ? '/dashboard' : '/employees'} replace />;
}

export default function App() {
  return (
    // Kabuk DISINDAKI tembel rotalar (parola unutma ve sifirlama) icin sinir.
    // Kabugun icindekiler Layout'taki Suspense'e dusuyor; ic ice Suspense'te
    // en yakini kazanir, dolayisiyla ikisi cakismiyor. Burada yedek icerik
    // BOS: bu ekranlarda henuz bir kabuk yok, bos bir sayfada beliren tek bir
    // cubuk yalnizca dikkat dagitirdi.
    <Suspense fallback={null}>
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      {/* Kabugun DISINDA: parolasini unutan kisi tanimi geregi giris
          yapamaz, korumali bir rotanin arkasinda olsalardi ulasilamazlardi. */}
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route path="/employees" element={<EmployeeListPage />} />
          <Route path="/leave" element={<LeaveListPage />} />

          {/* Detay okumadir: her oturum acmis kullaniciya acik. Duzenleme
              ekrani olmadan USER'in tiklayacagi hicbir sey yoktu. */}
          <Route path="/employees/:id/details" element={<EmployeeDetailPage />} />

          {/* Kendi parolasini herkes degistirir. */}
          <Route path="/account/password" element={<ChangePasswordPage />} />

          {/* Organizasyon semasi da TOPLU yapisal veri: sunucu ayni rolleri
              istiyor, arayuz de ayni yetenegi soruyor. */}
          <Route element={<CapabilityRoute requires="viewDashboard" />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/org-chart" element={<OrgChartPage />} />
          </Route>

          {/* Personel formu Ik uzmanina, hesap ekrani sistem yoneticisine
              ait. Roller bolundugunde bunlar ayni kisi olmayabilir. */}
          <Route element={<CapabilityRoute requires="editEmployees" />}>
            {/* Departman referans verisidir ve yazmasi Ik uzmanina ait;
                sunucudaki kuralla ayni yetenek soruluyor. */}
            <Route path="/departments" element={<DepartmentListPage />} />
            <Route path="/employees/new" element={<EmployeeFormPage />} />
            <Route path="/employees/:id" element={<EmployeeFormPage />} />
          </Route>

          <Route element={<CapabilityRoute requires="manageAccounts" />}>
            <Route path="/users" element={<UserListPage />} />

            {/* Denetim izi de hesap yonetimiyle ayni kitleye ait: sunucu
                SYSTEM_ADMIN istiyor, arayuz ayni yetenegi soruyor. */}
            <Route path="/activity" element={<AuditPage />} />
          </Route>

          {/* Bilinmeyen adres kabugun ICINDE karsilanir: kullanici menusunu
              ve cikis dugmesini kaybetmez. */}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>

      {/* Kok adres tek gercek yonlendirmedir. Onceki halde her yanlis adres
          sessizce listeye gidiyordu ve yazim hatasi hic fark edilmiyordu. */}
      <Route path="/" element={<LandingRedirect />} />
    </Routes>
    </Suspense>
  );
}
