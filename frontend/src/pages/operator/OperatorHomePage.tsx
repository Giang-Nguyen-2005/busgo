import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { allPages, operatorApi } from "../../api/operatorApi";
import { useAuth } from "../../features/auth/AuthProvider";
import { canManageOperator } from "../../features/auth/access";
import { OperatorPageHeader, OperatorStatusBadge } from "../../features/operator/shared";
import { OperationsQueryState } from "../../features/operator/OperationsShared";
import { RefreshState } from "../../features/operator/RefreshState";
import { useOccupancy } from "../../features/operator/queries";
import { departureClock, dispatchOrder, overdue, vietnamToday } from "../../features/operator/dispatch";
import type { TripSummaryResponse } from "../../types/operator";
import { date, dateTime } from "../../utils/format";
import { ManagementOverview, useManagementOverview } from "../../features/operator/Reports";
import { FleetDashboardWarnings } from "../../features/operator/FleetMaintenance";

function TripShortcuts({ id }: { id: number }) {
  return <nav className="dispatch-shortcuts" aria-label="Công cụ chuyến xe">
    <Link className="button" to={`/operator/trips/${id}/seats`}>Sơ đồ ghế</Link>
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
      <OperationsQueryState query={occupancy}>{data => <>
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
  return <div className="operator-dashboard"><OperatorPageHeader title="Điều hành"><Link className="button secondary" to="/operator/bookings">Tra cứu đặt vé</Link></OperatorPageHeader>
    <p className="muted">Ngày vận hành · {date(today)} · Giờ Việt Nam</p><RefreshState query={query} />
    {manage && <ManagementOverview query={overview} operational />}
    <OperationsQueryState query={query}>{trips => {
      const ordered = dispatchOrder(trips);
      const upcoming = ordered.filter(t => t.status === "SCHEDULED" && Date.parse(t.departureTime) >= Date.now()).sort((a,b) => Date.parse(a.departureTime)-Date.parse(b.departureTime))[0];
      const highlight = ordered.find(t => t.status === "BOARDING") || upcoming || ordered.find(t => t.status === "SCHEDULED");
      return <><dl className="dispatch-totals">{[["Chuyến hôm nay",trips.length],["Đang đón khách",trips.filter(t=>t.status==="BOARDING").length],["Đã lên lịch",trips.filter(t=>t.status==="SCHEDULED").length],["Đang chạy",trips.filter(t=>t.status==="DEPARTED").length]].map(([label,count])=><div key={label}><dt>{label}</dt><dd>{count}</dd></div>)}</dl>
        {highlight ? <HighlightTrip key={highlight.id} trip={highlight} upcoming={highlight === upcoming} /> : <p className="notice info">{trips.length ? "Không có chuyến đang đón khách hoặc chờ khởi hành hôm nay." : "Hôm nay chưa có chuyến xe."}</p>}
        <div className="dispatch-list-heading"><h2>Lịch vận hành hôm nay</h2><Link to={"/operator/trips?businessDate="+today}>Xem và lọc chuyến xe</Link></div>
        {!!trips.length && <div className="operator-trip-rows">{ordered.map(t=><article key={t.id} className="operator-trip-row"><strong className="operator-departure">{departureClock(t.departureTime)}</strong><div><Link to={`/operator/trips/${t.id}`}><strong>{t.route.name}</strong></Link><p>{t.bus.licensePlate} · {t.bus.busTypeName}</p></div><div><OperatorStatusBadge status={t.status} />{overdue(t) && <p className="operator-overdue">Quá giờ dự kiến</p>}</div><Link className="button secondary" to={`/operator/trips/${t.id}/seats`}>Sơ đồ ghế</Link></article>)}</div>}
      </>;
    }}</OperationsQueryState>
    {manage && <FleetDashboardWarnings />}
    {manage && <ManagementOverview query={overview} />}
    <p className="fine-print">Giờ dự kiến không xác nhận chuyến đã khởi hành.</p>
    {manage && <section className="dispatch-management"><h2>Quản lý</h2><nav className="dispatch-shortcuts" aria-label="Quản lý nhà xe"><Link to="/operator/trips/new">Tạo chuyến</Link><Link to="/operator/buses">Đội xe</Link><Link to="/operator/routes">Tuyến vận hành</Link><Link to="/operator/staff">Nhân sự</Link></nav></section>}
  </div>;
}
