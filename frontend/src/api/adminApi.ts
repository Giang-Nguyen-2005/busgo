import { apiClient, get, post } from "./client";
import type { ApiResponse, PagedResponse } from "../types/api";
import type { AdminOperatorDetail, AdminOperatorListItem, AdminOperatorFilters, CreateOperatorRequest, OperatorContactRequest } from "../types/admin";
import type { ActiveStatus, OperatorStaff, StaffFilters } from "../types/operator";
const base = "/admin/operators";
export const adminApi = {
  operators: async (params: AdminOperatorFilters, signal?: AbortSignal) => (await apiClient.get<PagedResponse<AdminOperatorListItem>>(base, { params, signal })).data,
  operator: (id: number, signal?: AbortSignal) => get<AdminOperatorDetail>(`${base}/${id}`, undefined, signal),
  createOperator: (body: CreateOperatorRequest) => post<AdminOperatorDetail>(base, body),
  updateOperator: async (id: number, body: OperatorContactRequest) => (await apiClient.patch<ApiResponse<AdminOperatorDetail>>(`${base}/${id}`, body)).data.data,
  updateStatus: async (id: number, status: ActiveStatus) => (await apiClient.patch<ApiResponse<AdminOperatorDetail>>(`${base}/${id}/status`, { status })).data.data,
  staff: async (id: number, params: StaffFilters, signal?: AbortSignal) => (await apiClient.get<PagedResponse<OperatorStaff>>(`${base}/${id}/staff`, { params, signal })).data,
};
