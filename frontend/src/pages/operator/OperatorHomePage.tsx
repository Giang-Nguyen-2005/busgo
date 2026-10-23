import { ClipboardList, AlertTriangle } from "lucide-react";
import "../../features/operator/dispatch-board.css";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { allPages, operatorApi } from "../../api/operatorApi";
import { useAuth } from "../../features/auth/AuthProvider";
import { canManageOperator } from "../../features/auth/access";
import { OperatorPageHeader, OperatorStatusBadge } from "../../features/operator/shared";
import { OperationsQueryState } from "../../features/operator/OperationsShared";
import { RefreshState } from "../../features/operator/RefreshState";
import { useOccupancy } from "../../features/operator/queries";
import { departureClock, dispatchOrder, overdue, vietnamToday, tripLabels } from "../../features/operator/dispatch";
import type { TripSummaryResponse } from "../../types/operator";
import { date, dateTime } from "../../utils/format";
import { ManagementOverview, useManagementOverview } from "../../features/operator/Reports";
import { CompletenessWarning } from "./OperatorSeatsPage";
import { FleetDashboardWarnings } from "../../features/operator/FleetMaintenance";

function TripShortcuts({ id }: { id: number }) {
  return <nav className="dispatch-shortcuts" aria-label="Công cụ chuyến xe">
    <Link className="button" to={`/operator/dispatch?businessDate=${vietnamToday()}&trip=${id}`}><ClipboardList size={17} /> Bảng điều phối</Link><Link to={`/operator/trips/${id}/seats`}>Sơ đồ ghế</Link>
    <Link to={`/operator/trips/${id}/passengers`}>Hành khách</Link>
    <Link to={`/operator/trips/${id}/occupancy`}>Tình trạng chặng</Link>
    <Link to={`/operator/bookings?tripId=${id}`}>Đặt vé chuyến này</Link>
  </nav>;
}
function HighlightTrip({ trip, upcoming }: { trip: TripSummaryResponse; upcoming: boolean }) {
  const occupancy = useOccupancy(trip.id);
  const bookings = useQuery({ queryKey: ["operator", "bookings", "dashboard", trip.id], queryFn: ({ signal }) => operatorApi.bookings({ tripId: trip.id, page: 0, size: 1 }, signal), refetchInterval: 60_000, retry: 1 });
  return <section className="dispatch-highlight" aria-label="Chuyến cần theo dõi">
    <span className="eyebrow">{trip.status === "BOARDING" ? "ĐANG ĐÓN KHÁCH" : upcoming ? "CHUYẾN SẮP TỚI" : "CHUYẾN CẦN THEO DÕI"}</span>
    <div className="dispatch-highlight-heading"><strong className="operator-departure">{departureClock(trip.departureTime)}</strong><div><h2><Link to={`/operator/trips/${trip.id}`}>{trip.route.name}</Link></h2><p>{dateTime(trip.departureTime)} · {trip.bus.licensePlate} · {trip.bus.busTypeName}</p></div><OperatorStatusBadge status={trip.status} /></div>
    {overdue(trip) && <p className="notice warning">Quá giờ dự kiến · Kiểm tra trạng thái chuyến.</p>}
    <div className="dispatch-trip-metrics">
      <div><small>Đặt vé của chuyến · mọi trạng thái</small><OperationsQueryState query={bookings}>{data => <strong>{data.pagination.totalElements}</strong>}</OperationsQueryState></div>
      <OperationsQueryState query={occupancy}>{data => <><CompletenessWarning data={data} />
        <div><small>Ghế trống suốt chuyến</small><strong>{data.complete ? data.wholeTripAvailableSeatCount : "Chưa đủ dữ liệu"}</strong></div>
        <div><small>Ghế có đặt vé ở ít nhất một chặng</small><strong>{data.complete ? data.seats.filter(s => s.segments.some(c => !c.missing && c.status === "BOOKED")).length : "Chưa đủ dữ liệu"}</strong></div>
      </>}</OperationsQueryState>
    </div>
    <p className="fine-print">Tổng ghế: {trip.seatCount}. Ghế có đặt vé không đồng nghĩa đã thanh toán hoặc đã lên xe. Các số liệu được cập nhật riêng.</p>
    <TripShortcuts id={trip.id} />
  </section>;
}
export function OperatorHomePage() {
  const manage = canManageOperator(useAuth().user?.roles);
  const today = vietnamToday();
  const overview = useManagementOverview(manage);
  const query = useQuery({ queryKey: ["operator", "trips", "today", today], queryFn: ({ signal }) => allPages(p => operatorApi.trips({ ...p, businessDate: today }, signal)), refetchInterval: 60_000, retry: 1 });
  return <div className="operator-dashboard"><OperatorPageHeader title="Điều hành hôm nay"><Link className="button" to="/operator/dispatch"><ClipboardList size={18} /> Bảng điều phối</Link><Link className="button secondary" to="/operator/bookings">Tra cứu đặt vé</Link></OperatorPageHeader>
    <p className="muted">Ngày vận hành · {date(today)} · Giờ Việt Nam</p><RefreshState query={query} />
    <OperationsQueryState query={query}>{trips => {
      const ordered = dispatchOrder(trips);
      const upcoming = ordered.filter(t => t.status === "SCHEDULED" && Date.parse(t.departureTime) >= Date.now()).sort((a,b) => Date.parse(a.departureTime)-Date.parse(b.departureTime))[0];
      const highlight = ordered.find(t => t.status === "BOARDING") || upcoming || ordered.find(t => t.status === "SCHEDULED");
      return <><section className="ops-command" aria-label="Tổng quan vận hành hôm nay"><div><span className="eyebrow">NHỊP VẬN HÀNH · {date(today)}</span><div className="ops-total">{trips.length}<span>chuyến trong ngày</span></div><p>Toàn bộ chuyến theo ngày vận hành Việt Nam.</p><div className="ops-status-track" aria-hidden="true">{Object.keys(tripLabels).map(status=><span key={status} className={`ops-bar-${status}`} style={{flex:trips.filter(t=>t.status===status).length}} />)}</div><ul className="ops-status-legend">{Object.entries(tripLabels).map(([status,label])=><li key={status}><i className={`ops-bar-${status}`} /><span>{label}</span><strong>{trips.filter(t=>t.status===status).length}</strong></li>)}</ul></div><div className="ops-command-next"><span className="eyebrow">{highlight?.status === "BOARDING" ? "ĐANG ĐÓN KHÁCH" : "ƯU TIÊN THEO DÕI"}</span>{highlight ? <><strong className="ops-next-time">{departureClock(highlight.departureTime)}</strong><h2>{highlight.route.name}</h2><p>{highlight.bus.licensePlate} · {tripLabels[highlight.status]}</p><Link className="button" to={`/operator/dispatch?businessDate=${today}&trip=${highlight.id}`}><ClipboardList size={18} /> Mở điều phối chuyến</Link></> : <><h2>{trips.length ? "Không còn chuyến chờ khởi hành" : "Ngày vận hành đang trống"}</h2><p>{trips.length ? "Xem trạng thái thực tế trong lịch bên dưới." : "Chưa có chuyến xe trong ngày đã chọn."}</p><Link className="button" to="/operator/dispatch">Chọn ngày điều phối</Link></>}</div></section>
        <section className="ops-exceptions"><h2><AlertTriangle size={19} /> Cần chú ý</h2>{trips.some(t=>overdue(t)) ? <p className="notice warning">{trips.filter(t=>overdue(t)).length} chuyến đã lên lịch quá giờ dự kiến. Kiểm tra trạng thái thực tế trong lịch vận hành.</p> : <p>Không có chuyến đã lên lịch quá giờ dự kiến trong dữ liệu hiện có.</p>}</section>
        {highlight ? <HighlightTrip key={highlight.id} trip={highlight} upcoming={highlight === upcoming} /> : <p className="notice info">{trips.length ? "Không có chuyến đang đón khách hoặc chờ khởi hành hôm nay." : "Hôm nay chưa có chuyến xe."}</p>}
        <div className="dispatch-list-heading"><h2>Lịch vận hành hôm nay</h2><Link to={"/operator/trips?businessDate="+today}>Xem và lọc chuyến xe</Link></div>
        {!!trips.length && <div className="operator-trip-rows">{ordered.map(t=><article key={t.id} className="operator-trip-row"><strong className="operator-departure">{departureClock(t.departureTime)}</strong><div><Link to={`/operator/trips/${t.id}`}><strong>{t.route.name}</strong></Link><p>{t.bus.licensePlate} · {t.bus.busTypeName}</p></div><div><OperatorStatusBadge status={t.status} />{overdue(t) && <p className="operator-overdue">Quá giờ dự kiến</p>}</div><Link className="button secondary" to={`/operator/dispatch?businessDate=${today}&trip=${t.id}`}>Điều phối</Link></article>)}</div>}
      </>;
    }}</OperationsQueryState>
    {manage && <ManagementOverview query={overview} operational />}
    {manage && <FleetDashboardWarnings />}
    {manage && <ManagementOverview query={overview} />}
    <p className="fine-print">Giờ dự kiến không xác nhận chuyến đã khởi hành.</p>
    {manage && <section className="dispatch-management"><h2>Quản lý</h2><nav className="dispatch-shortcuts" aria-label="Quản lý nhà xe"><Link to="/operator/trips/new">Tạo chuyến</Link><Link to="/operator/buses">Đội xe</Link><Link to="/operator/routes">Tuyến vận hành</Link><Link to="/operator/staff">Nhân sự</Link></nav></section>}
  </div>;
}
