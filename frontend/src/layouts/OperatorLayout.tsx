import { NavLink, useLocation } from "react-router-dom";
import { NotificationBell } from "../features/notifications/Notifications";
import { useAuth } from "../features/auth/AuthProvider";
import { canManageOperator, operatorNavigation } from "../features/auth/access";
import { operatorIcons } from "../components/foundations/navigationIcons";
import { WorkspaceShell } from "./WorkspaceShell";
import "../features/operator/operator.css";

const groups = [
  ["Điều hành", ["", "/dispatch", "/trips", "/bookings", "/support"]],
  ["Kinh doanh", ["/customers", "/reports", "/finance", "/reviews", "/vouchers"]],
  ["Nguồn lực", ["/buses", "/maintenance", "/employees"]],
  ["Cấu hình", ["/routes", "/bus-types", "/staff"]],
] as const;

export function OperatorLayout() {
  const auth = useAuth();
  const { pathname } = useLocation();
  const items = operatorNavigation(auth.user?.roles);
  const current = items.find(([path]) => path ? pathname === `/operator${path}` || pathname.startsWith(`/operator${path}/`) : pathname === "/operator")?.[1] || "Quản lý nhà xe";
  return <WorkspaceShell kind="operator" title="Điều hành nhà xe" current={current}
    role={canManageOperator(auth.user?.roles) ? "Quản trị viên nhà xe" : "Nhân viên hỗ trợ"}
    notifications={canManageOperator(auth.user?.roles) && <NotificationBell operator />}
    navigation={groups.map(([group, paths]) => {
      const allowed = items.filter(([path]) => (paths as readonly string[]).includes(path));
      return allowed.length > 0 && <div className="ws-nav-section" key={group}><span className="ws-nav-group">{group}</span>{allowed.map(([path, label]) => {
        const Icon = operatorIcons[path as keyof typeof operatorIcons];
        return <NavLink key={path} end={!path} to={`/operator${path}`}><Icon size={18} aria-hidden="true" />{label}</NavLink>;
      })}</div>;
    })} />;
}
