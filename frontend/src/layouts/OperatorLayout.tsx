import { useState } from "react";
import { Link, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../features/auth/AuthProvider";
import "../features/operator/operator.css";
export function OperatorLayout() {
  const auth = useAuth();
  const [open, setOpen] = useState(false);
  return (
    <div className="operator-shell">
      <aside className="operator-sidebar">
        <Link className="operator-brand" to="/operator">
          BusGo · Nhà xe
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
            ["", "Tổng quan"],
            ["/trips", "Chuyến xe"],
            ["/buses", "Đội xe"],
            ["/bus-types", "Loại xe"],
            ["/routes", "Tuyến vận hành"],
          ].map(([path, label]) => (
            <NavLink
              key={path}
              end={!path}
              to={`/operator${path}`}
              onClick={() => setOpen(false)}
            >
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="operator-workspace">
        <header className="operator-topbar">
          <Link to="/">Trang khách hàng</Link>
          <details>
            <summary>{auth.user?.fullName} · Hồ sơ</summary>
            <p>{auth.user?.email}</p>
            <p>{auth.user?.phone || "Chưa có số điện thoại"}</p>
            <p>OPERATOR_ADMIN</p>
            <button onClick={auth.logout}>Đăng xuất</button>
          </details>
        </header>
        <main className="operator-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
