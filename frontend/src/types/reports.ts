export interface ReportFilters { fromDate: string; toDate: string; routeId?: number; tripId?: number; bookingSource?: "WEB" | "PHONE"; paymentMethod?: "MOCK_ONLINE" | "QR_TRANSFER" | "PAY_ON_BOARD"; page?: number; size?: number }
export interface ReportMetadata extends ReportFilters { timezone: string; asOf: string; dateBasis: string }
export interface ReportMoney { grossMockCollections: number; mockRefunds: number; netMockCollections: number; paidPaymentCount: number; refundedPaymentCount: number }
export interface ReportAttendance { eligibleResolvedTickets: number; boardedTickets: number; noShowTickets: number; checkedInNotBoarded: number; unresolvedAttendance: number; ticketlessNoShows: number; boardingRate: number | null; noShowRate: number | null }
export interface ReportLoad { expectedCells: number; actualCells: number; missingCells: number; sellableCells: number; reservedCells: number; paidCells: number; heldCells: number; complete: boolean; reservedSegmentLoad: number | null; paidSegmentLoad: number | null }
export interface ReportSummary {
  metadata: ReportMetadata;
  collections: { totals: ReportMoney; byPaymentMethod: Record<string, ReportMoney>; trend: { date: string; money: ReportMoney }[] };
  bookings: { bookingsCreated: number; byStatus: Record<string, number>; bySource: Record<string, number>; cancellations: number; paymentTimeouts: number; currentUnpaidCount: number; ticketsIssued: number; validTickets: number; voidTickets: number };
  cancellations: { totalCancellations: number; byReason: Record<string, number>; refundedCancellations: number; unpaidCancellations: number; amountRefunded: number };
  attendance: ReportAttendance; load: ReportLoad;
  operations: { trips: number; boardingTrips: number; runningTrips: number; upcomingWithoutDriver: number; openPickups: number; incompleteTrips: number };
}
export interface TripPerformance { tripId: number; routeId: number; route: string; plannedDeparture: string; status: string; bus: string; bookings: number; validTickets: number; attendance: ReportAttendance; money: ReportMoney; load: ReportLoad; wholeTripAvailableSeats: number | null }
export interface RoutePerformance { routeId: number; route: string; tripCount: number; operatedTripCount: number; bookings: number; validTickets: number; attendance: ReportAttendance; money: ReportMoney; load: ReportLoad }
export interface ReportTable<T> { metadata: ReportMetadata; data: T[]; pagination: { page: number; size: number; totalElements: number; totalPages: number } }
