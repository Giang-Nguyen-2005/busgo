export interface Named {
  id: number;
  name: string;
}
export interface Location extends Named {
  province: string;
  district: string | null;
}
export interface User {
  id: number;
  fullName: string;
  email: string;
  phone: string;
  roles: string[];
}
export interface Tokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}
export interface LoginResponse extends Tokens {
  user: Omit<User, "phone">;
}
export interface Pickup {
  tripStopId: number;
  locationId: number;
  name: string;
  departureTime: string;
}
export interface Dropoff {
  tripStopId: number;
  locationId: number;
  name: string;
  arrivalTime: string;
}
export interface Trip {
  tripId: number;
  operator: Named;
  route: Named;
  busType: Named;
  pickup: Pickup;
  dropoff: Dropoff;
  durationMinutes: number;
  price: number;
  availableSeats: number;
  status: string;
}
export interface TripDetail extends Trip {
  stops: {
    tripStopId: number;
    locationId: number;
    name: string;
    stopOrder: number;
    allowPickup: boolean;
    allowDropoff: boolean;
    arrivalTime: string | null;
    departureTime: string | null;
  }[];
}
export interface Seat {
  tripSeatId: number;
  seatCode: string;
  row: number;
  column: number;
  floor: number;
  seatType: string;
  available: boolean;
}
export interface SeatMapData {
  tripId: number;
  pickup: Pickup;
  dropoff: Dropoff;
  price: number;
  availableSeatCount: number;
  seats: Seat[];
}
export interface Hold {
  holdToken: string;
  tripId: number;
  pickup: Pickup;
  dropoff: Dropoff;
  tripSeatIds: number[];
  seats: Pick<Seat, "tripSeatId" | "seatCode">[];
  pricePerSeat: number;
  totalPrice: number;
  expiresAt: string;
  status: "ACTIVE" | "EXPIRED";
}
export type BookingStatus = "PENDING" | "CONFIRMED" | "CANCELLED" | "COMPLETED";
export interface Stop {
  tripStopId: number;
  locationId: number;
  name: string;
  time: string;
}
export interface Booking {
  bookingId: number;
  bookingCode: string;
  status: BookingStatus;
  tripId: number;
  operator: Named;
  route: Named;
  pickup: Stop;
  dropoff: Stop;
  contact: { name: string; phone: string; email: string };
  seats: {
    tripSeatId: number;
    seatCode: string;
    passengerName: string | null;
    unitPrice: number;
  }[];
  pricePerSeat: number;
  totalAmount: number;
  createdAt: string;
}
export interface BookingListItem {
  bookingId: number;
  bookingCode: string;
  status: BookingStatus;
  tripId: number;
  routeName: string;
  operatorName: string;
  pickup: Stop;
  dropoff: Stop;
  departureTime: string;
  seats: string[];
  totalAmount: number;
  createdAt: string;
}
export interface Payment {
  paymentId: number;
  bookingId: number;
  bookingCode: string;
  method: string;
  amount: number;
  paymentStatus: string;
  bookingStatus: BookingStatus;
  transactionReference: string;
  paidAt: string;
}
export interface TicketBundle {
  bookingId: number;
  bookingCode: string;
  status: BookingStatus;
  paymentStatus: string;
  paymentMethod: string;
  amount: number;
  tripId: number;
  operator: Named;
  route: Named;
  pickup: Stop;
  dropoff: Stop;
  departureTime: string;
  arrivalTime: string;
  tickets: {
    ticketId: number;
    ticketCode: string;
    passengerName: string;
    seatCode: string;
    qrData: string;
  }[];
}
