import { useQuery } from "@tanstack/react-query";
import { Link, Navigate, Outlet, useLocation } from "react-router-dom";
import { get } from "../../api/client";
import type { User } from "../../types/customer";
import { useAuth } from "./AuthProvider";
import { Empty, ErrorState, Loading } from "../../components/ui";
import { canAccessOperatorPath } from "./access";
export function OperatorGuard() {
  const auth = useAuth();
  const location = useLocation();
  const profile = useQuery({
    queryKey: ["me"],
    queryFn: ({ signal }) => get<User>("/users/me", undefined, signal),
    enabled: auth.authenticated,
    retry: false,
  });
  if (!auth.authenticated)
    return (
      <Navigate
        replace
        to={`/login?returnTo=${encodeURIComponent(location.pathname + location.search + location.hash)}`}
      />
    );
  if (auth.loading || profile.isPending) return <Loading />;
  if (profile.isError)
    return <ErrorState error={profile.error} retry={() => profile.refetch()} />;
  if (!canAccessOperatorPath(auth.user?.roles || [], location.pathname))
    return (
      <Empty title="403 — Không có quyền truy cập">
        <p>
          Tài khoản của bạn không có quyền truy cập trang này.
        </p>
        <Link to="/">Trang chủ</Link>
        <p>
          <button onClick={auth.logout}>Đăng xuất</button>
        </p>
      </Empty>
    );
  return <Outlet />;
}
