import { safeReturn } from "../../utils/format";

export const isSystemAdmin = (roles: string[] = []) => roles.includes("SYSTEM_ADMIN");
export const canManageOperator = (roles: string[] = []) => !isSystemAdmin(roles) && roles.includes("OPERATOR_ADMIN");
export const canReadOperator = (roles: string[] = []) => !isSystemAdmin(roles) && (canManageOperator(roles) || roles.includes("OPERATOR_STAFF"));
export function canAccessOperatorPath(roles: string[], path: string) {
  if (!canReadOperator(roles)) return false;
  if (!/^\/operator(\/|$)/.test(path)) return false;
  if (canManageOperator(roles)) return true;
  return /^\/operator\/?$/.test(path) || /^\/operator\/(trips|bookings|bus-types|employees)\/?$/.test(path) ||
    /^\/operator\/(trips|bookings|bus-types)\/\d+\/?$/.test(path) || /^\/operator\/trips\/\d+\/(seats|passengers|occupancy)\/?$/.test(path);
}
export function loginDestination(roles: string[], requested: string | null) {
  const target = safeReturn(requested);
  const path = target.split(/[?#]/)[0];
  if (isSystemAdmin(roles)) return path === "/admin" || path.startsWith("/admin/") ? target : "/admin";
  if (canReadOperator(roles)) return canAccessOperatorPath(roles, path) ? target : "/operator";
  return /^\/(admin|operator)(\/|$)/.test(path) ? "/" : target;
}
export function operatorNavigation(roles: string[] = []) {
  if (!canReadOperator(roles)) return [];
  const items = [["", "Tổng quan"], ["/trips", "Chuyến xe"], ["/bookings", "Đặt vé"], ["/bus-types", "Loại xe"]];
  return canManageOperator(roles) ? [...items, ["/reports", "Báo cáo"], ["/buses", "Đội xe"], ["/routes", "Tuyến vận hành"], ["/employees", "Nhân sự vận hành"], ["/staff", "Tài khoản nhân viên"]] : [...items, ["/employees", "Nhân sự vận hành"]];
}
