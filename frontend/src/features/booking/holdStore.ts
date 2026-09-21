import type { Hold } from "../../types/customer";
export type SavedHold = Hold & { operatorName?: string; routeName?: string };
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
