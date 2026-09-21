import { useEffect } from "react";
import { BusFront, LogOut, UserRound } from "lucide-react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../features/auth/AuthProvider";
export function CustomerLayout() {
  const auth = useAuth();
  const location = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [location.pathname]);
  return (
    <>
      <a className="skip-link" href="#main">
        Đến nội dung chính
      </a>
      <header className="site-header">
        <div className="header-inner">
          <Link className="brand" to="/" aria-label="BusGo trang chủ">
            <span className="logo-mark">
              <BusFront size={23} />
            </span>
            BusGo<span className="brand-dot">.</span>
          </Link>
          <nav className="main-nav" aria-label="Điều hướng chính">
            <NavLink to="/" end>
              Trang chủ
            </NavLink>
            <NavLink to="/my-bookings">Vé của tôi</NavLink>
          </nav>
          <div className="account-nav">
            {auth.authenticated ? (
              <>
                <Link className="profile-link" to="/profile">
                  <UserRound size={18} />
                  <span>{auth.user?.fullName || "Tài khoản"}</span>
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
                <Link className="login-link" to="/login">
                  Đăng nhập
                </Link>
                <Link className="button small" to="/register">
                  Đăng ký
                </Link>
              </>
            )}
          </div>
        </div>
      </header>
      <main id="main" className="container">
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="footer-inner">
          <div>
            <Link className="brand" to="/">
              <BusFront size={24} />
              BusGo<span className="brand-dot">.</span>
            </Link>
            <p>Mỗi chuyến đi, một khởi đầu.</p>
          </div>
          <div>
            <strong>Đặt vé xe khách trực tuyến</strong>
            <p>Dự án học tập · Thanh toán giả lập</p>
            <small>Giờ hiển thị theo Việt Nam (UTC+7).</small>
          </div>
          <div>
            <Link to="/">Tìm chuyến xe</Link>
            <Link to="/my-bookings">Vé của tôi</Link>
            <Link to="/profile">Tài khoản</Link>
          </div>
        </div>
      </footer>
    </>
  );
}
