import { cellLabels, cellStatus } from "../../features/operator/dispatch";
import { useState } from "react";
import { manifestGroups } from "../../features/operator/dispatch";
import { RefreshState } from "../../features/operator/RefreshState";
import { CompletenessWarning } from "./OperatorSeatsPage";
import { Link, useParams } from "react-router-dom";
import { Empty } from "../../components/ui";
import { useOccupancy, usePassengers } from "../../features/operator/queries";
import { OperationsBadge, OperationsQueryState } from "../../features/operator/OperationsShared";
import { manifestEmptyText, occupancyMatrix, seatPassenger } from "../../features/operator/operations";
import { OperatorStatusBadge } from "../../features/operator/shared";
import { dateTime, paymentStatusLabel } from "../../utils/format";
import type { PassengerManifest, TripOccupancy } from "../../types/operator";

export function ManifestContent({ manifest }: { manifest: PassengerManifest }) {
  const [search, setSearch] = useState("");
  const groups = manifestGroups(manifest.passengers, search);
  return <div className="manifest-cleanup"><div className="manifest-toolbar"><OperatorStatusBadge status={manifest.tripStatus} /><span>{manifest.passengers.length} dòng hành khách</span></div>
    <p className="fine-print">Liên hệ đặt vé không xác minh danh tính khách trên ghế. Tên trên vé có thể lấy từ liên hệ đặt vé.</p>
    <label className="field operator-manifest-search">Tìm ghế, mã đặt vé, khách trên ghế, tên trên vé, liên hệ hoặc điện thoại<input value={search} onChange={e => setSearch(e.target.value)} type="search" /></label>
    {!manifest.passengers.length ? <Empty title={manifestEmptyText} /> : !groups.length ? <Empty title="Không có hành khách phù hợp tìm kiếm" /> : groups.map(group => <section className="manifest-group" key={group.key}>
      <h3>{group.passengers[0].pickup.name} → {group.passengers[0].dropoff.name}</h3>
      <div className="operator-manifest-list">{group.passengers.map(p => <article className="operator-passenger" key={p.bookingItemId}>
        <div className="manifest-seat"><strong className="operator-seat-code">{p.seatCode}</strong></div>
        <div className="manifest-identity"><small>{p.passengerName ? "Khách trên ghế" : "Liên hệ đặt vé"}</small><strong>{p.passengerName || p.contact.name}</strong><p>{p.contact.phone} <small>Điện thoại liên hệ</small></p>{!p.passengerName && <small className="muted">Khách trên ghế: {seatPassenger(p.passengerName)}</small>}<details className="row-secondary"><summary>Đặt vé & thông tin vé</summary><Link to={"/operator/bookings/" + p.bookingId}>{p.bookingCode}</Link><p>Liên hệ đặt vé: {p.contact.name}</p><p>Mã vé: {p.ticketCode || "Chưa có vé"}</p><p>Tên trên vé: {p.ticketPassengerName || "—"}</p></details></div>
        <div className="manifest-journey"><p><small>Đón</small>{p.pickup.name}{p.pickup.time && <small>{dateTime(p.pickup.time)}</small>}</p><p><small>Trả</small>{p.dropoff.name}{p.dropoff.time && <small>{dateTime(p.dropoff.time)}</small>}</p></div>
        <div className="manifest-state"><OperationsBadge status={p.bookingStatus} /><span className={`operator-badge operator-status-${p.paymentStatus}`}>{paymentStatusLabel(p.paymentStatus)}</span></div>
      </article>)}</div>
    </section>)}
  </div>;
}

export function OperatorPassengersPage() {
  const id = Number(useParams().tripId);
  const query = usePassengers(id);
  return <><h2>Hành khách</h2><RefreshState query={query} />
    <OperationsQueryState query={query}>{data => <ManifestContent manifest={data} />}</OperationsQueryState></>;
}
export function OccupancyContent({ occupancy: data }: { occupancy: TripOccupancy }) {
  const matrix = occupancyMatrix(data);
  const counts = Object.entries(cellLabels).map(([status, label]) => ({ status, label, count: matrix.rows.reduce((sum, row) => sum + row.cells.filter(c => cellStatus(c) === status).length, 0) }));
  return <><CompletenessWarning data={data} />
    <div className="operator-occupancy-counts" aria-label="Tổng ô ghế theo chặng">{counts.map(c => <span className={`operator-badge operator-status-${c.status}`} key={c.status}>{c.label}: <strong>{c.count}</strong></span>)}</div>
    <p className="notice operator-occupancy-note">{data.seatCount} ghế · {data.segmentCount} chặng · {data.wholeTripAvailableSeatCount} ghế trống suốt chuyến. “Đã đặt” là tồn kho dành cho đặt vé, chưa chắc đã thanh toán hoặc lên xe. {matrix.segments.length > 1 && "Một ghế có thể trống ở chặng khác; xem từng ô theo chặng."}</p>
    <section className={`card operator-matrix-panel ${matrix.segments.length === 1 ? "operator-single-segment" : ""}`}><h2>{matrix.segments.length === 1 ? "Ghế · một chặng" : "Ghế × chặng"}</h2><p className="muted">{matrix.segments.length === 1 ? "Tình trạng từng ghế trên chặng duy nhất." : "Cuộn trong bảng để xem ghế và chặng."} Thời gian theo Việt Nam (UTC+7).</p>
      {!matrix.rows.length || !matrix.segments.length ? <Empty title="Chưa có dữ liệu ghế theo chặng" /> :
        <div className="operator-table-scroll operator-occupancy" tabIndex={0} role="region" aria-label="Tình trạng ghế theo từng chặng">
          <table className="operator-table"><thead><tr><th scope="col">Ghế</th>{matrix.segments.map(s => <th scope="col" key={s.tripSegmentId}>{s.segmentOrder}. {s.fromName} → {s.toName}</th>)}</tr></thead>
            <tbody>{matrix.rows.map(({ seat, cells }) => <tr key={seat.tripSeatId}><th scope="row">{seat.seatCode}<p>Tầng {seat.floor}</p></th>
              {cells.map((cell, i) => <td className={`operator-cell operator-cell-${cellStatus(cell)}`} key={matrix.segments[i].tripSegmentId}>{cell && !cell.missing && cell.status ? <><OperationsBadge status={cell.status} />
                {cell.status === "BOOKED" && cell.bookingId && <p><Link to={`/operator/bookings/${cell.bookingId}`}>{cell.bookingCode || `Đặt vé #${cell.bookingId}`}</Link></p>}
                {cell.status === "HELD" && cell.holdExpiresAt && <p>Hết hạn: {dateTime(cell.holdExpiresAt)}</p>}</> : <OperationsBadge status="MISSING" />}</td>)}
            </tr>)}</tbody>
          </table></div>}
    </section>
  </>;
}
export function OperatorOccupancyPage() {
  const id = Number(useParams().tripId);
  const query = useOccupancy(id);
  return <><h2>Tình trạng chặng</h2><RefreshState query={query} />
    <OperationsQueryState query={query}>{data => <OccupancyContent occupancy={data} />}</OperationsQueryState></>;
}
