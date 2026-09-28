import type { ActiveStatus, CreateOperatorStaffRequest } from "./operator";
export interface AdminOperatorListItem {
  id: number; code: string; name: string; phone: string | null; email: string | null;
  status: ActiveStatus; activeStaffCount: number; activeAdminCount: number; createdAt: string; updatedAt: string;
}
export interface AdminOperatorDetail extends Omit<AdminOperatorListItem, "activeStaffCount" | "activeAdminCount"> {
  address: string | null;
  staffCounts: { total: number; active: number; admins: number; staff: number };
  operationalCounts: { buses: number; routes: number; trips: number; bookings: number };
}
export interface OperatorContactRequest { name: string; phone: string; email: string; address: string }
export interface CreateOperatorRequest extends OperatorContactRequest {
  code: string; status: ActiveStatus; initialAdmin: Omit<CreateOperatorStaffRequest, "role">;
}
export interface AdminOperatorFilters { q?: string; status?: ActiveStatus; page: number; size: number }
