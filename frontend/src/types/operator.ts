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

// M12: OperatorBookingDtos and OperatorTripOperationsDtos.
export type BookingStatus = "PENDING" | "CONFIRMED" | "CANCELLED" | "COMPLETED";
export type PaymentStatus = "PENDING" | "PAID" | "FAILED" | "REFUNDED";
export type InventoryStatus = "AVAILABLE" | "HELD" | "BOOKED" | "BLOCKED";
export interface BookingContact { name: string; phone: string; email: string | null }
export interface BookingStop { tripStopId: number; locationId: number; name: string; time: string | null }
export interface OperatorBookingListItem {
  bookingId: number; bookingCode: string; status: BookingStatus; paymentStatus: PaymentStatus;
  tripId: number; route: { id: number; name: string }; contact: BookingContact;
  pickup: BookingStop; dropoff: BookingStop; seatCount: number; totalAmount: number; createdAt: string;
}
export interface OperatorTicket {
  id: number; ticketCode: string; passengerName: string | null; seatCode: string;
  paymentId: number; createdAt: string;
}
export interface OperatorBookingItem {
  bookingItemId: number; tripSeatId: number; seatCode: string; passengerName: string | null;
  unitPrice: number; ticket: OperatorTicket | null;
}
export interface OperatorPayment {
  id: number; method: "MOCK_QR"; amount: number; status: PaymentStatus;
  transactionReference: string | null; paidAt: string | null; createdAt: string;
}
export interface OperatorBookingDetail extends Omit<OperatorBookingListItem, "paymentStatus" | "tripId" | "seatCount"> {
  trip: { id: number; status: TripStatus; departureTime: string; estimatedArrivalTime: string };
  customer: { id: number; fullName: string; email: string; phone: string | null };
  items: OperatorBookingItem[]; payments: OperatorPayment[]; updatedAt: string;
}
export interface PassengerManifestRow {
  bookingId: number; bookingCode: string; bookingStatus: BookingStatus; bookingItemId: number;
  tripSeatId: number; seatCode: string; pickup: BookingStop; dropoff: BookingStop;
  passengerName: string | null; ticketPassengerName: string | null; contact: BookingContact;
  paymentStatus: PaymentStatus; ticketCode: string | null;
}
export interface PassengerManifest { tripId: number; tripStatus: TripStatus; passengers: PassengerManifestRow[] }
export interface OccupancySegment {
  tripSegmentId: number; segmentOrder: number; fromTripStopId: number; toTripStopId: number;
  fromName: string; toName: string;
  counts: { available: number; held: number; booked: number; blocked: number };
}
export interface SeatSegmentState {
  tripSegmentId: number; segmentOrder: number; status: InventoryStatus; holdExpiresAt: string | null;
  bookingId: number | null; bookingCode: string | null; bookingStatus: BookingStatus | null;
}
export interface OccupancySeat {
  tripSeatId: number; seatCode: string; row: number; column: number; floor: number;
  seatType: SeatType; segments: SeatSegmentState[];
}
export interface TripOccupancy {
  tripId: number; tripStatus: TripStatus; seatCount: number; segmentCount: number;
  wholeTripAvailableSeatCount: number; segments: OccupancySegment[]; seats: OccupancySeat[];
}
export interface BookingFilters {
  q?: string; tripId?: number; status?: BookingStatus; paymentStatus?: PaymentStatus;
  date?: string; page: number; size: number;
}

// M13 OperatorStaffDtos: membership and login status are distinct.
export type OperatorRole = "OPERATOR_ADMIN" | "OPERATOR_STAFF";
export interface OperatorStaff {
  staffId: number; staffCode: string | null; membershipStatus: ActiveStatus; role: OperatorRole;
  user: { id: number; fullName: string; email: string; phone: string | null; status: "ACTIVE" | "INACTIVE" | "LOCKED" };
  createdAt: string;
}
export interface CreateOperatorStaffRequest { fullName: string; email: string; phone: string; password: string; staffCode: string; role: OperatorRole }
export interface UpdateOperatorStaffRequest { staffCode: string; status: ActiveStatus; role: OperatorRole }
export interface StaffFilters { q?: string; status?: ActiveStatus; role?: OperatorRole; page: number; size: number }
