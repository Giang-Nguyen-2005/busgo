import type { Booking } from "../../types/customer";

// A navigation hint only. Booking status and inventory always come from the API.
const key = "busgo.completed-bookings";
type Recovery = { userId: number; tripId: number; pickupId: number; dropoffId: number; bookingId: number };
function entries(): Recovery[] {
  try {
    const data = JSON.parse(sessionStorage.getItem(key) || "[]");
    return Array.isArray(data) ? data.filter(e => e && [e.userId, e.tripId, e.pickupId, e.dropoffId, e.bookingId].every(id => Number.isSafeInteger(id) && id > 0)) : [];
  } catch { return []; }
}
export function rememberBooking(userId: number, booking: Booking) {
  const hint = { userId, tripId: booking.tripId, pickupId: booking.pickup.locationId, dropoffId: booking.dropoff.locationId, bookingId: booking.bookingId };
  try { sessionStorage.setItem(key, JSON.stringify([...entries(), hint].slice(-20))); } catch { /* Recovery remains available through My bookings. */ }
}
export function bookingRecovery(userId: number | undefined, tripId: number, pickupId: number, dropoffId: number) {
  return entries().reverse().find(e => e.userId === userId && e.tripId === tripId && e.pickupId === pickupId && e.dropoffId === dropoffId)?.bookingId;
}
export const bookingPaymentNavigation = (bookingId: number) => ({ to: `/payment?bookingId=${bookingId}`, options: { replace: true } });
