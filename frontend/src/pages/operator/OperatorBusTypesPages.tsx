import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { operatorApi } from "../../api/operatorApi";
import { useBusTypes } from "../../features/operator/queries";
import {
  OperatorPageHeader,
  OperatorStatusBadge,
  OperatorTable,
  QueryState,
} from "../../features/operator/shared";
import { SeatLayoutPreview } from "../../features/operator/SeatLayoutPreview";
export function OperatorBusTypesPage() {
  const query = useBusTypes();
  return (
    <>
      <OperatorPageHeader title="Loại xe" />
      <p>Danh mục chỉ đọc. Mẫu ghế được quản lý bên ngoài khu vực nhà xe.</p>
      <QueryState query={query}>
        {(rows) => (
          <OperatorTable
            headers={["Loại xe", "Số ghế", "Trạng thái"]}
            empty={!rows.length}
          >
            {rows.map((b) => (
              <tr key={b.id}>
                <td>
                  <Link to={`/operator/bus-types/${b.id}`}>{b.name}</Link>
                </td>
                <td>{b.seatCount}</td>
                <td>
                  <OperatorStatusBadge status={b.status} />
                </td>
              </tr>
            ))}
          </OperatorTable>
        )}
      </QueryState>
    </>
  );
}
export function OperatorBusTypeDetailPage() {
  const id = Number(useParams().busTypeId);
  const query = useQuery({
    queryKey: ["operator", "bus-types", id],
    queryFn: ({ signal }) => operatorApi.busType(id, signal),
  });
  return (
    <>
      <OperatorPageHeader title="Chi tiết loại xe">
        <Link to="/operator/bus-types">Danh sách loại xe</Link>
      </OperatorPageHeader>
      <QueryState query={query}>
        {(b) => (
          <section className="card">
            <h2>{b.name}</h2>
            <p>{b.description}</p>
            <p>
              {b.seatCount} ghế · <OperatorStatusBadge status={b.status} />
            </p>
            <h2>Mẫu sơ đồ ghế (chỉ đọc)</h2>
            <SeatLayoutPreview seats={b.seats} />
          </section>
        )}
      </QueryState>
    </>
  );
}
