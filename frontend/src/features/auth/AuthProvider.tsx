import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { get } from "../../api/client";
import { getTokens, setTokens } from "../../api/session";
import type { Tokens, User } from "../../types/customer";
import { ErrorState, Loading } from "../../components/ui";

export const AuthContext = createContext<{
  user?: User;
  authenticated: boolean;
  loading: boolean;
  error?: unknown;
  retry?: () => void;
  login: (tokens: Tokens) => void;
  logout: () => void;
}>({ authenticated: false, loading: false, login: () => {}, logout: () => {} });
export function AuthProvider({ children }: { children: ReactNode }) {
  const [authenticated, setAuthenticated] = useState(!!getTokens());
  const cache = useQueryClient();
  useEffect(() => {
    const sync = () => {
      const next = !!getTokens();
      setAuthenticated(next);
      if (!next) cache.clear();
    };
    window.addEventListener("busgo:auth", sync);
    return () => window.removeEventListener("busgo:auth", sync);
  }, [cache]);
  const profile = useQuery({
    queryKey: ["me"],
    queryFn: ({ signal }) => get<User>("/users/me", undefined, signal),
    enabled: authenticated,
    retry: false,
  });
  return (
    <AuthContext.Provider
      value={{
        user: authenticated ? profile.data : undefined,
        authenticated,
        error: profile.error,
        retry: () => { void profile.refetch(); },
        loading: authenticated && profile.isPending,
        login: (tokens) => {
          setTokens(null);
          cache.clear();
          setTokens(tokens);
          setAuthenticated(true);
        },
        logout: () => {
          setTokens(null);
          cache.clear();
        },
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
export const useAuth = () => useContext(AuthContext);
export function CustomerGuard() {
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
        to={`/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`}
        replace
      />
    );
  if (auth.loading) return <Loading />;
  if (profile.isError)
    return <ErrorState error={profile.error} retry={() => profile.refetch()} />;
  if (!auth.user?.roles.includes("CUSTOMER"))
    return (
      <div className="empty card">
        <h1>Cần tài khoản khách hàng</h1>
        <p>Vui lòng sử dụng tài khoản khách hàng để đặt vé.</p>
        <button onClick={auth.logout}>Đăng nhập tài khoản khác</button>
      </div>
    );
  return <Outlet />;
}
