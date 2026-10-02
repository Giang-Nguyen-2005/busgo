import { useState } from "react";
import { BusFront, LayoutDashboard, CalendarDays, Ticket, Armchair, Route, Users } from "lucide-react";
import { Link, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../features/auth/AuthProvider";
import "../features/operator/operator.css";
import { canManageOperator, operatorNavigation } from "../features/auth/access";
const navigationIcons = { "": LayoutDashboard, "/trips": CalendarDays, "/bookings": Ticket, "/bus-types": Armchair, "/buses": BusFront, "/routes": Route, "/staff": Users };
function NavigationIcon({ path }: { path: string }) {
  const Icon = navigationIcons[path as keyof typeof navigationIcons] || BusFront;
  return <Icon size={18} aria-hidden="true" />;
}
export function OperatorLayout() {
  const auth = useAuth();
  const [open, setOpen] = useState(false);
  return (
    <div className="operator-shell">
      <a className="skip-link" href="#operator-main">Đến nội dung chính</a>
      <aside className="operator-sidebar">
        <Link className="operator-brand" to="/operator">
          <BusFront aria-hidden="true" /> BusGo<span className="brand-dot">.</span>
        </Link>
        <button
          className="operator-menu"
          aria-expanded={open}
          aria-controls="operator-navigation"
          onClick={() => setOpen(!open)}
        >
          Điều hướng
        </button>
        <nav
          id="operator-navigation"
          className={open ? "operator-nav is-open" : "operator-nav"}
          aria-label="Quản lý nhà xe"
        >
          <span className="operator-nav-group">Điều hành</span>
          {operatorNavigation(auth.user?.roles).slice(0, 3).map(([path, label]) => (
            <NavLink
              key={path}
              end={!path}
              to={`/operator${path}`}
              onClick={() => setOpen(false)}
            >
              <NavigationIcon path={path} />{label}
            </NavLink>
          ))}
          {canManageOperator(auth.user?.roles) && <span className="operator-nav-group">Quản lý</span>}
          {operatorNavigation(auth.user?.roles).slice(3).map(([path, label]) => <NavLink key={path} to={`/operator${path}`} onClick={() => setOpen(false)}><NavigationIcon path={path} />{label}</NavLink>)}
        </nav>
        <div className="workspace-identity"><strong>ĐIỀU HÀNH NHÀ XE</strong><span>{auth.user?.fullName}</span><span>{canManageOperator(auth.user?.roles) ? "Quản trị viên nhà xe" : "Nhân viên · Chỉ xem"}</span></div>
      </aside>
      <div className="operator-workspace">
        <header className="operator-topbar">
          <Link to="/">Trang khách hàng</Link>
          <details>
            <summary>{auth.user?.fullName} · Hồ sơ</summary>
            <div className="operator-account-panel">
            <p>{auth.user?.email}</p>
            <p>{auth.user?.phone || "Chưa có số điện thoại"}</p>
            <p>{canManageOperator(auth.user?.roles) ? "Quản trị viên nhà xe" : "Nhân viên · Chỉ xem"}</p>
            <button onClick={auth.logout}>Đăng xuất</button>
            </div>
          </details>
        </header>
        <main id="operator-main" className="operator-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
