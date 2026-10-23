import { Armchair, Building2, BusFront, CalendarDays, ChartNoAxesCombined, ClipboardList, Contact, FileCheck, Headset, Home, LayoutDashboard, Route, ShieldAlert, Star, Tags, Ticket, UserCog, UsersRound, Wallet, Wrench } from "lucide-react";

// Explicit vocabulary for every existing menu entry. Access remains in auth/access.ts.
export const operatorIcons = {
  "": LayoutDashboard, "/dispatch": ClipboardList, "/trips": CalendarDays, "/bookings": Ticket, "/support": Headset,
  "/customers": Contact, "/reports": ChartNoAxesCombined, "/finance": Wallet,
  "/reviews": Star, "/vouchers": Tags, "/buses": BusFront, "/maintenance": Wrench,
  "/employees": UsersRound, "/routes": Route, "/bus-types": Armchair, "/staff": UserCog,
};
export const adminNavigation = [
  { to: "/admin", label: "Tổng quan", icon: LayoutDashboard },
  { to: "/admin/operator-applications", label: "Đơn đăng ký nhà xe", icon: FileCheck },
  { to: "/admin/operators", label: "Nhà xe", icon: Building2 },
  { to: "/admin/finance", label: "Tài chính", icon: Wallet },
  { to: "/admin/disputes", label: "Tranh chấp", icon: ShieldAlert },
  { to: "/admin/audit-events", label: "Nhật ký kiểm toán", icon: ClipboardList },
];
export const customerIcons = { home: Home, application: FileCheck, bookings: Ticket, support: Headset };
