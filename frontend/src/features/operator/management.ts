import { statusLabels } from "../../utils/status";
import type { AdminOperatorFilters, CreateOperatorRequest } from "../../types/admin";
import type { CreateOperatorStaffRequest, StaffFilters, UpdateOperatorStaffRequest } from "../../types/operator";
export const activeStatuses = ["ACTIVE", "INACTIVE"] as const;
export const staffRoles = ["OPERATOR_ADMIN", "OPERATOR_STAFF"] as const;
export const managementLabel = (value: string) => ({ ...statusLabels.user, OPERATOR_ADMIN: "Quản trị viên nhà xe", OPERATOR_STAFF: "Nhân viên nhà xe" })[value] || "Chưa xác định";
export function adminOperatorFilters(params: URLSearchParams): AdminOperatorFilters {
  const size = [10, 20, 50, 100].includes(Number(params.get("size"))) ? Number(params.get("size")) : 20;
  const page = Number(params.get("page"));
  return { q: params.get("q")?.trim().slice(0, 100) || undefined,
    status: activeStatuses.find(s => s === params.get("status")), size,
    page: Number.isInteger(page) && page >= 0 && page <= Math.floor(2147483647 / size) ? page : 0 };
}
export const staffFilters = (params: URLSearchParams): StaffFilters => ({ ...adminOperatorFilters(params), role: staffRoles.find(r => r === params.get("role")) });
const text = (data: FormData, key: string) => String(data.get(key) || "").trim();
export function accountRequest(data: FormData, prefix = "") {
  return { fullName: text(data, prefix + "fullName"), email: text(data, prefix + "email"), phone: text(data, prefix + "phone"),
    password: String(data.get(prefix + "password") || ""), staffCode: text(data, prefix + "staffCode") };
}
export function contactRequest(data: FormData) {
  return { name: text(data, "name"), phone: text(data, "phone"), email: text(data, "email"), address: text(data, "address") };
}
export function createOperatorRequest(data: FormData): CreateOperatorRequest {
  return { ...contactRequest(data), code: text(data, "code"), status: data.get("status") === "INACTIVE" ? "INACTIVE" : "ACTIVE", initialAdmin: accountRequest(data, "admin.") };
}
export function createStaffRequest(data: FormData): CreateOperatorStaffRequest {
  return { ...accountRequest(data), role: data.get("role") === "OPERATOR_ADMIN" ? "OPERATOR_ADMIN" : "OPERATOR_STAFF" };
}
export function updateStaffRequest(data: FormData): UpdateOperatorStaffRequest {
  return { staffCode: text(data, "staffCode"), status: data.get("status") === "INACTIVE" ? "INACTIVE" : "ACTIVE", role: data.get("role") === "OPERATOR_ADMIN" ? "OPERATOR_ADMIN" : "OPERATOR_STAFF" };
}
