import { useEffect, useRef, useState, type ReactNode } from "react";
import { BusFront, ChevronDown, Menu, UserRound } from "lucide-react";
import { Link, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../features/auth/AuthProvider";
import { Drawer } from "../components/foundations/Drawer";
import { IconButton } from "../components/foundations/IconButton";
import { PageContent } from "../components/foundations/PageContent";
import "./shells.css";

export function WorkspaceShell({ kind, title, role, current, navigation, notifications }: {
  kind: "operator" | "admin"; title: string; role: string; current: string;
  navigation: ReactNode; notifications?: ReactNode;
}) {
  const auth = useAuth();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const account = useRef<HTMLDetailsElement>(null);
  useEffect(() => { setOpen(false); if (account.current) account.current.open = false; }, [location.key]);
  useEffect(() => {
    const dismiss = (event: PointerEvent) => {
      if (account.current && !account.current.contains(event.target as Node)) account.current.open = false;
    };
    document.addEventListener("pointerdown", dismiss);
    return () => document.removeEventListener("pointerdown", dismiss);
  }, []);
  const identity = <div className="ws-identity"><strong>{title}</strong><span>{auth.user?.fullName}</span><span>{role}</span></div>;
  return <div className={`${kind}-shell workspace-shell`}>
    <a className="skip-link" href={`#${kind}-main`}>Đến nội dung chính</a>
    <aside className="ws-sidebar">
      <Link className="ws-brand" to={`/${kind}`}><BusFront aria-hidden="true" />BusGo<span>.</span></Link>
      <p className="ws-context">{title}</p>
      <nav className="ws-nav" aria-label={title}>{navigation}</nav>
      {identity}
    </aside>
    <div className="ws-workspace">
      <header className="ws-topbar">
        <IconButton className="shell-menu-trigger" label="Mở điều hướng" aria-expanded={open} aria-controls={`${kind}-navigation`} onClick={() => setOpen(true)}><Menu /></IconButton>
        <div className="ws-location"><span>{title}</span><strong>{current}</strong></div>
        <div className="ws-topbar-actions">
          <Link className="ws-customer-link" to="/">Trang khách hàng</Link>
          {notifications}
          <details ref={account} className="ws-account" onKeyDown={event => {
            if (event.key === "Escape" && account.current?.open) { account.current.open = false; account.current.querySelector("summary")?.focus(); }
          }}>
            <summary aria-label={`${auth.user?.fullName} · Tài khoản`}><UserRound aria-hidden="true" /><span>{auth.user?.fullName} · {kind === "admin" ? "Tài khoản" : "Hồ sơ"}</span><ChevronDown size={16} aria-hidden="true" /></summary>
            <div className="ws-account-panel"><strong>{auth.user?.fullName}</strong><p>{auth.user?.email}</p>
              {kind === "operator" && <p>{auth.user?.phone || "Chưa có số điện thoại"}</p>}
              <p>{role}</p><Link to="/">Trang khách hàng</Link><button className="secondary" onClick={auth.logout}>Đăng xuất</button>
            </div>
          </details>
        </div>
      </header>
      <main id={`${kind}-main`} className={`ws-content ${kind}-content`} tabIndex={-1}><PageContent><Outlet /></PageContent></main>
    </div>
    <Drawer id={`${kind}-navigation`} title={title} open={open} onClose={() => setOpen(false)}>
      <nav className="ws-nav" aria-label={`${title} · di động`} onClick={event => {
        if ((event.target as Element).closest("a")) setOpen(false);
      }}>{navigation}</nav>{identity}
    </Drawer>
  </div>;
}
