import { NavLink, useLocation } from "react-router-dom";
import { adminNavigation } from "../components/foundations/navigationIcons";
import { WorkspaceShell } from "./WorkspaceShell";
import "../features/admin/admin.css";

export function AdminLayout() {
  const { pathname } = useLocation();
  const current = adminNavigation.find(({ to }) => pathname === to || (to !== "/admin" && pathname.startsWith(`${to}/`)))?.label || "Quản trị hệ thống";
  return <WorkspaceShell kind="admin" title="Quản trị hệ thống" role="Quản trị viên BusGo" current={current}
    navigation={<div className="ws-nav-section"><span className="ws-nav-group">Nền tảng BusGo</span>{adminNavigation.map(({ to, label, icon: Icon }) =>
      <NavLink key={to} end={to === "/admin"} to={to}><Icon size={18} aria-hidden="true" />{label}</NavLink>)}</div>} />;
}
