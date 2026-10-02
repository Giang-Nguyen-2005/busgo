import { useQueries } from "@tanstack/react-query";
import { operatorApi } from "../../api/operatorApi";
import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useOccupancy, useOperatorBooking } from "../../features/operator/queries";
import { useTripWorkspace } from "../../features/operator/TripWorkspace";
import { boardStatus, cellLabels, cellStatus, holdExpired, intersectingBookings, refreshExpiredHolds, seatCells, seatSummary, selectedSegments, wholeJourneyAvailable } from "../../features/operator/dispatch";
import { OperationsBadge, OperationsQueryState } from "../../features/operator/OperationsShared";
import { seatPassenger } from "../../features/operator/operations";
import { RefreshState } from "../../features/operator/RefreshState";
import { dateTime, paymentStatusLabel } from "../../utils/format";
import type { OperatorBookingDetail, SeatSegmentState, TripDetailResponse, TripOccupancy, TripSegmentResponse } from "../../types/operator";

export function CompletenessWarning({ data }: { data: TripOccupancy }) {
  return data.complete === false ? <div className="notice danger" role="alert">Tồn kho chưa đầy đủ · {data.missingInventoryCellCount} ô ghế × chặng chưa có dữ liệu ({data.actualInventoryCellCount}/{data.expectedInventoryCellCount}). Không coi ô thiếu là ghế trống.</div> : null;
}
export function SeatLegend() {
  return <div className="operator-legend" aria-label="Chú giải tình trạng ghế">{Object.entries(cellLabels).map(([status, label]) => <span key={status} className={`operator-badge operator-status-${status}`}>{label}</span>)}</div>;
}
const segmentName = (trip: TripDetailResponse, segment: TripSegmentResponse) => `${trip.stops.find(s => s.id === segment.fromTripStopId)?.locationName || `#${segment.fromTripStopId}`} → ${trip.stops.find(s => s.id === segment.toTripStopId)?.locationName || `#${segment.toTripStopId}`}`;
export function HoldState({ cell, now }: { cell: SeatSegmentState; now: number }) {
  return <p>{holdExpired(cell, now) ? "Đã đến hạn giữ chỗ · đang cập nhật" : cell.holdExpiresAt ? `Hết hạn: ${dateTime(cell.holdExpiresAt)}` : "Đang giữ chỗ · chưa có giờ hết hạn"}</p>;
}
export function BookingInspectionContent({ booking: b, seatId }: { booking: OperatorBookingDetail; seatId: number }) {
  const [copied, setCopied] = useState("");
  const latest = b.payments.reduce<(typeof b.payments)[number] | undefined>((a, p) => !a || p.id > a.id ? p : a, undefined);
  return <section className="operator-inspection-booking"><div className="inspection-group"><h3>{b.bookingCode}</h3><OperationsBadge status={b.status} /></div>
    <section className="inspection-group"><h4>Thanh toán</h4><p>{latest ? paymentStatusLabel(latest.status) : "Chưa có giao dịch thanh toán"}</p></section>
    <section className="inspection-group"><h4>Hành trình · Đón → trả</h4><p>{b.pickup.name} → {b.dropoff.name}</p>
    {b.pickup.time && <p>Giờ đón: {dateTime(b.pickup.time)}</p>}{b.dropoff.time && <p>Giờ trả: {dateTime(b.dropoff.time)}</p>}</section>
    <section className="inspection-group"><h4>Liên hệ đặt vé</h4><p>{b.contact.name}</p><p>{b.contact.phone} <button className="secondary" onClick={async () => { try { await navigator.clipboard.writeText(b.contact.phone); setCopied("Đã sao chép"); } catch { setCopied("Không thể sao chép. Vui lòng chọn số điện thoại."); } }}>Sao chép số</button></p><span role="status">{copied}</span>
    {b.contact.email && <p>{b.contact.email}</p>}</section>
    <section className="inspection-group"><h4>Ghế & vé điện tử</h4>{b.items.filter(item => item.tripSeatId === seatId).map(item => <dl key={item.bookingItemId}>
      <dt>Ghế</dt><dd>{item.seatCode}</dd><dt>Khách trên ghế</dt><dd>{seatPassenger(item.passengerName)}</dd>
      <dt>Tên trên vé</dt><dd>{item.ticket?.passengerName || "Chưa có tên trên vé"}</dd><dt>Mã vé</dt><dd>{item.ticket?.ticketCode || "Chưa có vé"}</dd>
    </dl>)}<p className="muted">Tên trên vé và liên hệ đặt vé không xác minh danh tính khách trên ghế.</p></section>
    <Link to={`/operator/bookings/${b.bookingId}`}>Xem đầy đủ đặt vé</Link>
  </section>;
}
function BookingInspection({ id, seatId }: { id: number; seatId: number }) {
  const query = useOperatorBooking(id);
  return <OperationsQueryState query={query}>{booking => <BookingInspectionContent booking={booking} seatId={seatId} />}</OperationsQueryState>;
}
export function SeatBoard({ trip, data, segments, inspect, selectedSeatId, bookings = {} }: { bookings?: Record<number, OperatorBookingDetail | undefined>; trip: TripDetailResponse; data: TripOccupancy; segments: TripSegmentResponse[]; inspect: (id: number) => void; selectedSeatId?: number | null }) {
  const floors = [...new Set(trip.seats.map(s => s.floor))].sort((a, b) => a - b);
  return <div className="operator-seat-layout">{floors.map(floor => {
    const seats = trip.seats.filter(s => s.floor === floor);
    const minRow = Math.min(...seats.map(s => s.row)), minCol = Math.min(...seats.map(s => s.column));
    const columns = Array.from({ length: Math.max(...seats.map(s => s.column)) - minCol + 1 }, (_, i) => seats.some(s => s.column === minCol + i) ? "142px" : "26px").join(" ");
    return <section key={floor}><h3>Tầng {floor}</h3><div className="operator-seat-scroll" tabIndex={0} role="region" aria-label={`Sơ đồ tầng ${floor}`}><div className="operator-seat-grid operator-live-seats" style={{ gridTemplateColumns: columns }}>
      {seats.map(seat => {
        const cells = seatCells(data, seat.id, segments), summary = seatSummary(cells);
        const status = boardStatus(cells);
        const occupied = intersectingBookings(cells);
        return <button key={seat.id} className={`operator-seat operator-status-${status}`} aria-pressed={selectedSeatId === seat.id} disabled={!segments.length} style={{ gridRow: seat.row - minRow + 1, gridColumn: seat.column - minCol + 1 }} aria-label={`Ghế ${seat.seatCode} · ${summary}`} aria-haspopup="dialog" onClick={() => inspect(seat.id)}>
          <strong>{seat.seatCode}</strong><small>{summary}</small>
          {occupied.map(({ bookingId }) => <span className="operator-seat-contact" key={bookingId}>
            <b>{bookings[bookingId]?.contact.name || "Liên hệ: xem chi tiết"}</b>
            {bookings[bookingId]?.contact.phone && <span>{bookings[bookingId]!.contact.phone}</span>}
            <span>{cells.find(c => c?.bookingId === bookingId)?.bookingCode || `#${bookingId}`}</span>
          </span>)}
          {cells.length === 1 && cellStatus(cells[0]) === "HELD" && cells[0]?.holdExpiresAt && <small>Hết hạn: {dateTime(cells[0].holdExpiresAt)}</small>}
          {cells.length > 1 && <span className="operator-mini-states">{cells.map((cell, i) => <span key={segments[i].id} className={`operator-badge operator-status-${cellStatus(cell)}`} title={`${segmentName(trip, segments[i])}: ${cellLabels[cellStatus(cell)]}`}>{i + 1}. {cellLabels[cellStatus(cell)]}</span>)}</span>}
        </button>;
      })}</div></div></section>;
  })}</div>;
}
function BoardWithContacts(props: React.ComponentProps<typeof SeatBoard>) {
  const ids = [...new Set(props.trip.seats.flatMap(s => intersectingBookings(seatCells(props.data, s.id, props.segments)).map(b => b.bookingId)))];
  const queries = useQueries({ queries: ids.map(id => ({ queryKey: ["operator", "bookings", id], queryFn: ({ signal }: { signal: AbortSignal }) => operatorApi.booking(id, signal), staleTime: 30_000, retry: 1 })) });
  const bookings = Object.fromEntries(ids.map((id, i) => [id, queries[i].isError ? undefined : queries[i].data]));
  return <SeatBoard {...props} bookings={bookings} />;
}
export function OperatorSeatsPage() {
  const trip = useTripWorkspace();
  const query = useOccupancy(trip.id);
  const [params, setParams] = useSearchParams();
  const segments = selectedSegments(trip, params);
  const [seatId, setSeatId] = useState<number | null>(null);
  const [bookingId, setBookingId] = useState<number | null>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  const trigger = useRef<HTMLElement | null>(null);
  const handledExpiries = useRef(new Set<string>());
  const [now, setNow] = useState(Date.now());
  useEffect(() => { handledExpiries.current.clear(); }, [trip.id]);
  useEffect(() => {
    const tick = () => {
      const time = Date.now(); setNow(time);
      if (!query.isFetching) refreshExpiredHolds(query.data, handledExpiries.current, time, () => { void query.refetch(); });
      // Never infer AVAILABLE locally; ordinary 30s polling covers unchanged expired holds.
    };
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [query.data, query.isFetching, query.refetch]);
  const close = () => { dialog.current?.close(); setSeatId(null); setBookingId(null); trigger.current?.focus(); };
  useEffect(() => { if (seatId !== null) dialog.current?.showModal(); }, [seatId]);
  useEffect(() => { dialog.current?.close(); setSeatId(null); setBookingId(null); }, [params.toString(), trip.id]);
  const set = (key: string, value: string) => setParams(old => { const next = new URLSearchParams(old); value ? next.set(key, value) : next.delete(key); return next; });
  return <><h2>Sơ đồ ghế</h2><p className="muted">Vị trí theo bản chụp chuyến xe. Chọn ghế để xem chi tiết theo chặng.</p>
    <div className="operator-filters"><label className="field">Ngữ cảnh xem<select value={params.get("mode") === "journey" ? "journey" : "segment"} onChange={e => set("mode", e.target.value)}><option value="segment">Theo chặng</option><option value="journey">Theo hành trình</option></select></label>
      {params.get("mode") !== "journey" ? <label className="field">Chặng<select value={segments.length === 1 ? segments[0].id : ""} onChange={e => set("segment", e.target.value)}><option value="">Chọn một chặng</option>{[...trip.segments].sort((a,b) => a.segmentOrder-b.segmentOrder).map(s => <option key={s.id} value={s.id}>{segmentName(trip, s)}</option>)}</select></label> : <>
        {(["from", "to"] as const).map((key, i) => <label className="field" key={key}>{i ? "Điểm cuối" : "Điểm đầu"}<select value={params.get(key) || ""} onChange={e => set(key, e.target.value)}><option value="">Chọn điểm</option>{[...trip.stops].sort((a,b)=>a.stopOrder-b.stopOrder).map(s => <option key={s.id} value={s.id}>{s.locationName}</option>)}</select></label>)}
      </>}</div>
    {!segments.length ? <p className="notice">Chọn một chặng hoặc hành trình liên tục hợp lệ để xem tình trạng ghế.</p> : <p className="operator-context"><strong>{segments.map(s => segmentName(trip, s)).join(" · ")}</strong></p>}
    <RefreshState query={query} /><OperationsQueryState query={query}>{data => {
      const cells = seatId === null ? [] : seatCells(data, seatId, segments);
      const bookings = intersectingBookings(cells);
      const activeBooking = bookings.some(b => b.bookingId === bookingId) ? bookingId : bookings[0]?.bookingId;
      return <><CompletenessWarning data={data} /><SeatLegend />
        {!!segments.length && <div className="operator-segment-counts">
          {params.get("mode") === "journey" && <strong>Trống suốt hành trình: {trip.seats.filter(s => wholeJourneyAvailable(seatCells(data, s.id, segments))).length}</strong>}
          {segments.map(s => { const states = trip.seats.map(seat => cellStatus(seatCells(data, seat.id, [s])[0])); return <p key={s.id}><strong>{segmentName(trip, s)}</strong> · {Object.entries(cellLabels).map(([status,label]) => `${label}: ${states.filter(x => x === status).length}`).join(" · ")}</p>; })}
        </div>}
        <BoardWithContacts trip={trip} data={data} segments={segments} selectedSeatId={seatId} inspect={id => { trigger.current = document.activeElement as HTMLElement; setSeatId(id); setBookingId(null); }} />
        {!trip.seats.length && <p className="notice">Chuyến chưa có ghế trong bản chụp.</p>}
        <dialog ref={dialog} className="operator-seat-drawer" aria-labelledby="seat-inspection-title" onCancel={e => { e.preventDefault(); close(); }} onClose={() => { setSeatId(null); setBookingId(null); trigger.current?.focus(); }}>
          <div className="operator-heading"><h2 id="seat-inspection-title">Ghế {trip.seats.find(s => s.id === seatId)?.seatCode}</h2><button className="secondary" autoFocus onClick={close}>Đóng</button></div>
          <ol className="operator-inspection-segments">{segments.map((s,i) => <li key={s.id}><strong>{segmentName(trip,s)}</strong><p>{cellLabels[cellStatus(cells[i])]}</p>
            {cellStatus(cells[i]) === "MISSING" && <p className="notice danger">Cảnh báo toàn vẹn dữ liệu: chưa có tồn kho cho ghế trên chặng này.</p>}
            {cells[i] && cellStatus(cells[i]) === "HELD" && <HoldState cell={cells[i]!} now={now} />}
          </li>)}</ol>
          {bookings.map(b => <section key={b.bookingId}><button className="secondary" aria-expanded={activeBooking === b.bookingId} onClick={() => setBookingId(b.bookingId)}>{cells.find(c => c?.bookingId === b.bookingId)?.bookingCode || `Đặt vé #${b.bookingId}`}</button><p>{segments.filter(s => b.segmentIds.includes(s.id)).map(s => segmentName(trip,s)).join(" · ")}</p>
            {activeBooking === b.bookingId && seatId !== null && <BookingInspection key={b.bookingId} id={b.bookingId} seatId={seatId} />}</section>)}
        </dialog>
      </>;
    }}</OperationsQueryState></>;
}
