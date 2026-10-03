import { apiClient, get, post } from "./client";
import type { ApiResponse } from "../types/api";
import type { Employee, Crew, Capability, AttendanceRow, Pickup } from "../types/operations";
const base = "/operator";
export const operationsApi = {
  employees: () => get<Employee[]>(`${base}/employees`),
  employee: (id: number) => get<Employee>(`${base}/employees/${id}`),
  saveEmployee: async (body: Omit<Employee, "id">, id?: number) => id ? (await apiClient.patch<ApiResponse<Employee>>(`${base}/employees/${id}`, body)).data.data : post<Employee>(`${base}/employees`, body),
  crew: (id: number) => get<Crew>(`${base}/trips/${id}/crew`),
  replaceCrew: async (id: number, assignments: { employeeId: number; duty: Capability }[]) => (await apiClient.put<ApiResponse<Crew>>(`${base}/trips/${id}/crew`, { assignments })).data.data,
  attendance: (id: number) => get<AttendanceRow[]>(`${base}/trips/${id}/attendance`),
  pickups: (id: number) => get<Pickup[]>(`${base}/trips/${id}/pickups`),
  reservationNoShow: (id: number, item: number, stopId: number, reason?: string) => post(`${base}/trips/${id}/booking-items/${item}/no-show`, { stopId, reason }),
  transition: (id: number, ticket: number, command: "check-in" | "board" | "no-show", stopId: number, reason?: string) => post(`${base}/trips/${id}/tickets/${ticket}/${command}`, { stopId, reason }),
  close: (id: number, stopId: number) => post(`${base}/trips/${id}/stops/${stopId}/close-pickup`, { stopId }),
  history: (id: number) => get<{ id: number; action: string; actor_id: number; entity_type: string; entity_id: number; occurred_at: string; reason: string | null }[]>(`${base}/trips/${id}/history`),
};
