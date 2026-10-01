import { useState } from "react";
import { Link, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../features/auth/AuthProvider";
import "../features/admin/admin.css";
export function AdminLayout() {
 const auth = useAuth(); const [open, setOpen] = useState(false);
 return <div className="admin-shell"><a className="skip-link" href="#admin-main">Đến nội dung chính</a><aside className="admin-sidebar"><Link className="admin-brand" to="/admin">BusGo · Hệ thống</Link><button className="admin-menu" aria-expanded={open} aria-controls="admin-navigation" onClick={() => setOpen(!open)}>Điều hướng</button><nav id="admin-navigation" className={'admin-nav' + (open ? ' is-open' : '')} aria-label="Quản trị hệ thống"><NavLink end to="/admin" onClick={() => setOpen(false)}>Tổng quan</NavLink><NavLink to="/admin/operators" onClick={() => setOpen(false)}>Nhà xe</NavLink></nav></aside><div className="admin-workspace"><header className="admin-topbar"><Link to="/">Trang khách hàng</Link><details><summary>{auth.user?.fullName} · Tài khoản</summary><div className="admin-profile"><p>{auth.user?.email}</p><p>Quản trị hệ thống</p><button className="secondary" onClick={auth.logout}>Đăng xuất</button></div></details></header><main id="admin-main" className="admin-content"><Outlet /></main></div></div>;
}
