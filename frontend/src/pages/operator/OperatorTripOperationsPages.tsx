import { useState } from "react";
import { manifestGroups } from "../../features/operator/dispatch";
import { RefreshState } from "../../features/operator/RefreshState";
import { CompletenessWarning, SeatLegend } from "./OperatorSeatsPage";
import { Link, useParams } from "react-router-dom";
import { Empty } from "../../components/ui";
import { useOccupancy, usePassengers } from "../../features/operator/queries";
import { OperationsBadge, OperationsQueryState } from "../../features/operator/OperationsShared";
import { manifestEmptyText, occupancyMatrix, seatPassenger } from "../../features/operator/operations";
import { OperatorStatusBadge, OperatorTable } from "../../features/operator/shared";
import { dateTime, paymentStatusLabel } from "../../utils/format";
import type { PassengerManifest, TripOccupancy } from "../../types/operator";

export function ManifestContent({ manifest }: { manifest: PassengerManifest }) {
  const [search, setSearch] = useState("");
  const groups = manifestGroups(manifest.passengers, search);
  return <><p><OperatorStatusBadge status={manifest.tripStatus} /></p>
    <p>Tên trên vé có thể lấy từ liên hệ đặt vé và không xác minh danh tính khách trên ghế.</p>
    <label className="field">Tìm ghế, mã đặt vé, khách trên ghế, tên trên vé, liên hệ hoặc điện thoại<input value={search} onChange={e => setSearch(e.target.value)} type="search" /></label>
    {!manifest.passengers.length ? <Empty title={manifestEmptyText} /> : !groups.length ? <Empty title="Không có hành khách phù hợp tìm kiếm" /> : groups.map(group => <section className="card" key={group.key}>
      <h3>{group.passengers[0].pickup.name} → {group.passengers[0].dropoff.name}</h3>
      <div className="operator-manifest-list">{group.passengers.map(p => <article className="operator-passenger" key={p.bookingItemId}>
        <div><strong className="operator-seat-code">{p.seatCode}</strong><Link to={"/operator/bookings/" + p.bookingId}>{p.bookingCode}</Link><p><OperationsBadge status={p.bookingStatus} /></p></div>
        <dl><dt>Khách trên ghế</dt><dd>{seatPassenger(p.passengerName)}</dd><dt>Tên trên vé</dt><dd>{p.ticketPassengerName || "—"}</dd><dt>Mã vé</dt><dd>{p.ticketCode || "Chưa có vé"}</dd></dl>
        <dl><dt>Liên hệ đặt vé</dt><dd>{p.contact.name}</dd><dt>Điện thoại</dt><dd>{p.contact.phone}</dd><dt>Thanh toán</dt><dd>{paymentStatusLabel(p.paymentStatus)}</dd></dl>
        <div><p>Đón: {p.pickup.name}{p.pickup.time && <small>{dateTime(p.pickup.time)}</small>}</p><p>Trả: {p.dropoff.name}{p.dropoff.time && <small>{dateTime(p.dropoff.time)}</small>}</p></div>
      </article>)}</div>
    </section>)}
  </>;
}
export function OperatorPassengersPage() {
  const id = Number(useParams().tripId);
  const query = usePassengers(id);
  return <><h2>Hành khách</h2><RefreshState query={query} />
    <OperationsQueryState query={query}>{data => <ManifestContent manifest={data} />}</OperationsQueryState></>;
}
export function OccupancyContent({ occupancy: data }: { occupancy: TripOccupancy }) {
  const matrix = occupancyMatrix(data);
  return <><CompletenessWarning data={data} /><SeatLegend />
    <section className="card"><h2>Tổng quan</h2><OperatorStatusBadge status={data.tripStatus} />
      <p>{data.seatCount} ghế · {data.segmentCount} chặng · {data.wholeTripAvailableSeatCount} ghế trống suốt chuyến</p>
      <p className="notice">“Đã đặt” nghĩa là tồn kho ghế được dành cho một đặt vé, chưa chắc đã thanh toán hoặc lên xe. Tình trạng từng chặng là thông tin chính xác; một ghế có thể trống ở chặng khác.</p>
    </section>
    <section className="card"><h2>Tổng hợp theo chặng</h2>
      <OperatorTable headers={["Chặng", "Còn trống", "Đang giữ chỗ", "Đã đặt", "Đã khóa"]} empty={!matrix.segments.length}>
        {matrix.segments.map(s => <tr key={s.tripSegmentId}><td>{s.segmentOrder}. {s.fromName} → {s.toName}</td><td>{s.counts.available}</td><td>{s.counts.held}</td><td>{s.counts.booked}</td><td>{s.counts.blocked}</td></tr>)}
      </OperatorTable></section>
    <section className="card"><h2>Ghế × chặng</h2><p className="muted">Cuộn ngang để xem các chặng. Thời gian theo Việt Nam (UTC+7).</p>
      {!matrix.rows.length || !matrix.segments.length ? <Empty title="Chưa có dữ liệu ghế theo chặng" /> :
        <div className="operator-table-scroll operator-occupancy" tabIndex={0} role="region" aria-label="Tình trạng ghế theo từng chặng">
          <table className="operator-table"><thead><tr><th scope="col">Ghế</th>{matrix.segments.map(s => <th scope="col" key={s.tripSegmentId}>{s.segmentOrder}. {s.fromName} → {s.toName}</th>)}</tr></thead>
            <tbody>{matrix.rows.map(({ seat, cells }) => <tr key={seat.tripSeatId}><th scope="row">{seat.seatCode}<p>Tầng {seat.floor}</p></th>
              {cells.map((cell, i) => <td key={matrix.segments[i].tripSegmentId}>{cell && !cell.missing && cell.status ? <><OperationsBadge status={cell.status} />
                {cell.status === "BOOKED" && cell.bookingId && <p><Link to={`/operator/bookings/${cell.bookingId}`}>{cell.bookingCode || `Đặt vé #${cell.bookingId}`}</Link></p>}
                {cell.status === "HELD" && cell.holdExpiresAt && <p>Hết hạn: {dateTime(cell.holdExpiresAt)}</p>}</> : "Chưa có dữ liệu"}</td>)}
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
