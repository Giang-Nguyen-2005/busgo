import { useEffect, useState } from "react";
import { NotificationBell } from "../features/notifications/Notifications";
import { BusFront, LogOut, UserRound, Menu } from "lucide-react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../features/auth/AuthProvider";
import { customerAccess } from "../features/customer/presentation";
import "../features/customer/customer.css";
import { Drawer } from "../components/foundations/Drawer";
import { IconButton } from "../components/foundations/IconButton";
import { PageContent } from "../components/foundations/PageContent";
import { customerIcons } from "../components/foundations/navigationIcons";
import "./shells.css";
import "../features/customer/customer-typography.css";
export function CustomerLayout() {
  const auth = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const customer = customerAccess(auth.user?.roles);
  const workspace = auth.user?.roles.includes("SYSTEM_ADMIN") ? "/admin" : "/operator";
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [location.pathname]);
  useEffect(() => { setOpen(false); }, [location.key]);
  useEffect(() => {
    const path = location.pathname;
    const titles: Record<string, string> = { "/": "Tìm chuyến xe liên tỉnh", "/search": "Kết quả tìm chuyến", "/login": "Đăng nhập", "/register": "Đăng ký", "/profile": "Thông tin tài khoản", "/my-bookings": "Vé của tôi", "/booking": "Thông tin đặt vé", "/payment": "Thanh toán mô phỏng", "/booking-success": "Vé điện tử", "/support": "Hỗ trợ đặt vé", "/notifications": "Thông báo", "/notification-preferences": "Cài đặt thông báo", "/operator-application": "Đăng ký nhà xe" };
    const title = titles[path] || (path.startsWith("/trips/") ? "Chi tiết chuyến & chọn chỗ" : path.startsWith("/operators/") ? "Hồ sơ nhà xe" : path.endsWith("/modify") ? "Thay đổi đặt vé" : path.startsWith("/my-bookings/") ? "Chi tiết đặt vé" : path.startsWith("/support/") ? "Yêu cầu hỗ trợ" : "Không tìm thấy trang");
    document.title = title + " | BusGo";
  }, [location.pathname]);
  const navigation = <>
    <NavLink to="/" end><customerIcons.home aria-hidden="true" />Trang chủ</NavLink>
    <NavLink to="/operator-application"><customerIcons.application aria-hidden="true" />Đăng ký nhà xe</NavLink>
    {customer && <NavLink to="/my-bookings"><customerIcons.bookings aria-hidden="true" />Vé của tôi</NavLink>}
    {customer && <NavLink to="/support"><customerIcons.support aria-hidden="true" />Hỗ trợ</NavLink>}
  </>;
  return (
    <div className="customer-shell">
      <a className="skip-link" href="#main">
        Đến nội dung chính
      </a>
      <header className="customer-header">
        <div className="customer-header-inner">
          <Link className="brand" to="/" aria-label="BusGo trang chủ">
            <span className="logo-mark">
              <BusFront size={23} />
            </span>
            BusGo<span className="brand-dot">.</span>
          </Link>
          <nav className="customer-navigation" aria-label="Điều hướng chính">{navigation}</nav>
          <div className="customer-account">
            {auth.authenticated ? (
              <>
                {customer && <NotificationBell />}
                <Link className="customer-profile" to={customer ? "/profile" : workspace}>
                  <UserRound size={18} />
                  <span>{customer ? "Hồ sơ" : "Khu vực quản lý"}</span>
                </Link>
                <button
                  className="icon-button"
                  aria-label="Đăng xuất"
                  title="Đăng xuất"
                  onClick={auth.logout}
                >
                  <LogOut size={18} />
                </button>
              </>
            ) : (
              <>
                <Link className="customer-login" to="/login">
                  Đăng nhập
                </Link>
                <Link className="button small" to="/register">
                  Đăng ký
                </Link>
              </>
            )}
          </div>
          <IconButton className="shell-menu-trigger" label="Mở điều hướng" aria-expanded={open} aria-controls="customer-navigation" onClick={() => setOpen(true)}><Menu /></IconButton>
        </div>
      </header>
      <Drawer id="customer-navigation" title="Điều hướng BusGo" open={open} onClose={() => setOpen(false)}>
        <nav className="customer-mobile-navigation" aria-label="Điều hướng chính · di động" onClick={event => { if ((event.target as Element).closest("a")) setOpen(false); }}>
          {navigation}
          {auth.authenticated ? <><Link to={customer ? "/profile" : workspace}><UserRound aria-hidden="true" />{customer ? "Hồ sơ" : "Khu vực quản lý"}</Link><button className="secondary" onClick={() => { setOpen(false); auth.logout(); }}>Đăng xuất</button></> : <><Link to="/login">Đăng nhập</Link><Link to="/register">Đăng ký</Link></>}
        </nav>
      </Drawer>
      <main id="main" className="container" tabIndex={-1}>
        <PageContent><Outlet /></PageContent>
      </main>
      <footer className="customer-footer">
        <div className="customer-footer-inner">
          <div>
            <Link className="brand" to="/">
              <BusFront size={24} />
              BusGo<span className="brand-dot">.</span>
            </Link>
            <p>Tìm chuyến xe liên tỉnh, chọn chỗ và theo dõi đặt vé trong cùng một tài khoản.</p>
          </div>
          <div>
            <strong>Đặt vé xe khách trực tuyến</strong>
            <p>BusGo là phiên bản giới thiệu với nhà xe và lịch chạy minh họa. Vé và thanh toán trên trang không dùng cho chuyến đi thực tế.</p>
            <small>Giờ hiển thị theo Việt Nam (UTC+7).</small>
          </div>
          <div>
            <strong>Thông tin cho chuyến đi</strong>
            <Link to="/">Tìm chuyến xe</Link>
            <a href="/#booking-guide">Hướng dẫn đặt vé</a>
            <a href="/#customer-faq">Câu hỏi thường gặp</a>
            {customer && <Link to="/support">Yêu cầu hỗ trợ</Link>}
            {customer ? (
              <>
                <Link to="/my-bookings">Vé của tôi</Link>
                <Link to="/profile">Tài khoản</Link>
              </>
            ) : (
              <Link to={auth.authenticated ? workspace : "/login"}>{auth.authenticated ? "Khu vực quản lý" : "Đăng nhập"}</Link>
            )}
          </div>
        </div>
      </footer>
    </div>
  );
}
