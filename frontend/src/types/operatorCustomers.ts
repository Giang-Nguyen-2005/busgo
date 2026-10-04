import type { PagedResponse } from './api';
export type CustomerType = 'ACCOUNT' | 'OFFLINE_CONTACT';
export type CustomerSort = 'LATEST' | 'NAME' | 'BOOKINGS' | 'MOCK_PAID';
export interface CustomerFilters { q?: string; customerType?: CustomerType; sort?: CustomerSort; page: number; size: number }
export interface CustomerAttendance { boarded: number; noShow: number; checkedIn: number; unrecorded: number }
export interface CustomerMoney { grossMockPaid: number; mockRefunds: number; netMockPaid: number }
export interface OperatorCustomer {
  customerKey: string; customerType: CustomerType; displayName: string; phone: string; email: string | null;
  latestBookingAt: string; latestJourney: string; totalBookings: number; confirmedBookings: number;
  cancelledBookings: number; webBookings: number; phoneBookings: number; boardedJourneys: number;
  attendance: CustomerAttendance; money: CustomerMoney;
}
export interface CustomerBooking {
  bookingId: number; bookingCode: string; source: string; contactName: string; contactPhone: string;
  contactEmail: string | null; routeName: string; pickup: string; dropoff: string; seats: string;
  bookingStatus: string; paymentMethod: string; paymentStatus: string; validTickets: number; voidTickets: number;
  attendance: CustomerAttendance; money: CustomerMoney; cancellationReason: string | null;
  cancelledAt: string | null; createdAt: string;
  payments: { id: number; method: string; status: string; amount: number; paidAt: string | null; createdAt: string }[];
  refunds: { id: number; amount: number; reason: string; refundedAt: string }[];
}
export interface CustomerDetail { summary: OperatorCustomer; bookings: PagedResponse<CustomerBooking> }
