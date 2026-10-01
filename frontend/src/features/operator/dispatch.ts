import type { PassengerManifestRow, SeatSegmentState, TripDetailResponse, TripOccupancy, TripSegmentResponse, TripSummaryResponse } from "../../types/operator";

export const vietnamToday = (now = new Date()) => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
export const departureClock = (value: string) => new Intl.DateTimeFormat("vi-VN", { timeZone: "Asia/Ho_Chi_Minh", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
export const businessDate = (value: string | null, now = new Date()) => value && /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value ? value : vietnamToday(now);
export const tripLabels = { SCHEDULED: "Đã lên lịch", BOARDING: "Đang đón khách", DEPARTED: "Đang chạy", COMPLETED: "Hoàn thành", CANCELLED: "Đã hủy" };
export const tripTabs = [["", "Tổng quan"], ["seats", "Sơ đồ ghế"], ["passengers", "Hành khách"], ["occupancy", "Tình trạng chặng"]] as const;
export const overdue = (trip: TripSummaryResponse, now = Date.now()) => trip.status === "SCHEDULED" && Date.parse(trip.departureTime) < now;
export function dispatchOrder(trips: TripSummaryResponse[]) {
  const priority = (t: TripSummaryResponse) => t.status === "BOARDING" ? 0 : t.status === "SCHEDULED" ? 1 : 2;
  return [...trips].sort((a, b) => priority(a) - priority(b) || Date.parse(a.departureTime) - Date.parse(b.departureTime));
}
// Resolve only snapshot IDs, and reject any gap/reversal in the requested journey.
export function selectedSegments(trip: Pick<TripDetailResponse, "segments" | "stops">, params: URLSearchParams): TripSegmentResponse[] {
  const ordered = [...trip.segments].sort((a, b) => a.segmentOrder - b.segmentOrder);
  if (ordered.length === 1) return ordered;
  if (params.get("mode") !== "journey") return ordered.filter(s => s.id === Number(params.get("segment")));
  const start = Number(params.get("from")), end = Number(params.get("to"));
  if (!trip.stops.some(s => s.id === start) || !trip.stops.some(s => s.id === end) || start === end) return [];
  const index = ordered.findIndex(s => s.fromTripStopId === start);
  if (index < 0) return [];
  const result: TripSegmentResponse[] = [];
  let cursor = start;
  for (const segment of ordered.slice(index)) {
    if (segment.fromTripStopId !== cursor) return [];
    result.push(segment);
    cursor = segment.toTripStopId;
    if (cursor === end) return result;
  }
  return [];
}
export function seatCells(data: TripOccupancy, seatId: number, segments: TripSegmentResponse[]): (SeatSegmentState | undefined)[] {
  const seat = data.seats.find(s => s.tripSeatId === seatId);
  return segments.map(s => seat?.segments.find(c => c.tripSegmentId === s.id));
}
export const cellStatus = (cell?: SeatSegmentState) => !cell || cell.missing || !cell.status ? "MISSING" : cell.status;
export const cellLabels = { AVAILABLE: "Còn trống", HELD: "Đang giữ chỗ", BOOKED: "Đã đặt", BLOCKED: "Đã khóa", MISSING: "Chưa có dữ liệu" };
export const wholeJourneyAvailable = (cells: (SeatSegmentState | undefined)[]) => cells.length > 0 && cells.every(c => cellStatus(c) === "AVAILABLE");
export function seatSummary(cells: (SeatSegmentState | undefined)[]) {
  if (!cells.length) return "Chọn chặng để xem";
  if (cells.length > 1 && wholeJourneyAvailable(cells)) return "Trống suốt hành trình";
  const first = cells[0];
  const same = cells.every(c => cellStatus(c) === cellStatus(first) && (c?.bookingId ?? null) === (first?.bookingId ?? null));
  return same ? cellLabels[cellStatus(first)] : "Khác nhau theo chặng";
}
export function intersectingBookings(cells: (SeatSegmentState | undefined)[]) {
  const groups = new Map<number, number[]>();
  for (const c of cells) if (cellStatus(c) === "BOOKED" && c?.bookingId) groups.set(c.bookingId, [...(groups.get(c.bookingId) || []), c.tripSegmentId]);
  return [...groups].map(([bookingId, segmentIds]) => ({ bookingId, segmentIds }));
}
export const holdExpired = (cell: SeatSegmentState | undefined, now = Date.now()) => cellStatus(cell) === "HELD" && !!cell?.holdExpiresAt && Date.parse(cell.holdExpiresAt) <= now;
export function refreshExpiredHolds(data: TripOccupancy | undefined, handled: Set<string>, now: number, refresh: () => void) {
  let expired = false;
  for (const seat of data?.seats || []) for (const cell of seat.segments) {
    const key = `${seat.tripSeatId}:${cell.tripSegmentId}:${cell.holdExpiresAt}`;
    if (holdExpired(cell, now) && !handled.has(key)) { handled.add(key); expired = true; }
  }
  if (expired) refresh();
}
export function manifestGroups(rows: PassengerManifestRow[], search: string) {
  const normalize = (s: string) => s.normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/đ/g, "d").replace(/Đ/g, "D").toLowerCase();
  const term = normalize(search.trim());
  const groups = new Map<string, PassengerManifestRow[]>();
  for (const row of rows) {
    if (!normalize([row.seatCode, row.bookingCode, row.passengerName, row.ticketPassengerName, row.contact.name, row.contact.phone].join(" ")).includes(term)) continue;
    const key = `${row.pickup.tripStopId}:${row.dropoff.tripStopId}`;
    groups.set(key, [...(groups.get(key) || []), row]);
  }
  return [...groups].map(([key, passengers]) => ({ key, passengers }));
}
