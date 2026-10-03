import { businessDate, departureClock, overdue, tripLabels } from "../../features/operator/dispatch";
import { CrewSection } from "../../features/operator/CrewBoarding";
import { useTripWorkspace } from "../../features/operator/TripWorkspace";
import { RefreshState } from "../../features/operator/RefreshState";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { operatorApi } from "../../api/operatorApi";
import { Field } from "../../components/ui";
import {
  useBusChoices,
  useRouteChoices,
} from "../../features/operator/queries";
import {
  FormErrors,
  OperatorError,
  OperatorPageHeader,
  OperatorStatusBadge,
  OperatorTable,
  Pagination,
  positiveId,
  QueryState,
  tripStatuses,
  useOperatorFilters,
} from "../../features/operator/shared";
import { TripTimeline } from "../../features/operator/TripTimeline";

import { dateTime } from "../../utils/format";

import { useAuth } from "../../features/auth/AuthProvider";
import { canManageOperator } from "../../features/auth/access";
export function OperatorTripsPage() {
  const manage = canManageOperator(useAuth().user?.roles);
  const { params, page, size, set, reset } = useOperatorFilters();
  const routes = useRouteChoices(manage);
  const buses = useBusChoices(manage);
  const status = tripStatuses.find((s) => s === params.get("status"));
  const date = businessDate(params.get("businessDate"));
  const filters = {
    page,
    size,
    businessDate: date,
    status,
    routeId: positiveId(params.get("routeId")),
    busId: positiveId(params.get("busId")),
  };
  const query = useQuery({
    placeholderData: keepPreviousData, refetchInterval: 30_000, retry: 1,
    queryKey: ["operator", "trips", filters],
    queryFn: ({ signal }) => operatorApi.trips(filters, signal),
  });
  return (
    <>
      <OperatorPageHeader title="Chuyến xe">
        {manage && <Link className="button" to="/operator/trips/new">
          Tạo chuyến
        </Link>}
      </OperatorPageHeader>
      <div className="operator-filters">
        <Field
          label="Ngày vận hành (Việt Nam)"
          type="date"
          value={date || ""}
          onChange={(e) => set("businessDate", e.target.value)}
        />
{manage && <>        <label className="field">
          Tuyến
          <select
            value={filters.routeId || ""}
            onChange={(e) => set("routeId", e.target.value)}
          >
            <option value="">Tất cả</option>
            {routes.data?.map((r) => (
              <option value={r.route.id} key={r.id}>
                {r.route.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Xe
          <select
            value={filters.busId || ""}
            onChange={(e) => set("busId", e.target.value)}
          >
            <option value="">Tất cả</option>
            {buses.data?.map((b) => (
              <option value={b.id} key={b.id}>
                {b.licensePlate}
              </option>
            ))}
          </select>
        </label></>}
        {!manage && <><Field label="Mã tuyến" type="number" min={1} value={filters.routeId || ""} onChange={e => set("routeId", e.target.value)} /><Field label="Mã xe" type="number" min={1} value={filters.busId || ""} onChange={e => set("busId", e.target.value)} /></>}
        <label className="field">
          Trạng thái
          <select
            value={status || ""}
            onChange={(e) => set("status", e.target.value)}
          >
            <option value="">Tất cả</option>
            {tripStatuses.map((s) => (
              <option key={s} value={s}>{tripLabels[s]}</option>
            ))}
          </select>
        </label>
        <button className="secondary" onClick={reset}>Xóa bộ lọc</button>
      </div>
      <p className="muted">
        Ngày theo lịch Việt Nam (UTC+7). Giờ hiển thị là giờ dự kiến.
      </p>
      {routes.isError && (
        <OperatorError error={routes.error} retry={() => routes.refetch()} />
      )}
      {buses.isError && (
        <OperatorError error={buses.error} retry={() => buses.refetch()} />
      )}
      <RefreshState query={query} />
      <QueryState query={query}>
        {(result) => (
          <>
            {!result.data.length ? <p className="notice">Không có chuyến phù hợp với bộ lọc.</p> : <div className="operator-trip-rows">{result.data.map(t => <article className="operator-trip-row" key={t.id}>
              <div><strong className="operator-departure">{departureClock(t.departureTime)}</strong><small>{dateTime(t.departureTime)}</small></div>
              <div><Link to={"/operator/trips/" + t.id}><strong>{t.route.name}</strong></Link><p>{t.bus.licensePlate} · {t.bus.busTypeName}</p><small className="muted">Chuyến #{t.id}</small></div>
              <div><OperatorStatusBadge status={t.status} />{overdue(t) && <p className="operator-overdue">Quá giờ dự kiến</p>}</div>
              <Link className="button secondary" to={"/operator/trips/" + t.id}>Xem chuyến</Link>
            </article>)}</div>}
            <Pagination pagination={result.pagination} set={set} />
          </>
        )}
      </QueryState>
    </>
  );
}
const tripSchema = z.object({
  operatorRouteId: z.number().int().positive("Chọn tuyến vận hành."),
  busId: z.number().int().positive("Chọn xe."),
  departureTime: z
    .string()
    .min(1, "Chọn giờ khởi hành.")
    .refine(
      (v) =>
        Number.isFinite(Date.parse(`${v}+07:00`)) &&
        Date.parse(`${v}+07:00`) > Date.now(),
      "Giờ khởi hành phải trong tương lai (UTC+7).",
    ),
});
type TripValues = z.infer<typeof tripSchema>;
export function OperatorTripCreatePage() {
  const routes = useRouteChoices();
  const buses = useBusChoices();
  const cache = useQueryClient();
  const navigate = useNavigate();
  const form = useForm<TripValues>({
    resolver: zodResolver(tripSchema),
    defaultValues: { operatorRouteId: 0, busId: 0, departureTime: "" },
  });
  const mutation = useMutation({
    mutationFn: (v: TripValues) =>
      operatorApi.createTrip({
        ...v,
        departureTime: new Date(`${v.departureTime}+07:00`).toISOString(),
      }),
    onSuccess: async (trip) => {
      await cache.invalidateQueries({ queryKey: ["operator", "trips"] });
      navigate(`/operator/trips/${trip.id}`);
    },
  });
  return (
    <>
      <OperatorPageHeader title="Tạo chuyến">
        <Link to="/operator/trips">Danh sách chuyến</Link>
      </OperatorPageHeader>
      <QueryState query={routes}>
        {(rs) => (
          <QueryState query={buses}>
            {(bs) => {
              const activeRoutes = rs.filter(
                (r) => r.status === "ACTIVE" && r.route.status === "ACTIVE",
              );
              const availableBuses = bs.filter((b) => b.status === "AVAILABLE");
              return (
                <form
                  className="card form-stack operator-form"
                  onSubmit={form.handleSubmit((v) => mutation.mutate(v))}
                >
                  <fieldset disabled={mutation.isPending}>
                    <label className="field">
                      Tuyến vận hành
                      <select
                        {...form.register("operatorRouteId", {
                          valueAsNumber: true,
                        })}
                      >
                        <option value={0}>Chọn tuyến</option>
                        {activeRoutes.map((r) => (
                          <option key={r.id} value={r.id}>
                            {r.route.name}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="field">
                      Xe sẵn sàng
                      <select
                        {...form.register("busId", { valueAsNumber: true })}
                      >
                        <option value={0}>Chọn xe</option>
                        {availableBuses.map((b) => (
                          <option key={b.id} value={b.id}>
                            {b.licensePlate} · {b.busType.name}
                          </option>
                        ))}
                      </select>
                    </label>
                    <Field
                      label="Khởi hành — giờ Việt Nam (UTC+7)"
                      type="datetime-local"
                      step={60}
                      {...form.register("departureTime")}
                    />
                    <FormErrors
                      messages={Object.values(form.formState.errors).map(
                        (e) => e.message,
                      )}
                    />
                    <p>
                      Máy chủ kiểm tra lịch trùng và tạo bản chụp điểm dừng,
                      chặng và ghế. Chuyến đã tạo không thể sửa hoặc hủy từ giao
                      diện này.
                    </p>
                    <button
                      disabled={
                        mutation.isPending ||
                        !activeRoutes.length ||
                        !availableBuses.length
                      }
                    >
                      {mutation.isPending ? "Đang tạo…" : "Tạo chuyến"}
                    </button>
                    {!activeRoutes.length && (
                      <p>
                        Chưa có tuyến hoạt động.{" "}
                        <Link to="/operator/routes/catalog">Đăng ký tuyến</Link>
                      </p>
                    )}
                    {!availableBuses.length && (
                      <p>
                        Chưa có xe sẵn sàng.{" "}
                        <Link to="/operator/buses">Quản lý đội xe</Link>
                      </p>
                    )}
                  </fieldset>
                  {mutation.isError && <OperatorError error={mutation.error} />}
                </form>
              );
            }}
          </QueryState>
        )}
      </QueryState>
    </>
  );
}
export function OperatorTripDetailPage() {
  const t = useTripWorkspace();
  return <>
    <CrewSection tripId={t.id} status={t.status} />
    <section className="card"><h2>Tổng quan chuyến</h2><p>{t.seats.length} ghế · {t.segments.length} chặng</p><p>Dự kiến đến: {dateTime(t.estimatedArrivalTime)}</p><Link to={"/operator/bookings?tripId=" + t.id}>Tra cứu đặt vé của chuyến</Link></section>
    <section className="card"><h2>Điểm dừng theo lịch</h2><TripTimeline stops={t.stops} /></section>
    <section className="card"><h2>Các chặng</h2><OperatorTable headers={["Thứ tự", "Điểm đầu", "Điểm cuối"]} empty={!t.segments.length}>{[...t.segments].sort((a,b)=>a.segmentOrder-b.segmentOrder).map(s => <tr key={s.id}><td>{s.segmentOrder}</td><td>{t.stops.find(x=>x.id===s.fromTripStopId)?.locationName}</td><td>{t.stops.find(x=>x.id===s.toTripStopId)?.locationName}</td></tr>)}</OperatorTable></section>
  </>;
}
