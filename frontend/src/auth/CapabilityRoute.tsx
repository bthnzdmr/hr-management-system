import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';

type Capability = 'editEmployees' | 'manageAccounts' | 'viewDashboard';

/**
 * Belirli bir YETENEK gerektiren rotalar.
 *
 * Onceki hali "AdminRoute" idi ve tek bir ADMIN rolu varsayiyordu. Roller
 * bolundugunde rota da bolunmek zorunda kaldi: personel formu Ik uzmanina,
 * hesap ekrani sistem yoneticisine ait ve bunlar artik AYNI kisi olmayabilir.
 *
 * ProtectedRoute gibi bu da bir GUVENLIK onlemi degildir; sunucu her istegi
 * kendisi dogrular. Amac, kullanicinin bir formu bastan sona doldurup
 * gonderdiginde 403 ile karsilasmasini engellemektir.
 */
export function CapabilityRoute({ requires }: { requires: Capability }) {
  const { user, canEditEmployees, canManageAccounts, canViewDashboard } = useAuth();

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  const allowed = {
    editEmployees: canEditEmployees,
    manageAccounts: canManageAccounts,
    viewDashboard: canViewDashboard,
  }[requires];

  if (!allowed) {
    return <Navigate to="/employees" replace />;
  }
  return <Outlet />;
}
