import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { Armchair, BusFront, SlidersHorizontal, X } from "lucide-react";
import { operatorApi } from "../../api/operatorApi";
import { dateTime } from "../../utils/format";
import { Drawer } from "../../components/foundations/Drawer";
import { OperationsQueryState } from "../../features/operator/OperationsShared";
import { RefreshState } from "../../features/operator/RefreshState";
import { useOccupancy, useOperatorBooking } from "../../features/operator/queries";
import { businessDate, departureClock, tripLabels, overdue, selectedSegments, seatCells, cellStatus, cellLabels, seatSummary, boardStatus, intersectingBookings, refreshExpiredHolds } from "../../features/operator/dispatch";
import { OperatorPageHeader, OperatorStatusBadge } from "../../features/operator/shared";
import { BookingInspectionContent, CompletenessWarning, HoldState, SeatLegend } from "./OperatorSeatsPage";
import type { TripDetailResponse, TripOccupancy, TripSegmentResponse, TripStatus } from "../../types/operator";
import "../../features/operator/dispatch-board.css";

const segmentName = (trip: TripDetailResponse, s: TripSegmentResponse) => `${trip.stops.find(t => t.id === s.fromTripStopId)?.locationName || `#${s.fromTripStopId}`} → ${trip.stops.find(t => t.id === s.toTripStopId)?.locationName || `#${s.toTripStopId}`}`;
function useCompact() {
  const [compact, setCompact] = useState(() => window.matchMedia("(max-width: 700px)").matches);
  useEffect(() => { const media = window.matchMedia("(max-width: 700px)"); const update = () => setCompact(media.matches); media.addEventListener("change", update); return () => media.removeEventListener("change", update); }, []);
  return compact;
}
function SelectedBooking({ id, seatId }: { id: number; seatId: number }) {
  const query = useOperatorBooking(id);
  return <OperationsQueryState query={query}>{booking => <BookingInspectionContent booking={booking} seatId={seatId} />}</OperationsQueryState>;
}
export function DispatchSeatGrid({ trip, data, segments, floor, selected, onSelect }: { trip: TripDetailResponse; data: TripOccupancy; segments: TripSegmentResponse[]; floor: number; selected: number | null; onSelect: (id: number) => void }) {
  const seats = trip.seats.filter(s => s.floor === floor);
  if (!seats.length) return <p className="notice">Chưa có ghế trên tầng này.</p>;
  const minRow = Math.min(...seats.map(s => s.row)), minCol = Math.min(...seats.map(s => s.column));
  const columns = Array.from({ length: Math.max(...seats.map(s => s.column)) - minCol + 1 }, (_, i) => seats.some(s => s.column === minCol + i) ? "100px" : "24px").join(" ");
  return <div className="db-seat-scroll" role="region" aria-label={`Sơ đồ tầng ${floor}`} tabIndex={0}><div className="db-bus"><div className="db-bus-front"><BusFront size={20} /> Đầu xe · Tầng {floor}</div><div className="db-seats" style={{ gridTemplateColumns: columns }}>{seats.map(seat => {
    const cells = seatCells(data, seat.id, segments), summary = seatSummary(cells);
    return <button key={seat.id} disabled={!segments.length} aria-label={`Ghế ${seat.seatCode} · ${summary}`} aria-pressed={selected === seat.id} className={`db-seat operator-status-${boardStatus(cells)}`} style={{ gridRow: seat.row - minRow + 1, gridColumn: seat.column - minCol + 1 }} onClick={() => onSelect(seat.id)}><Armchair size={21} aria-hidden="true" /><strong>{seat.seatCode}</strong><small>{summary}</small></button>;
  })}</div></div></div>;
}
function SeatWorkspace({ trip, data }: { trip: TripDetailResponse; data: TripOccupancy }) {
  const floors = [...new Set(trip.seats.map(s => s.floor))].sort((a,b) => a-b);
  const [floorChoice, setFloor] = useState(floors[0]);
  const floor = floors.includes(floorChoice) ? floorChoice : floors[0];
  const [context, setContext] = useState(new URLSearchParams());
  const [seatId, setSeat] = useState<number | null>(null);
  const [bookingId, setBooking] = useState<number | null>(null);
  const compact = useCompact();
  const trigger = useRef<HTMLElement | null>(null);
  const segments = selectedSegments(trip, context);
  const cells = seatId === null ? [] : seatCells(data, seatId, segments);
  const bookings = intersectingBookings(cells);
  const activeBooking = bookings.some(b => b.bookingId === bookingId) ? bookingId : null;
  const close = () => { setSeat(null); setBooking(null); trigger.current?.focus(); };
  const changeContext = (key: string, value: string) => { setContext(old => { const p = new URLSearchParams(old); p.set(key,value); return p; }); setSeat(null); setBooking(null); };
  const inspector = <>
    {seatId === null ? <div className="db-inspect-empty"><Armchair size={36} /><h3>Kiểm tra một ghế</h3><p>Chọn chặng, sau đó chọn ghế để xem tồn kho và các đặt vé liên quan.</p></div> : <>
      <ol className="db-segment-details">{segments.map((s,i) => <li key={s.id}><strong>{segmentName(trip,s)}</strong><p className={`operator-badge operator-status-${cellStatus(cells[i])}`}>{cellLabels[cellStatus(cells[i])]}</p>{cellStatus(cells[i]) === "HELD" && cells[i] && <HoldState cell={cells[i]!} now={Date.now()} />}{cellStatus(cells[i]) === "MISSING" && <p>Chưa có tồn kho. Không xác nhận ghế trống.</p>}</li>)}</ol>
      {bookings.map(b => <section key={b.bookingId}><button className="secondary" aria-expanded={activeBooking === b.bookingId} onClick={() => setBooking(b.bookingId)}>{cells.find(c => c?.bookingId === b.bookingId)?.bookingCode || `Đặt vé #${b.bookingId}`}</button><p className="fine-print">{segments.filter(s => b.segmentIds.includes(s.id)).map(s => segmentName(trip,s)).join(" · ")}</p>{activeBooking === b.bookingId && <SelectedBooking key={b.bookingId} id={b.bookingId} seatId={seatId} />}</section>)}
      {!bookings.length && <p>Không có đặt vé được liên kết trong ngữ cảnh này.</p>}
      <p className="fine-print">Đã đặt không đồng nghĩa đã thanh toán hoặc đã lên xe. Thanh toán trong BusGo là mô phỏng.</p>
      <nav className="db-links"><Link to={`/operator/trips/${trip.id}/passengers`}>Danh sách hành khách</Link><Link to={`/operator/trips/${trip.id}/occupancy`}>Tồn kho từng chặng</Link></nav>
    </>}
  </>;
  return <><section className="db-workspace">
    <div className="db-banner"><span className="eyebrow">CHUYẾN ĐANG XEM · GIỜ VIỆT NAM</span><h2><span>{departureClock(trip.departureTime)}</span> {trip.route.name}</h2><p>{trip.bus.licensePlate} · {trip.bus.busTypeName}</p><small>{dateTime(trip.departureTime)}</small><OperatorStatusBadge status={trip.status} /><Link to={`/operator/trips/${trip.id}`}>Chi tiết chuyến</Link></div>
    <div className="db-context-controls"><label className="field">Ngữ cảnh<select value={context.get("mode") || "segment"} onChange={e => changeContext("mode",e.target.value)}><option value="segment">Một chặng</option><option value="journey">Hành trình liên tục</option></select></label>
      {context.get("mode") !== "journey" ? <label className="field">Chặng<select value={segments.length === 1 ? segments[0].id : ""} onChange={e => changeContext("segment",e.target.value)}><option value="">Chọn chặng</option>{[...trip.segments].sort((a,b)=>a.segmentOrder-b.segmentOrder).map(s=><option key={s.id} value={s.id}>{segmentName(trip,s)}</option>)}</select></label> : (["from","to"] as const).map((key,i)=><label className="field" key={key}>{i ? "Điểm cuối" : "Điểm đầu"}<select value={context.get(key) || ""} onChange={e=>changeContext(key,e.target.value)}><option value="">Chọn điểm</option>{[...trip.stops].sort((a,b)=>a.stopOrder-b.stopOrder).map(s=><option key={s.id} value={s.id}>{s.locationName}</option>)}</select></label>)}
    </div>
    <p className="db-context">{segments.length ? segments.map(s=>segmentName(trip,s)).join(" · ") : "Chọn một chặng hoặc hành trình liên tục, xuôi chiều để xem ghế."}</p>
    <CompletenessWarning data={data} /><SeatLegend />
    <div className="db-floors" aria-label="Chọn tầng">{floors.map(f=><button key={f} className="secondary" aria-pressed={floor===f} onClick={()=>{setFloor(f);setSeat(null);setBooking(null);}}>Tầng {f}</button>)}</div>
    <DispatchSeatGrid trip={trip} data={data} segments={segments} floor={floor} selected={seatId} onSelect={id=>{trigger.current=document.activeElement as HTMLElement;setSeat(id);setBooking(null);}} />
  </section>
  {compact ? <Drawer id="dispatch-inspector" title={`Ghế ${trip.seats.find(s=>s.id===seatId)?.seatCode || ""}`} closeLabel="Đóng chi tiết ghế" open={seatId!==null} onClose={close}>{inspector}</Drawer> : <aside className="db-inspector" aria-label="Chi tiết ghế"><div className="db-inspector-heading"><h2>{seatId === null ? "Chi tiết ghế" : `Ghế ${trip.seats.find(s=>s.id===seatId)?.seatCode}`}</h2>{seatId !== null && <button className="secondary" aria-label="Đóng chi tiết ghế" onClick={close}><X size={18} /></button>}</div>{inspector}</aside>}
  </>;
}
function SelectedTrip({ id }: { id: number }) {
  const trip = useQuery({ queryKey: ["operator","trips",id], queryFn: ({signal}) => operatorApi.trip(id,signal), refetchInterval:30_000,retry:1 });
  const occupancy = useOccupancy(id);
  const expiries = useRef(new Set<string>());
  useEffect(()=>{ const timer=window.setInterval(()=>{if (!occupancy.isFetching) refreshExpiredHolds(occupancy.data,expiries.current,Date.now(),()=>void occupancy.refetch());},1000);return ()=>window.clearInterval(timer);},[occupancy.data,occupancy.isFetching,occupancy.refetch]);
  return <div className="db-selected"><div className="db-refresh" aria-label="Lần cập nhật cũ hơn của chuyến và tồn kho"><RefreshState query={{ ...occupancy, isFetching: trip.isFetching || occupancy.isFetching, dataUpdatedAt: Math.min(trip.dataUpdatedAt, occupancy.dataUpdatedAt) }} onRefresh={() => { void trip.refetch(); void occupancy.refetch(); }} /></div><OperationsQueryState query={trip}>{t=><OperationsQueryState query={occupancy}>{data=><SeatWorkspace key={id} trip={t} data={data} />}</OperationsQueryState>}</OperationsQueryState></div>;
}
export function OperatorDispatchPage() {
  const [params,setParams]=useSearchParams();
  const date=businessDate(params.get("businessDate"));
  const status = Object.keys(tripLabels).includes(params.get("status") || "") ? params.get("status") as TripStatus : undefined;
  const rawPage=Number(params.get("page")); const page=Number.isSafeInteger(rawPage)&&rawPage>0?rawPage:0;
  const filters={businessDate:date,status,page,size:20};
  const query=useQuery({queryKey:["operator","trips",filters],queryFn:({signal})=>operatorApi.trips(filters,signal),refetchInterval:30_000,retry:1});
  const [filterOpen,setFilterOpen]=useState(false);
  const compact=useCompact();
  const set=(key:string,value:string)=>setParams(old=>{const p=new URLSearchParams(old);value?p.set(key,value):p.delete(key);p.delete("trip");if(["businessDate","status"].includes(key))p.delete("page");if(["page","businessDate","status"].includes(key)){p.delete("q");p.delete("route");p.delete("bus");}return p;});
  return <div className="db-page"><OperatorPageHeader title="Bảng điều phối"><Link to="/operator">Tổng quan hôm nay</Link></OperatorPageHeader>
    <div className="db-toolbar"><label className="field">Ngày vận hành · Việt Nam<input type="date" value={date} onChange={e=>set("businessDate",e.target.value)} /></label><label className="field">Trạng thái · toàn bộ ngày<select value={status||""} onChange={e=>set("status",e.target.value)}><option value="">Tất cả trạng thái</option>{Object.entries(tripLabels).map(([s,label])=><option key={s} value={s}>{label}</option>)}</select></label><RefreshState query={query} /></div>
    <OperationsQueryState query={query}>{result=>{
      const rows=result.data.filter(t=>(!params.get("route")||String(t.route.routeId)===params.get("route"))&&(!params.get("bus")||String(t.bus.id)===params.get("bus"))&&`${t.id} ${t.route.name} ${t.bus.licensePlate}`.toLocaleLowerCase("vi").includes((params.get("q")||"").toLocaleLowerCase("vi"))).sort((a,b)=>Date.parse(a.departureTime)-Date.parse(b.departureTime));
      const requested=Number(params.get("trip"));
      const selected=rows.find(t=>t.id===requested) || (!params.has("trip") ? rows[0] : undefined);
      const selectedId = Number.isSafeInteger(requested) && requested > 0 ? requested : selected?.id;
      const filterFields=<div className="db-filter-fields">{compact && <label className="field">Trạng thái · toàn bộ ngày<select value={status||""} onChange={e=>set("status",e.target.value)}><option value="">Tất cả trạng thái</option>{Object.entries(tripLabels).map(([s,label])=><option key={s} value={s}>{label}</option>)}</select></label>}<p className="fine-print">Lọc trong {result.data.length} chuyến của trang {page+1}. Đổi trang để tìm thêm.</p><label className="field">Tìm trên trang<input value={params.get("q")||""} placeholder="Tuyến, biển số hoặc mã chuyến" onChange={e=>set("q",e.target.value)} /></label><label className="field">Tuyến trên trang<select value={params.get("route")||""} onChange={e=>set("route",e.target.value)}><option value="">Tất cả tuyến trên trang</option>{[...new Map(result.data.map(t=>[t.route.routeId,t.route])).values()].map(r=><option key={r.routeId} value={r.routeId}>{r.name}</option>)}</select></label><label className="field">Xe trên trang<select value={params.get("bus")||""} onChange={e=>set("bus",e.target.value)}><option value="">Tất cả xe trên trang</option>{[...new Map(result.data.map(t=>[t.bus.id,t.bus])).values()].map(b=><option key={b.id} value={b.id}>{b.licensePlate}</option>)}</select></label></div>;
      return <div className="db-layout"><aside className="db-schedule"><h2>Lịch khởi hành</h2><p className="fine-print">{result.pagination.totalElements} chuyến theo ngày / trạng thái · Trang {page+1}/{Math.max(1,result.pagination.totalPages)}</p>
        {compact ? <><button className="secondary" onClick={()=>setFilterOpen(true)}><SlidersHorizontal size={16} /> Lọc trên trang</button><Drawer id="dispatch-filters" title="Lọc chuyến trên trang" closeLabel="Đóng bộ lọc" open={filterOpen} onClose={()=>setFilterOpen(false)}>{filterFields}<RefreshState query={query} /><button onClick={()=>setFilterOpen(false)}>Xem kết quả</button></Drawer><label className="field">Chọn chuyến<select value={selected?.id||""} onChange={e=>setParams(old=>{const p=new URLSearchParams(old);p.set("trip",e.target.value);return p;})}><option value="" disabled>Chọn chuyến</option>{rows.map(t=><option key={t.id} value={t.id}>{departureClock(t.departureTime)} · {t.route.name} · {t.bus.licensePlate}</option>)}</select></label></> : <>{filterFields}<div className="db-trip-list">{rows.map(t=><button className="db-trip" key={t.id} aria-pressed={selected?.id===t.id} onClick={()=>setParams(old=>{const p=new URLSearchParams(old);p.set("trip",String(t.id));return p;})}><strong>{departureClock(t.departureTime)}</strong><span>{t.route.name}</span><small>{t.bus.licensePlate}</small><OperatorStatusBadge status={t.status} />{overdue(t)&&<small className="operator-overdue">Quá giờ dự kiến</small>}</button>)}</div></>}
        {!rows.length&&<p className="notice">Không có chuyến phù hợp trên trang này.</p>}
        <nav className="db-pagination" aria-label="Trang chuyến"><button className="secondary" disabled={page===0} onClick={()=>set("page",String(page-1))}>Trước</button><button className="secondary" disabled={page+1>=result.pagination.totalPages} onClick={()=>set("page",String(page+1))}>Sau</button></nav>
      </aside>{selectedId ? <SelectedTrip key={`${date}:${status}:${page}:${selectedId}`} id={selectedId} /> : <section className="db-workspace"><h2>{result.pagination.totalElements ? "Chọn chuyến trong danh sách" : "Chưa có chuyến xe"}</h2><p>Thay đổi ngày hoặc bộ lọc để tiếp tục.</p></section>}</div>;
    }}</OperationsQueryState>
  </div>;
}




