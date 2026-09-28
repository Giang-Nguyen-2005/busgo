import { useState } from "react";
import { Link, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../features/auth/AuthProvider";
import "../features/operator/operator.css";
export function AdminLayout() {
  const auth = useAuth();
  const [open, setOpen] = useState(false);
  return <div className="operator-shell"><aside className="operator-sidebar"><Link className="operator-brand" to="/admin">BusGo · Hệ thống</Link>
    <button className="operator-menu" aria-expanded={open} aria-controls="admin-navigation" onClick={() => setOpen(!open)}>Điều hướng</button>
    <nav id="admin-navigation" className={open ? "operator-nav is-open" : "operator-nav"} aria-label="Quản trị hệ thống"><NavLink end to="/admin" onClick={() => setOpen(false)}>Tổng quan</NavLink><NavLink to="/admin/operators" onClick={() => setOpen(false)}>Nhà xe</NavLink></nav></aside>
    <div className="operator-workspace"><header className="operator-topbar"><Link to="/">Trang chủ</Link><span>{auth.user?.fullName} · Quản trị hệ thống</span><button onClick={auth.logout}>Đăng xuất</button></header><main className="operator-content"><Outlet /></main></div></div>;
}
