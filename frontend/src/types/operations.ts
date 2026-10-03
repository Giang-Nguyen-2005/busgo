export type Capability = "DRIVER" | "ATTENDANT";
export interface Employee { id: number; employeeCode: string; fullName: string; phone: string; status: "ACTIVE" | "INACTIVE"; capabilities: Capability[]; licenceNumber: string | null; licenceClass: string | null; licenceExpiryDate: string | null; version: number }
export interface CrewAssignment { id: number; employeeId: number; duty: Capability; fullName: string; status: string; licenceExpiryDate: string | null }
export interface Crew { assignments: CrewAssignment[]; ready: boolean; warning: string }
export interface Pickup { stopId: number; name: string; stopOrder: number; closedAt: string | null }
export type BoardingStatus = "EXPECTED" | "CHECKED_IN" | "BOARDED" | "NO_SHOW";
export interface AttendanceRow { bookingItemId: number; source: "PHONE" | "WEB"; paymentBlocked: boolean; bookingId: number; bookingCode: string; bookingAmount: number; pickupTime: string | null; seatCode: string; passengerName: string; phone: string; paymentMethod: import("./operator").PaymentMethod; paymentStatus: string; ticketId: number | null; ticketCode: string | null; boardingStatus: BoardingStatus | null; pickupStopId: number; pickupName: string; dropoffName: string }
