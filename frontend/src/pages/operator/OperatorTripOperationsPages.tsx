import { Link, useParams } from "react-router-dom";
import { Empty } from "../../components/ui";
import { useOccupancy, usePassengers } from "../../features/operator/queries";
import { OperationsBadge, OperationsQueryState } from "../../features/operator/OperationsShared";
import { manifestEmptyText, occupancyMatrix, seatPassenger } from "../../features/operator/operations";
import { OperatorPageHeader, OperatorStatusBadge, OperatorTable } from "../../features/operator/shared";
import { dateTime, paymentStatusLabel } from "../../utils/format";
import type { PassengerManifest, TripOccupancy } from "../../types/operator";

export function ManifestContent({ manifest }: { manifest: PassengerManifest }) {
  return <><p><OperatorStatusBadge status={manifest.tripStatus} /></p>
    <p>Danh sách từ đặt vé đã xác nhận hoặc hoàn thành. Tên trên vé có thể lấy từ liên hệ đặt vé và không xác minh danh tính khách trên ghế.</p>
    {!manifest.passengers.length ? <Empty title={manifestEmptyText} /> :
      <OperatorTable headers={["Ghế", "Đặt vé", "Điểm đón", "Điểm trả", "Khách trên ghế", "Tên trên vé", "Liên hệ đặt vé", "Điện thoại", "Thanh toán", "Mã vé"]}>
        {manifest.passengers.map(p => <tr key={p.bookingItemId}>
          <td>{p.seatCode}</td><td><Link to={`/operator/bookings/${p.bookingId}`}>{p.bookingCode}</Link><p><OperationsBadge status={p.bookingStatus} /></p></td>
          <td>{p.pickup.name}{p.pickup.time && <p>{dateTime(p.pickup.time)}</p>}</td><td>{p.dropoff.name}{p.dropoff.time && <p>{dateTime(p.dropoff.time)}</p>}</td>
          <td>{seatPassenger(p.passengerName)}</td><td>{p.ticketPassengerName || "—"}</td><td>{p.contact.name}</td><td>{p.contact.phone}</td><td>{paymentStatusLabel(p.paymentStatus)}</td><td>{p.ticketCode || "Chưa có vé"}</td>
        </tr>)}
      </OperatorTable>}
  </>;
}
export function OperatorPassengersPage() {
  const id = Number(useParams().tripId);
  const query = usePassengers(id);
  return <><OperatorPageHeader title={`Hành khách · Chuyến #${id}`}><Link to={`/operator/trips/${id}`}>Chi tiết chuyến</Link><Link to={`/operator/trips/${id}/occupancy`}>Tình trạng ghế</Link></OperatorPageHeader>
    <OperationsQueryState query={query}>{data => <ManifestContent manifest={data} />}</OperationsQueryState></>;
}
export function OccupancyContent({ occupancy: data }: { occupancy: TripOccupancy }) {
  const matrix = occupancyMatrix(data);
  return <>
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
              {cells.map((cell, i) => <td key={matrix.segments[i].tripSegmentId}>{cell ? <><OperationsBadge status={cell.status} />
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
  return <><OperatorPageHeader title={`Tình trạng ghế · Chuyến #${id}`}><Link to={`/operator/trips/${id}`}>Chi tiết chuyến</Link><Link to={`/operator/trips/${id}/passengers`}>Hành khách</Link></OperatorPageHeader>
    <OperationsQueryState query={query}>{data => <OccupancyContent occupancy={data} />}</OperationsQueryState></>;
}
