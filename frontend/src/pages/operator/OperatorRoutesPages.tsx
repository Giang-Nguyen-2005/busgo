import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { operatorApi } from "../../api/operatorApi";
import type { ActiveStatus, OperatorRouteResponse } from "../../types/operator";
import {
  Confirm,
  OperatorError,
  OperatorPageHeader,
  OperatorStatusBadge,
  OperatorTable,
  Pagination,
  QueryState,
  useOperatorFilters,
} from "../../features/operator/shared";
import { RouteStops } from "../../features/operator/TripTimeline";
import { FareEditor } from "../../features/operator/FareEditor";
export function OperatorRoutesPage() {
  const { page, size, set } = useOperatorFilters();
  const query = useQuery({
    queryKey: ["operator", "routes", { page, size }],
    queryFn: ({ signal }) => operatorApi.routes({ page, size }, signal),
  });
  return (
    <>
      <OperatorPageHeader title="Tuyến vận hành">
        <Link className="button" to="/operator/routes/catalog">
          Danh mục / đăng ký tuyến
        </Link>
      </OperatorPageHeader>
      <QueryState query={query}>
        {(result) => (
          <>
            <OperatorTable
              headers={[
                "Tuyến",
                "Điểm đầu → cuối",
                "Trạng thái đăng ký",
                "Tuyến gốc",
              ]}
              empty={!result.data.length}
            >
              {result.data.map((r) => (
                <tr key={r.id}>
                  <td>
                    <Link to={`/operator/routes/${r.id}`}>{r.route.name}</Link>
                  </td>
                  <td>
                    {r.route.origin.name} → {r.route.destination.name}
                  </td>
                  <td>
                    <OperatorStatusBadge status={r.status} />
                  </td>
                  <td>
                    <OperatorStatusBadge status={r.route.status} />
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
export function OperatorRouteCatalogPage() {
  const { page, size, set } = useOperatorFilters();
  const cache = useQueryClient();
  const navigate = useNavigate();
  const query = useQuery({
    queryKey: ["operator", "catalog", { page, size }],
    queryFn: ({ signal }) => operatorApi.catalog({ page, size }, signal),
  });
  const attach = useMutation({
    mutationFn: (routeId: number) => operatorApi.attachRoute({ routeId }),
    onSuccess: async (r) => {
      await cache.invalidateQueries({ queryKey: ["operator", "routes"] });
      navigate(`/operator/routes/${r.id}`);
    },
  });
  return (
    <>
      <OperatorPageHeader title="Danh mục tuyến đang hoạt động">
        <Link to="/operator/routes">Tuyến của nhà xe</Link>
      </OperatorPageHeader>
      <p>
        Đăng ký một tuyến có sẵn. Nếu nhà xe đã ngừng tuyến này, thao tác sẽ
        kích hoạt lại đăng ký.
      </p>
      {attach.isError && <OperatorError error={attach.error} />}
      <QueryState query={query}>
        {(result) => (
          <>
            <OperatorTable
              headers={[
                "Tuyến / điểm dừng",
                "Quãng đường",
                "Thời lượng",
                "Đăng ký",
              ]}
              empty={!result.data.length}
            >
              {result.data.map((r) => (
                <tr key={r.id}>
                  <td>
                    <strong>{r.name}</strong>
                    <p>
                      {r.origin.name} → {r.destination.name}
                    </p>
                    <details>
                      <summary>Xem {r.stops.length} điểm dừng</summary>
                      <RouteStops stops={r.stops} />
                    </details>
                  </td>
                  <td>
                    {r.estimatedDistanceKm == null
                      ? "—"
                      : `${r.estimatedDistanceKm} km`}
                  </td>
                  <td>
                    {r.estimatedDurationMinutes == null
                      ? "—"
                      : `${r.estimatedDurationMinutes} phút`}
                  </td>
                  <td>
                    <button
                      disabled={attach.isPending}
                      onClick={() => attach.mutate(r.id)}
                    >
                      {attach.isPending && attach.variables === r.id
                        ? "Đang đăng ký…"
                        : "Đăng ký / kích hoạt lại"}
                    </button>
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
function RouteActivation({ route }: { route: OperatorRouteResponse }) {
  const cache = useQueryClient();
  const [confirm, setConfirm] = useState(false);
  const next: ActiveStatus = route.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
  const mutation = useMutation({
    mutationFn: () => operatorApi.updateRoute(route.id, { status: next }),
    onSuccess: async () => {
      setConfirm(false);
      await cache.invalidateQueries({ queryKey: ["operator", "routes"] });
    },
  });
  return (
    <>
      <p>
        Đăng ký của nhà xe: <OperatorStatusBadge status={route.status} /> ·
        Tuyến gốc: <OperatorStatusBadge status={route.route.status} />
      </p>
      {confirm ? (
        <Confirm
          text="Ngừng đăng ký tuyến này? Nhà xe sẽ không thể tạo chuyến mới trên tuyến cho đến khi kích hoạt lại."
          pending={mutation.isPending}
          confirm={() => mutation.mutate()}
          cancel={() => setConfirm(false)}
        />
      ) : (
        <button
          className="secondary"
          disabled={mutation.isPending}
          onClick={() =>
            next === "INACTIVE" ? setConfirm(true) : mutation.mutate()
          }
        >
          {mutation.isPending
            ? "Đang lưu…"
            : next === "ACTIVE"
              ? "Kích hoạt tuyến"
              : "Ngừng tuyến"}
        </button>
      )}
      {mutation.isError && <OperatorError error={mutation.error} />}
      {mutation.isSuccess && (
        <p role="status">Đã cập nhật trạng thái đăng ký.</p>
      )}
    </>
  );
}
export function OperatorRouteDetailPage() {
  const id = Number(useParams().operatorRouteId);
  const query = useQuery({
    queryKey: ["operator", "routes", id],
    queryFn: ({ signal }) => operatorApi.route(id, signal),
  });
  return (
    <>
      <OperatorPageHeader title="Chi tiết tuyến vận hành">
        <Link to="/operator/routes">Danh sách tuyến</Link>
      </OperatorPageHeader>
      <QueryState query={query}>
        {(r) => (
          <>
            <section className="card">
              <h2>{r.route.name}</h2>
              <p>
                {r.route.origin.name} → {r.route.destination.name}
              </p>
              <p>
                {r.route.estimatedDistanceKm == null
                  ? "Chưa có quãng đường"
                  : `${r.route.estimatedDistanceKm} km`}{" "}
                ·{" "}
                {r.route.estimatedDurationMinutes == null
                  ? "Chưa có thời lượng"
                  : `${r.route.estimatedDurationMinutes} phút`}
              </p>
              <RouteActivation key={r.id} route={r} />
              <h3>Điểm dừng theo thứ tự</h3>
              <RouteStops stops={r.route.stops} />
            </section>
            <FareEditor id={r.id} stops={r.route.stops} />
          </>
        )}
      </QueryState>
    </>
  );
}
