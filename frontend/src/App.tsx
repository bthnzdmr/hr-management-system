import { Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { AdminRoute } from './auth/AdminRoute';
import { LoginPage } from './pages/LoginPage';
import { EmployeeListPage } from './pages/EmployeeListPage';
import { EmployeeDetailPage } from './pages/EmployeeDetailPage';
import { EmployeeFormPage } from './pages/EmployeeFormPage';
import { UserListPage } from './pages/UserListPage';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { NotFoundPage } from './pages/NotFoundPage';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route path="/employees" element={<EmployeeListPage />} />

          {/* Detay okumadir: her oturum acmis kullaniciya acik. Duzenleme
              ekrani olmadan USER'in tiklayacagi hicbir sey yoktu. */}
          <Route path="/employees/:id/details" element={<EmployeeDetailPage />} />

          {/* Yazma ekranlari yalnizca ADMIN'e: USER formu doldurduktan sonra
              kacinilmaz olarak 403 alirdi. */}
          {/* Kendi parolasini herkes degistirir. */}
          <Route path="/account/password" element={<ChangePasswordPage />} />

          <Route element={<AdminRoute />}>
            <Route path="/employees/new" element={<EmployeeFormPage />} />
            <Route path="/employees/:id" element={<EmployeeFormPage />} />
            <Route path="/users" element={<UserListPage />} />
          </Route>

          {/* Bilinmeyen adres kabugun ICINDE karsilanir: kullanici menusunu
              ve cikis dugmesini kaybetmez. */}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>

      {/* Kok adres tek gercek yonlendirmedir. Onceki halde her yanlis adres
          sessizce listeye gidiyordu ve yazim hatasi hic fark edilmiyordu. */}
      <Route path="/" element={<Navigate to="/employees" replace />} />
    </Routes>
  );
}
