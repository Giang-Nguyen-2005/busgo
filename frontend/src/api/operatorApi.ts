import { apiClient, get, post } from "./client";
import type { ApiResponse, PagedResponse } from "../types/api";
import type * as O from "../types/operator";
const base = "/operator";
export interface PageParams {
  page?: number;
  size?: number;
}
export interface BusFilters extends PageParams {
  q?: string;
  status?: O.BusStatus;
  busTypeId?: number;
}
export interface TripFilters extends PageParams {
  date?: string;
  routeId?: number;
  busId?: number;
  status?: O.TripStatus;
}
const paged = async <T>(path: string, params: object, signal?: AbortSignal) =>
  (await apiClient.get<PagedResponse<T>>(base + path, { params, signal })).data;
const patch = async <T>(path: string, body: object) =>
  (await apiClient.patch<ApiResponse<T>>(base + path, body)).data.data;
export const operatorApi = {
  staff: (params: O.StaffFilters, signal?: AbortSignal) => paged<O.OperatorStaff>("/staff", params, signal),
  createStaff: (body: O.CreateOperatorStaffRequest) => post<O.OperatorStaff>(`${base}/staff`, body),
  updateStaff: (id: number, body: O.UpdateOperatorStaffRequest) => patch<O.OperatorStaff>(`/staff/${id}`, body),
  bookings: (params: O.BookingFilters, signal?: AbortSignal) =>
    paged<O.OperatorBookingListItem>("/bookings", params, signal),
  booking: (id: number, signal?: AbortSignal) =>
    get<O.OperatorBookingDetail>(`${base}/bookings/${id}`, undefined, signal),
  passengers: (id: number, signal?: AbortSignal) =>
    get<O.PassengerManifest>(`${base}/trips/${id}/passengers`, undefined, signal),
  occupancy: (id: number, signal?: AbortSignal) =>
    get<O.TripOccupancy>(`${base}/trips/${id}/occupancy`, undefined, signal),
  updateTripStatus: (id: number, status: O.TripStatus) =>
    patch<{ tripId: number; status: O.TripStatus }>(`/trips/${id}/status`, { status }),
  trips: (params: TripFilters, signal?: AbortSignal) =>
    paged<O.TripSummaryResponse>("/trips", params, signal),
  trip: (id: number, signal?: AbortSignal) =>
    get<O.TripDetailResponse>(`${base}/trips/${id}`, undefined, signal),
  createTrip: (body: O.CreateTripRequest) =>
    post<O.TripSummaryResponse>(`${base}/trips`, body),
  buses: (params: BusFilters, signal?: AbortSignal) =>
    paged<O.BusResponse>("/buses", params, signal),
  bus: (id: number, signal?: AbortSignal) =>
    get<O.BusResponse>(`${base}/buses/${id}`, undefined, signal),
  createBus: (body: O.CreateBusRequest) =>
    post<O.BusResponse>(`${base}/buses`, body),
  updateBus: (id: number, body: O.UpdateBusRequest) =>
    patch<O.BusResponse>(`/buses/${id}`, body),
  busTypes: (signal?: AbortSignal) =>
    get<O.BusTypeResponse[]>(`${base}/bus-types`, undefined, signal),
  busType: (id: number, signal?: AbortSignal) =>
    get<O.BusTypeResponse>(`${base}/bus-types/${id}`, undefined, signal),
  routes: (params: PageParams, signal?: AbortSignal) =>
    paged<O.OperatorRouteResponse>("/routes", params, signal),
  route: (id: number, signal?: AbortSignal) =>
    get<O.OperatorRouteResponse>(`${base}/routes/${id}`, undefined, signal),
  catalog: (params: PageParams, signal?: AbortSignal) =>
    paged<O.RouteDefinitionResponse>("/route-catalog", params, signal),
  attachRoute: (body: O.AttachRouteRequest) =>
    post<O.OperatorRouteResponse>(`${base}/routes`, body),
  updateRoute: (id: number, body: O.UpdateOperatorRouteRequest) =>
    patch<O.OperatorRouteResponse>(`/routes/${id}`, body),
  fares: (id: number, signal?: AbortSignal) =>
    get<O.FareResponse[]>(`${base}/routes/${id}/fares`, undefined, signal),
  replaceFares: async (id: number, body: O.ReplaceFaresRequest) =>
    (
      await apiClient.put<ApiResponse<O.FareResponse[]>>(
        `${base}/routes/${id}/fares`,
        body,
      )
    ).data.data,
};
// Choice lists must include every page, not silently truncate at the first 20/100 rows.
export async function allPages<T>(
  fetchPage: (params: PageParams) => Promise<PagedResponse<T>>,
) {
  const first = await fetchPage({ page: 0, size: 100 });
  const rows = [...first.data];
  for (let page = 1; page < first.pagination.totalPages; page++)
    rows.push(...(await fetchPage({ page, size: 100 })).data);
  return rows;
}
