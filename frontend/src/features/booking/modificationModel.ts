export type ModificationType = "SEAT_CHANGE" | "TRIP_CHANGE";
export interface Rule { allowed: boolean; reasonCode: string | null; message: string | null }
export interface Eligibility {
  seatChange: Rule; tripChange: Rule; bookingCode: string; source: string; contactName: string; tripId: number;
  items: { bookingItemId: number; seatId: number; seatLabel: string }[];
}
export interface ModificationMoney {
  currentTotal: number; newTotal: number; fareDelta: number; alreadyCollected: number;
  collectionRequired: number; refundRequired: number; newAmountDue: number;
}
export interface ModificationQuote {
  type: ModificationType; sourceTripId: number; targetTripId: number; bookingCode: string;
  journeyName: string; sourceDeparture: string; targetDeparture: string; money: ModificationMoney;
  items: { bookingItemId: number; oldSeatId: number; oldSeatLabel: string; newSeatId: number; newSeatLabel: string; oldFare: number; newFare: number }[];
}
export interface ModificationHistory {
  id: number; code: string; status: string; quote: ModificationQuote; actorType: string; actorName: string;
  createdAt: string; expiresAt: string; completedAt: string | null;
}
export function cashLabel(m: ModificationMoney): string {
  if (m.newAmountDue > 0) return "Số tiền cần thanh toán cho đặt vé mới";
  if (m.collectionRequired > 0) return "Cần thu thêm (mô phỏng)";
  if (m.refundRequired > 0) return "Cần hoàn lại (mô phỏng)";
  return "Không phát sinh thu hoặc hoàn tiền";
}
export function selectionComplete(itemIds: number[], seats: number[]): boolean {
  return itemIds.length > 0 && seats.length === itemIds.length && new Set(seats).size === seats.length;
}
export const modificationLabels: Record<string, string> = {
  HELD: "Đang giữ ghế", AWAITING_PAYMENT: "Chờ thu thêm mô phỏng", COMPLETED: "Đã hoàn tất",
  EXPIRED: "Đã hết hạn", CANCELLED: "Đã hủy yêu cầu", FAILED: "Không thành công",
};
