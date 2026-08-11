import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * Yalnizca ADMIN rolune acik rotalar.
 *
 * ProtectedRoute gibi bu da bir GUVENLIK onlemi degildir; sunucu her yazma
 * istegini kendisi dogrular. Amac, kullanicinin bir formu bastan sona
 * doldurup gonderdiginde 403 ile karsilasmasini engellemektir.
 */
export function AdminRoute() {
  const { user, isAdmin } = useAuth();

  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (!isAdmin) {
    return <Navigate to="/employees" replace />;
  }
  return <Outlet />;
}
