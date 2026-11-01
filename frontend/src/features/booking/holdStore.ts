import type { Hold, PrebookingInformation, Seat } from "../../types/customer";
export type SavedHold = Hold & {
  operatorName?: string;
  routeName?: string;
  searchContext?: string;
  // Presentation snapshot only; never used as input to booking or pricing requests.
  tripInformation?: { prebooking?: PrebookingInformation; vehicleName: string; seats: Pick<Seat, "floor">[] };
};
export function readHold(): SavedHold | null {
  try {
    const value = JSON.parse(sessionStorage.getItem("busgo.hold") || "null");
    return value?.holdToken &&
      value?.tripId &&
      value?.pickup?.locationId &&
      value?.dropoff?.locationId
      ? value
      : null;
  } catch {
    return null;
  }
}
export function saveHold(hold: SavedHold | null) {
  if (hold) sessionStorage.setItem("busgo.hold", JSON.stringify(hold));
  else sessionStorage.removeItem("busgo.hold");
}
