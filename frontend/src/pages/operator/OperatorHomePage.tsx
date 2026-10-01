import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { allPages, operatorApi } from "../../api/operatorApi";
import { useAuth } from "../../features/auth/AuthProvider";
import { canManageOperator } from "../../features/auth/access";
import { OperatorPageHeader, OperatorStatusBadge } from "../../features/operator/shared";
import { OperationsQueryState } from "../../features/operator/OperationsShared";
import { RefreshState } from "../../features/operator/RefreshState";
import { departureClock, dispatchOrder, overdue, vietnamToday } from "../../features/operator/dispatch";
export function OperatorHomePage() {
  const manage = canManageOperator(useAuth().user?.roles);
  const today = vietnamToday();
  const query = useQuery({ queryKey: ["operator", "trips", "today", today], queryFn: ({ signal }) => allPages(p => operatorApi.trips({ ...p, businessDate: today }, signal)), refetchInterval: 60_000, retry: 1 });
  return <><OperatorPageHeader title="Điều hành"><Link className="button secondary" to="/operator/bookings">Tra cứu đặt vé</Link></OperatorPageHeader>
    <h2>Chuyến hôm nay · {today}</h2><RefreshState query={query} />
    <OperationsQueryState query={query}>{trips => !trips.length ? <p className="notice">Hôm nay chưa có chuyến xe.</p> : <div className="operator-trip-rows">{dispatchOrder(trips).map(t => <article key={t.id} className="operator-trip-row">
      <strong className="operator-departure">{departureClock(t.departureTime)}</strong><div><Link to={"/operator/trips/" + t.id}><strong>{t.route.name}</strong></Link><p>{t.bus.licensePlate} · {t.bus.busTypeName}</p></div>
      <div><OperatorStatusBadge status={t.status} />{overdue(t) && <p className="operator-overdue">Quá giờ dự kiến</p>}</div><Link className="button secondary" to={"/operator/trips/" + t.id + "/seats"}>Sơ đồ ghế</Link>
    </article>)}</div>}</OperationsQueryState>
    <p className="muted">Giờ Việt Nam · Giờ dự kiến không xác nhận chuyến đã khởi hành.</p>
    <Link to={"/operator/trips?businessDate=" + today}>Xem và lọc chuyến xe</Link>
    {manage && <section className="card"><h2>Quản lý</h2><div className="operator-actions"><Link to="/operator/trips/new">Tạo chuyến</Link><Link to="/operator/buses">Đội xe</Link><Link to="/operator/routes">Tuyến vận hành</Link><Link to="/operator/staff">Nhân sự</Link></div></section>}
  </>;
}
