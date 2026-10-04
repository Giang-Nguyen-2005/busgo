import { apiClient } from './client';
import type { ApiResponse, PagedResponse } from '../types/api';
import type { AssignedTrip, FleetWarnings, Maintenance, MaintenanceStatus, MaintenanceType, StatusHistory } from '../types/fleet';
export interface MaintenanceFilters { busId?:number; status?:MaintenanceStatus; maintenanceType?:MaintenanceType; fromDate?:string; toDate?:string; page:number; size:number }
export const fleetApi = {
  async maintenance(filters:MaintenanceFilters, signal?:AbortSignal) { return (await apiClient.get<ApiResponse<PagedResponse<Maintenance>>>('/operator/maintenance',{params:filters,signal})).data.data; },
  async schedule(bus:number, input:{maintenanceType:MaintenanceType; title:string; note?:string; scheduledStart:string; scheduledEnd:string}) { return (await apiClient.post<ApiResponse<Maintenance>>(`/operator/buses/${bus}/maintenance`,input)).data.data; },
  async start(id:number) { return (await apiClient.post<ApiResponse<Maintenance>>(`/operator/maintenance/${id}/start`)).data.data; },
  async complete(id:number, input:{odometerKm?:number; nextDueDate?:string; nextDueOdometerKm?:number; note?:string}) { return (await apiClient.post<ApiResponse<Maintenance>>(`/operator/maintenance/${id}/complete`,input)).data.data; },
  async cancel(id:number, reason?:string) { return (await apiClient.post<ApiResponse<Maintenance>>(`/operator/maintenance/${id}/cancel`,{reason})).data.data; },
  async trips(bus:number,page:number,signal?:AbortSignal) { return (await apiClient.get<ApiResponse<PagedResponse<AssignedTrip>>>(`/operator/buses/${bus}/trips`,{params:{page,size:20},signal})).data.data; },
  async history(bus:number,signal?:AbortSignal) { return (await apiClient.get<ApiResponse<StatusHistory[]>>(`/operator/buses/${bus}/history`,{signal})).data.data; },
  async warnings(signal?:AbortSignal) { return (await apiClient.get<ApiResponse<FleetWarnings>>('/operator/fleet/readiness',{signal})).data.data; },
};

