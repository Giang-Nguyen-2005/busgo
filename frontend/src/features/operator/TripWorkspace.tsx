import { useAuth } from "../auth/AuthProvider";
import { canManageOperator } from "../auth/access";
import { useOccupancy } from "./queries";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, NavLink, Outlet, useOutletContext, useParams } from "react-router-dom";
import { operatorApi } from "../../api/operatorApi";
import { operationsApi } from "../../api/operationsApi";
import type { TripDetailResponse } from "../../types/operator";
import { dateTime } from "../../utils/format";
import { departureClock, tripTabs } from "./dispatch";
import { OperationsQueryState } from "./OperationsShared";
import { OperatorStatusBadge } from "./shared";
import { TripStatusAction } from "./TripStatusAction";
import { RefreshState } from "./RefreshState";
export const useTripWorkspace = () => useOutletContext<TripDetailResponse>();
export function TripWorkspace() {
  const auth = useAuth();
  const id = Number(useParams().tripId);
  const cache = useQueryClient();
  const occupancy = useOccupancy(id);
  const crew = useQuery({ queryKey: ["operator", "crew", id], queryFn: () => operationsApi.crew(id), refetchInterval: 30_000, retry: 1 });
  const query = useQuery({ queryKey: ["operator", "trips", id], queryFn: ({ signal }) => operatorApi.trip(id, signal), refetchInterval: 30_000, retry: 1 });
  return <OperationsQueryState query={query}>{trip => <>
    <section className="operator-trip-header card">
      <Link to="/operator/trips">← Chuyến xe</Link>
      <div className="operator-heading"><div><strong className="operator-departure">{departureClock(trip.departureTime)}</strong><h1>{trip.route.name}</h1>
        <p>{dateTime(trip.departureTime)} · Giờ Việt Nam</p><p><strong>{trip.bus.licensePlate}</strong> · {trip.bus.busTypeName} · <OperatorStatusBadge status={trip.status} /></p></div>
        <div>{canManageOperator(auth.user?.roles) && trip.status === "SCHEDULED" && <Link className="button" to={`/operator/bookings/new?tripId=${id}`}>Đặt chỗ cho khách</Link>}<TripStatusAction key={id} id={id} status={trip.status} crewReady={crew.data?.ready} /></div></div>
      {occupancy.data && !occupancy.isError && <p className="operator-workspace-counts"><strong>{occupancy.data.wholeTripAvailableSeatCount} ghế trống suốt chuyến</strong> · {occupancy.data.seats.filter(s => s.segments.some(c => !c.missing && c.status === "BOOKED")).length} ghế có đặt vé · {occupancy.data.seatCount} ghế{!occupancy.data.complete && " · Tồn kho chưa đầy đủ"}</p>}
      <RefreshState query={query} onRefresh={() => { void cache.invalidateQueries({ queryKey: ["operator", "trips", id] }); }} />
      <nav className="operator-tabs" aria-label="Không gian chuyến xe">{tripTabs.map(([path, label]) => <NavLink end key={path} to={`/operator/trips/${id}${path ? `/${path}` : ""}`}>{label}</NavLink>)}</nav>
    </section>
    <Outlet context={trip} />
  </>}</OperationsQueryState>;
}
