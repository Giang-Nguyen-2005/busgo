import { Link, Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "./AuthProvider";
import { Empty, ErrorState, Loading } from "../../components/ui";
import { isSystemAdmin } from "./access";

export function SystemAdminGuard() {
  const auth = useAuth();
  const location = useLocation();
  if (auth.loading) return <Loading />;
  if (!auth.authenticated) return <Navigate replace to={`/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`} />;
  if (auth.error) return <ErrorState error={auth.error} retry={auth.retry} />;
  if (!isSystemAdmin(auth.user?.roles)) return <Empty title="Không có quyền truy cập"><p>Khu vực này dành cho quản trị viên hệ thống.</p><Link to="/">Trang chủ</Link><button onClick={auth.logout}>Đăng xuất</button></Empty>;
  return <Outlet />;
}
