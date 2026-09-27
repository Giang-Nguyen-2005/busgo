import { useQuery } from "@tanstack/react-query";
import { Link, Navigate, Outlet, useLocation } from "react-router-dom";
import { get } from "../../api/client";
import type { User } from "../../types/customer";
import { useAuth } from "./AuthProvider";
import { Empty, ErrorState, Loading } from "../../components/ui";
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
  if (!auth.user?.roles.includes("OPERATOR_ADMIN"))
    return (
      <Empty title="403 — Không có quyền truy cập">
        <p>
          Khu vực nhà xe yêu cầu quyền OPERATOR_ADMIN. OPERATOR_STAFF không có
          quyền truy cập.
        </p>
        <Link to="/">Trang chủ</Link>
        <p>
          <button onClick={auth.logout}>Đăng xuất</button>
        </p>
      </Empty>
    );
  return <Outlet />;
}
