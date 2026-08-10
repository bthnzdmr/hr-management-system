import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * Giris yapmamis kullaniciyi giris ekranina yonlendirir.
 *
 * Bu bir GUVENLIK onlemi degildir: kod kullanicinin tarayicisinda calisir ve
 * atlatilabilir. Gercek koruma sunucudadir; buradaki amac, kullaniciya
 * kacinilmaz olarak 401 dondurecek bir ekrani hic gostermemektir.
 */
export function ProtectedRoute() {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    // Nereden geldigini tasiyoruz ki giristen sonra oraya donebilelim.
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Outlet />;
}
