import { useState } from "react";
import { BusFront, LayoutDashboard, CalendarDays, Ticket, Armchair, Route, Users, Wrench, ChartNoAxesCombined, Contact } from "lucide-react";
import { Link, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../features/auth/AuthProvider";
import "../features/operator/operator.css";
import { canManageOperator, operatorNavigation } from "../features/auth/access";
const navigationIcons = { "": LayoutDashboard, "/trips": CalendarDays, "/bookings": Ticket, "/customers": Users, "/bus-types": Armchair, "/buses": BusFront, "/routes": Route, "/staff": Users, "/employees": Contact, "/maintenance": Wrench, "/reports": ChartNoAxesCombined };
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
          {[
            ["Điều hành", ["", "/trips", "/bookings"]],
            ["Kinh doanh", ["/customers", "/reports"]],
            ["Nguồn lực", ["/buses", "/maintenance", "/employees"]],
            ["Cấu hình", ["/routes", "/bus-types", "/staff"]],
          ].map(([group, paths]) => {
            const items = operatorNavigation(auth.user?.roles).filter(([path]) => paths.includes(path));
            return items.length > 0 && <div className="operator-nav-section" key={String(group)}><span className="operator-nav-group">{group}</span>{items.map(([path, label]) => <NavLink key={path} end={!path} to={`/operator${path}`} onClick={() => setOpen(false)}><NavigationIcon path={path} />{label}</NavLink>)}</div>;
          })}
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
