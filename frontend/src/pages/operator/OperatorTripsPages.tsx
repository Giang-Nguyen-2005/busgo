import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
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
import { SeatLayoutPreview } from "../../features/operator/SeatLayoutPreview";
import { dateTime } from "../../utils/format";
export function OperatorTripsPage() {
  const { params, page, size, set } = useOperatorFilters();
  const routes = useRouteChoices();
  const buses = useBusChoices();
  const status = tripStatuses.find((s) => s === params.get("status"));
  const rawDate = params.get("date") || "";
  const date = /^\d{4}-\d{2}-\d{2}$/.test(rawDate) ? rawDate : undefined;
  const filters = {
    page,
    size,
    date,
    status,
    routeId: positiveId(params.get("routeId")),
    busId: positiveId(params.get("busId")),
  };
  const query = useQuery({
    queryKey: ["operator", "trips", filters],
    queryFn: ({ signal }) => operatorApi.trips(filters, signal),
  });
  return (
    <>
      <OperatorPageHeader title="Chuyến xe">
        <Link className="button" to="/operator/trips/new">
          Tạo chuyến
        </Link>
      </OperatorPageHeader>
      <div className="operator-filters">
        <Field
          label="Ngày khởi hành (UTC)"
          type="date"
          value={date || ""}
          onChange={(e) => set("date", e.target.value)}
        />
        <label className="field">
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
        </label>
        <label className="field">
          Trạng thái
          <select
            value={status || ""}
            onChange={(e) => set("status", e.target.value)}
          >
            <option value="">Tất cả</option>
            {tripStatuses.map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </label>
      </div>
      <p className="muted">
        Bộ lọc ngày theo UTC của máy chủ. Giờ hiển thị theo Việt Nam (UTC+7).
      </p>
      {routes.isError && (
        <OperatorError error={routes.error} retry={() => routes.refetch()} />
      )}
      {buses.isError && (
        <OperatorError error={buses.error} retry={() => buses.refetch()} />
      )}
      <QueryState query={query}>
        {(result) => (
          <>
            <OperatorTable
              headers={[
                "Chuyến / tuyến",
                "Xe",
                "Khởi hành",
                "Dự kiến đến",
                "Trạng thái",
              ]}
              empty={!result.data.length}
            >
              {result.data.map((t) => (
                <tr key={t.id}>
                  <td>
                    <Link to={`/operator/trips/${t.id}`}>
                      #{t.id} · {t.route.name}
                    </Link>
                  </td>
                  <td>{t.bus.licensePlate}</td>
                  <td>{dateTime(t.departureTime)}</td>
                  <td>{dateTime(t.estimatedArrivalTime)}</td>
                  <td>
                    <OperatorStatusBadge status={t.status} />
                  </td>
                </tr>
              ))}
            </OperatorTable>
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
  const id = Number(useParams().tripId);
  const query = useQuery({
    queryKey: ["operator", "trips", id],
    queryFn: ({ signal }) => operatorApi.trip(id, signal),
  });
  return (
    <>
      <OperatorPageHeader title={`Chi tiết chuyến #${id}`}>
        <Link to="/operator/trips">Danh sách chuyến</Link>
      </OperatorPageHeader>
      <QueryState query={query}>
        {(t) => (
          <>
            <section className="card">
              <h2>{t.route.name}</h2>
              <p>
                <OperatorStatusBadge status={t.status} /> ·{" "}
                <Link to={`/operator/buses/${t.bus.id}`}>
                  {t.bus.licensePlate}
                </Link>{" "}
                · {t.bus.busTypeName}
              </p>
              <p>
                {dateTime(t.departureTime)} → {dateTime(t.estimatedArrivalTime)}{" "}
                (giờ Việt Nam)
              </p>
              <p>
                {t.seats.length} ghế · {t.segments.length} chặng
              </p>
            </section>
            <section className="card">
              <h2>Điểm dừng theo lịch</h2>
              <TripTimeline stops={t.stops} />
            </section>
            <section className="card">
              <h2>Các chặng</h2>
              <OperatorTable
                headers={["Thứ tự", "Điểm đầu", "Điểm cuối"]}
                empty={!t.segments.length}
              >
                {[...t.segments]
                  .sort((a, b) => a.segmentOrder - b.segmentOrder)
                  .map((s) => (
                    <tr key={s.id}>
                      <td>{s.segmentOrder}</td>
                      <td>
                        {t.stops.find((stop) => stop.id === s.fromTripStopId)
                          ?.locationName || `#${s.fromTripStopId}`}
                      </td>
                      <td>
                        {t.stops.find((stop) => stop.id === s.toTripStopId)
                          ?.locationName || `#${s.toTripStopId}`}
                      </td>
                    </tr>
                  ))}
              </OperatorTable>
            </section>
            <section className="card">
              <h2>Sơ đồ ghế tại lúc tạo chuyến</h2>
              <p className="notice">
                Đây là bản chụp sơ đồ ghế, không phải tình trạng ghế trống hoặc
                đã đặt. Không có thông tin hành khách hay đặt vé.
              </p>
              <SeatLayoutPreview seats={t.seats} />
            </section>
          </>
        )}
      </QueryState>
    </>
  );
}
