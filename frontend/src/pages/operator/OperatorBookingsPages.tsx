import { useEffect, useState } from "react";
import { RefreshState } from "../../features/operator/RefreshState";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { Field } from "../../components/ui";
import { useOperatorBooking, useOperatorBookings } from "../../features/operator/queries";
import { OperationsBadge, OperationsQueryState } from "../../features/operator/OperationsShared";
import { bookingFilters, bookingStatuses, operationLabel, paymentStatuses, seatPassenger, updateBookingFilter } from "../../features/operator/operations";
import { OperatorPageHeader, OperatorStatusBadge, OperatorTable, Pagination } from "../../features/operator/shared";
import { dateTime, money, paymentMethodLabel, paymentStatusLabel } from "../../utils/format";
import type { BookingStop, OperatorBookingDetail } from "../../types/operator";

function Stop({ stop }: { stop: BookingStop }) {
  return <>{stop.name}{stop.time && <span className="operator-stop-time muted">{dateTime(stop.time)}</span>}</>;
}
export function OperatorBookingsPage() {
  const [params, setParams] = useSearchParams();
  const filters = bookingFilters(params);
  const set = (key: string, value: string) => setParams(previous => updateBookingFilter(previous, key, value), { replace: key === "q" });
  const [search, setSearch] = useState(filters.q);
  useEffect(() => { const timer = window.setTimeout(() => setSearch(filters.q), 350); return () => window.clearTimeout(timer); }, [filters.q]);
  const query = useOperatorBookings({ ...filters, q: search });
  return <>
    <OperatorPageHeader title="Đặt vé" />
    <div className="operator-filters">
      <Field label="Mã đặt vé / tên / điện thoại / email" maxLength={100} value={params.get("q") || ""} onChange={e => set("q", e.target.value)} />
      <Field label="Mã chuyến" type="number" min={1} value={params.get("tripId") || ""} onChange={e => set("tripId", e.target.value)} />
      <label className="field">Trạng thái đặt vé<select value={filters.status || ""} onChange={e => set("status", e.target.value)}><option value="">Tất cả</option>{bookingStatuses.map(s => <option key={s} value={s}>{operationLabel(s)}</option>)}</select></label>
      <label className="field">Thanh toán<select value={filters.paymentStatus || ""} onChange={e => set("paymentStatus", e.target.value)}><option value="">Tất cả</option>{paymentStatuses.map(s => <option key={s} value={s}>{paymentStatusLabel(s)}</option>)}</select></label>
      <Field label="Ngày tạo (Việt Nam)" type="date" value={filters.date || ""} onChange={e => set("date", e.target.value)} />
      <button className="secondary" onClick={() => setParams({})}>Xóa bộ lọc</button>
    </div>
    <p className="muted">Ngày tạo và giờ hiển thị theo Việt Nam (UTC+7).</p>
    <RefreshState query={query} /><div className="operator-booking-results">
    <OperationsQueryState query={query}>{result => <>
      <OperatorTable headers={["Mã đặt vé / tuyến", "Liên hệ đặt vé", "Điểm đón → trả", "Số ghế", "Đặt vé", "Thanh toán", "Tổng tiền", "Ngày tạo"]} empty={!result.data.length} emptyTitle="Không có đặt vé phù hợp với bộ lọc">
        {result.data.map(b => <tr key={b.bookingId}>
          <td><Link to={`/operator/bookings/${b.bookingId}`}>{b.bookingCode}</Link><p>{b.route.name}</p><Link to={`/operator/trips/${b.tripId}`}>Chuyến #{b.tripId}</Link></td>
          <td data-label="Liên hệ đặt vé"><strong>{b.contact.name}</strong><p>{b.contact.phone}</p><small>{b.contact.email}</small></td><td data-label="Điểm đón → trả"><Stop stop={b.pickup} /> → <Stop stop={b.dropoff} /></td><td data-label="Số ghế">{b.seatCount}</td>
          <td data-label="Đặt vé"><OperationsBadge status={b.status} /></td><td data-label="Thanh toán">{paymentStatusLabel(b.paymentStatus)}</td><td data-label="Tổng tiền">{money(b.totalAmount)}</td><td data-label="Ngày tạo">{dateTime(b.createdAt)}</td>
        </tr>)}
      </OperatorTable><Pagination pagination={result.pagination} set={set} />
    </>}</OperationsQueryState></div>
  </>;
}
export function BookingDetailContent({ booking: b }: { booking: OperatorBookingDetail }) {
  const latest = b.payments.reduce<(typeof b.payments)[number] | undefined>((a, p) => !a || p.id > a.id ? p : a, undefined);
  return <>
    <section className="card"><h2>{b.bookingCode}</h2><OperationsBadge status={b.status} />
      <p>Thanh toán gần nhất: {latest ? paymentStatusLabel(latest.status) : "Chưa có giao dịch thanh toán"}</p>
      <p>{b.route.name} · <Link to={`/operator/trips/${b.trip.id}`}>Chuyến #{b.trip.id}</Link> · <OperatorStatusBadge status={b.trip.status} /></p>
      <p>{dateTime(b.trip.departureTime)} → {dateTime(b.trip.estimatedArrivalTime)}</p>
      <p>Đón: <Stop stop={b.pickup} /> → Trả: <Stop stop={b.dropoff} /></p>
      <p>Tổng: {money(b.totalAmount)}</p>
      <p className="muted">Giờ Việt Nam (UTC+7)</p>
    </section>
    <section className="card"><h2>Liên hệ đặt vé</h2><p>{b.contact.name}</p><p>{b.contact.phone}</p><p>{b.contact.email || "Chưa có email"}</p></section>
    <section className="card"><h2>Ghế và vé</h2><p>Tên trên vé có thể lấy từ liên hệ đặt vé, không xác minh danh tính người ngồi trên ghế.</p>
      <OperatorTable headers={["Ghế", "Đơn giá", "Khách trên ghế", "Mã vé", "Tên trên vé", "Thông tin vé"]} empty={!b.items.length}>
        {b.items.map(item => <tr key={item.bookingItemId}><td>{item.seatCode}</td><td>{money(item.unitPrice)}</td><td>{seatPassenger(item.passengerName)}</td>
          <td>{item.ticket?.ticketCode || "Chưa có vé"}</td><td>{item.ticket?.passengerName || "—"}</td><td>{item.ticket && <>Ghế {item.ticket.seatCode}<p>Thanh toán #{item.ticket.paymentId}</p>{dateTime(item.ticket.createdAt)}</>}</td></tr>)}
      </OperatorTable></section>
    <section className="card"><h2>Lịch sử thanh toán</h2>
      {!b.payments.length && <p>Chưa có giao dịch thanh toán</p>}
      {!!b.payments.length && <OperatorTable headers={["Giao dịch", "Phương thức", "Trạng thái", "Số tiền", "Ngày tạo", "Ngày thanh toán"]} empty={!b.payments.length}>
        {b.payments.map(p => <tr key={p.id}><td>#{p.id}<p>{p.transactionReference || "—"}</p></td><td>{paymentMethodLabel(p.method)}</td><td>{paymentStatusLabel(p.status)}</td><td>{money(p.amount)}</td><td>{dateTime(p.createdAt)}</td><td>{p.paidAt ? dateTime(p.paidAt) : "—"}</td></tr>)}
      </OperatorTable>}</section>
    <details className="card operator-metadata"><summary>Tài khoản và thông tin nội bộ</summary><h2>Tài khoản khách hàng</h2><p>{b.customer.fullName} · #{b.customer.id}</p><p>{b.customer.email}</p><p>{b.customer.phone || "Chưa có số điện thoại"}</p><p>Đặt vé #{b.bookingId} · Tạo: {dateTime(b.createdAt)} · Cập nhật: {dateTime(b.updatedAt)}</p></details>
  </>;
}
export function OperatorBookingDetailPage() {
  const query = useOperatorBooking(Number(useParams().bookingId));
  return <><OperatorPageHeader title="Chi tiết đặt vé"><Link to="/operator/bookings">Danh sách đặt vé</Link></OperatorPageHeader>
    <RefreshState query={query} />
    <OperationsQueryState query={query}>{b => <BookingDetailContent booking={b} />}</OperationsQueryState></>;
}
