import type { BookingStatus, PaymentMethod } from "./operator";
export interface PublicPayment {
  bookingCode: string; operator: string; journey: string;
  pickup: { name: string; time: string | null }; dropoff: { name: string; time: string | null };
  seats: string[]; amount: number; method: PaymentMethod; status: BookingStatus; mockPayment: boolean;
}
