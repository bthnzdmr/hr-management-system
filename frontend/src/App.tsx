import { Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { CapabilityRoute } from './auth/CapabilityRoute';
import { LoginPage } from './pages/LoginPage';
import { EmployeeListPage } from './pages/EmployeeListPage';
import { LeaveListPage } from './pages/LeaveListPage';
import { EmployeeDetailPage } from './pages/EmployeeDetailPage';
import { EmployeeFormPage } from './pages/EmployeeFormPage';
import { UserListPage } from './pages/UserListPage';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { DashboardPage } from './pages/DashboardPage';
import { OrgChartPage } from './pages/OrgChartPage';
import { DepartmentListPage } from './pages/DepartmentListPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { useAuth } from './auth/AuthContext';

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
    <Routes>
      <Route path="/login" element={<LoginPage />} />

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
  );
}
