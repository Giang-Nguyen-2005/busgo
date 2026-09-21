// A convenience draft, never a reservation. Only a fresh seat response can restore it.
const key = "busgo.selection";
export interface SelectionDraft {
  tripId: number;
  pickupLocationId: number;
  dropoffLocationId: number;
  tripSeatIds: number[];
  returnTo: string;
  savedAt: number;
}
export function saveSelection(draft: Omit<SelectionDraft, "savedAt">) {
  sessionStorage.setItem(
    key,
    JSON.stringify({ ...draft, savedAt: Date.now() }),
  );
}
export function clearSelection() {
  sessionStorage.removeItem(key);
}
export function revalidateSelection(
  ids: number[],
  seats: { tripSeatId: number; available: boolean }[],
) {
  const available = new Set(
    seats.filter((seat) => seat.available).map((seat) => seat.tripSeatId),
  );
  return ids.filter((id) => available.has(id));
}
export function readSelection(
  tripId: number,
  pickupLocationId: number,
  dropoffLocationId: number,
): SelectionDraft | null {
  try {
    const draft = JSON.parse(sessionStorage.getItem(key) || "null");
    if (
      !draft ||
      !Number.isFinite(draft.savedAt) ||
      Date.now() - draft.savedAt > 30 * 60_000
    ) {
      clearSelection();
      return null;
    }
    if (
      draft.tripId !== tripId ||
      draft.pickupLocationId !== pickupLocationId ||
      draft.dropoffLocationId !== dropoffLocationId
    )
      return null;
    if (
      !Array.isArray(draft.tripSeatIds) ||
      draft.tripSeatIds.length < 1 ||
      draft.tripSeatIds.length > 5 ||
      !draft.tripSeatIds.every(
        (id: unknown) =>
          typeof id === "number" && Number.isSafeInteger(id) && id > 0,
      ) ||
      new Set(draft.tripSeatIds).size !== draft.tripSeatIds.length
    )
      return null;
    return draft;
  } catch {
    clearSelection();
    return null;
  }
}
