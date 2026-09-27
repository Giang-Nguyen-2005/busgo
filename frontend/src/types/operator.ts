// Mirrors FleetDtos, RouteDtos and TripDtos. Times are ISO OffsetDateTime strings.
export type ActiveStatus = "ACTIVE" | "INACTIVE";
export type BusStatus = "AVAILABLE" | "MAINTENANCE" | "INACTIVE";
export type TripStatus =
  "SCHEDULED" | "BOARDING" | "DEPARTED" | "COMPLETED" | "CANCELLED";
export type SeatType = "STANDARD";
export interface SeatTemplateResponse {
  id: number;
  seatCode: string;
  row: number;
  column: number;
  floor: number;
  seatType: SeatType;
  active: boolean;
}
export interface BusTypeResponse {
  id: number;
  name: string;
  seatCount: number;
  description: string | null;
  status: ActiveStatus;
  seats: SeatTemplateResponse[];
}
export interface BusTypeSummary {
  id: number;
  name: string;
  seatCount: number;
}
export interface BusResponse {
  id: number;
  licensePlate: string;
  status: BusStatus;
  busType: BusTypeSummary;
}
export interface CreateBusRequest {
  licensePlate: string;
  busTypeId: number;
}
export interface UpdateBusRequest {
  licensePlate?: string;
  busTypeId?: number;
  status?: BusStatus;
}
export interface RouteLocationResponse {
  id: number;
  name: string;
  province: string | null;
  district: string | null;
}
export interface RouteStopResponse {
  id: number;
  stopOrder: number;
  location: RouteLocationResponse;
  allowPickup: boolean;
  allowDropoff: boolean;
  estimatedOffsetMinutes: number;
  status: ActiveStatus;
}
export interface RouteDefinitionResponse {
  id: number;
  name: string;
  origin: RouteLocationResponse;
  destination: RouteLocationResponse;
  estimatedDistanceKm: number | null;
  estimatedDurationMinutes: number | null;
  status: ActiveStatus;
  stops: RouteStopResponse[];
}
export interface OperatorRouteResponse {
  id: number;
  status: ActiveStatus;
  route: RouteDefinitionResponse;
}
export interface AttachRouteRequest {
  routeId: number;
}
export interface UpdateOperatorRouteRequest {
  status: ActiveStatus;
}
export interface FareInput {
  fromRouteStopId: number;
  toRouteStopId: number;
  price: number;
}
export interface ReplaceFaresRequest {
  fares: FareInput[];
}
export interface FareResponse extends FareInput {
  id: number;
  status: ActiveStatus;
}
export interface CreateTripRequest {
  operatorRouteId: number;
  busId: number;
  departureTime: string;
}
export interface TripRouteSummary {
  operatorRouteId: number;
  routeId: number;
  name: string;
}
export interface TripBusSummary {
  id: number;
  licensePlate: string;
  busTypeId: number;
  busTypeName: string;
}
export interface TripSummaryResponse {
  id: number;
  status: TripStatus;
  route: TripRouteSummary;
  bus: TripBusSummary;
  departureTime: string;
  estimatedArrivalTime: string;
  seatCount: number;
  segmentCount: number;
}
export interface TripStopResponse {
  id: number;
  sourceRouteStopId: number | null;
  locationId: number;
  locationName: string;
  stopOrder: number;
  plannedArrivalTime: string | null;
  plannedDepartureTime: string | null;
  allowPickup: boolean;
  allowDropoff: boolean;
  status: ActiveStatus;
}
export interface TripSegmentResponse {
  id: number;
  segmentOrder: number;
  fromTripStopId: number;
  toTripStopId: number;
}
export interface TripSeatResponse {
  id: number;
  sourceSeatTemplateId: number | null;
  seatCode: string;
  row: number;
  column: number;
  floor: number;
  seatType: SeatType;
}
export interface TripDetailResponse extends Omit<
  TripSummaryResponse,
  "seatCount" | "segmentCount"
> {
  stops: TripStopResponse[];
  segments: TripSegmentResponse[];
  seats: TripSeatResponse[];
}
