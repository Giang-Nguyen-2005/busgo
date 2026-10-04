import { QRCodeSVG } from "qrcode.react";
import { CancellationSection } from "../../features/booking/CancellationSection";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { operatorApi } from "../../api/operatorApi";
import { useAuth } from "../../features/auth/AuthProvider";
import { canManageOperator } from "../../features/auth/access";
import { ErrorState } from "../../components/ui";
import { useEffect, useState } from "react";
import { RefreshState } from "../../features/operator/RefreshState";
import { Link, useLocation, useParams, useSearchParams } from "react-router-dom";
import { Field } from "../../components/ui";
import { useOperatorBooking, useOperatorBookings } from "../../features/operator/queries";
import { OperationsBadge, OperationsQueryState } from "../../features/operator/OperationsShared";
import { bookingFilters, bookingStatuses, operationLabel, paymentStatuses, seatPassenger, updateBookingFilter } from "../../features/operator/operations";
import { OperatorPageHeader, OperatorStatusBadge, OperatorTable, Pagination } from "../../features/operator/shared";
import { dateTime, money, paymentMethodLabel, paymentStatusLabel } from "../../utils/format";
import type { BookingStop, OperatorBookingDetail, OperatorBookingListItem } from "../../types/operator";

function Stop({ stop }: { stop: BookingStop }) {
  return <>{stop.name}{stop.time && <span className="operator-stop-time muted"> · {dateTime(stop.time)}</span>}</>;
}
export function OperatorBookingsPage() {
  const auth = useAuth();
  const [params, setParams] = useSearchParams();
  const filters = bookingFilters(params);
  const set = (key: string, value: string) => setParams(previous => updateBookingFilter(previous, key, value), { replace: key === "q" });
  const [search, setSearch] = useState(filters.q);
  useEffect(() => { const timer = window.setTimeout(() => setSearch(filters.q), 350); return () => window.clearTimeout(timer); }, [filters.q]);
  const query = useOperatorBookings({ ...filters, q: search });
  return <>
    <OperatorPageHeader title="Đặt vé">{canManageOperator(auth.user?.roles) && <Link className="button" to="/operator/bookings/new">Tạo đặt vé</Link>}</OperatorPageHeader>
    <div className="operator-filters">
      <Field label="Tìm mã đặt vé, tên, điện thoại, email" maxLength={100} value={params.get("q") || ""} onChange={e => set("q", e.target.value)} />
      <Field label="Mã chuyến" type="number" min={1} value={params.get("tripId") || ""} onChange={e => set("tripId", e.target.value)} />
      <label className="field">Trạng thái đặt vé<select value={filters.status || ""} onChange={e => set("status", e.target.value)}><option value="">Tất cả</option>{bookingStatuses.map(s => <option key={s} value={s}>{operationLabel(s)}</option>)}</select></label>
      <label className="field">Thanh toán<select value={filters.paymentStatus || ""} onChange={e => set("paymentStatus", e.target.value)}><option value="">Tất cả</option>{paymentStatuses.map(s => <option key={s} value={s}>{paymentStatusLabel(s)}</option>)}</select></label>
      <Field label="Ngày tạo (Việt Nam)" type="date" value={filters.date || ""} onChange={e => set("date", e.target.value)} />
      <button className="secondary" onClick={() => setParams({})}>Xóa bộ lọc</button>
    </div>
    <div className="operator-filter-chips">{[filters.q, filters.tripId && `Chuyến #${filters.tripId}`, filters.status && operationLabel(filters.status), filters.paymentStatus && paymentStatusLabel(filters.paymentStatus), filters.date].filter(Boolean).map(value => <span className="operator-badge" key={String(value)}>{value}</span>)}</div>
    <p className="muted">Ngày tạo và giờ hiển thị theo Việt Nam (UTC+7).</p>
    <RefreshState query={query} /><div className="operator-booking-results booking-zones">
    <OperationsQueryState query={query}>{result => <>
      <OperatorTable headers={["Đặt vé / liên hệ", "Hành trình", "Trạng thái / tổng tiền"]} empty={!result.data.length} emptyTitle="Không có đặt vé phù hợp với bộ lọc">
        {result.data.map(b => <OperatorBookingRow key={b.bookingId} booking={b} />)}
      </OperatorTable><Pagination pagination={result.pagination} set={set} />
    </>}</OperationsQueryState></div>
  </>;
}
export function OperatorBookingRow({ booking: b }: { booking: OperatorBookingListItem }) {
  return <tr>
    <td className="booking-identity"><Link className="operator-booking-code" to={`/operator/bookings/${b.bookingId}`}>{b.bookingCode}</Link><Link className="operator-contact-name" to={`/operator/bookings/${b.bookingId}`}>{b.contact.name}</Link><p>{b.contact.phone}</p><details className="row-secondary"><summary>Thông tin thêm</summary><small className="muted">{b.contact.email || "Chưa có email"}</small><small>Tạo: {dateTime(b.createdAt)}</small></details></td>
    <td className="booking-journey"><Link to={`/operator/trips/${b.tripId}`}><strong>{b.route.name}</strong></Link><p>{b.pickup.time ? dateTime(b.pickup.time) : "Chưa có giờ đón"} · <strong>{b.seatCount} ghế</strong></p><small className="muted">{b.pickup.name} → {b.dropoff.name}</small>{b.dropoff.time && <small className="muted">Trả: {dateTime(b.dropoff.time)}</small>}</td>
    <td className="booking-state"><div><OperationsBadge status={b.status} /><span className={`operator-badge operator-status-${b.paymentStatus}`}>{paymentStatusLabel(b.paymentStatus)}</span></div><small>{b.source} · {paymentMethodLabel(b.paymentMethod)}</small><strong className="booking-money">{money(b.totalAmount)}</strong></td>
  </tr>;
}

export function BookingDetailContent({ booking: b }: { booking: OperatorBookingDetail }) {
  const latest = b.payments.reduce<(typeof b.payments)[number] | undefined>((a, p) => !a || p.id > a.id ? p : a, undefined);
  return <article className="booking-dossier">
    <header className="dossier-overview"><div><span className="eyebrow">ĐẶT VÉ</span><h2>{b.bookingCode}</h2><p>{b.source} · {paymentMethodLabel(b.paymentMethod)}</p><OperationsBadge status={b.status} /><p className="muted">Tạo: {dateTime(b.createdAt)} · Giờ Việt Nam</p></div><div className="dossier-total"><small>Tổng đặt vé</small><strong>{money(b.totalAmount)}</strong></div></header>
    <div className="dossier-context">
      <section><h2>Hành trình</h2><h3>{b.route.name}</h3><p><Link to={`/operator/trips/${b.trip.id}`}>Chuyến #{b.trip.id}</Link> · <OperatorStatusBadge status={b.trip.status} /></p><dl className="dossier-stops"><div><dt>Đón</dt><dd><Stop stop={b.pickup} /></dd></div><div><dt>Trả</dt><dd><Stop stop={b.dropoff} /></dd></div></dl><p className="fine-print">Giờ toàn chuyến: {dateTime(b.trip.departureTime)} → {dateTime(b.trip.estimatedArrivalTime)}</p></section>
      <section><h2>Liên hệ đặt vé</h2><strong>{b.contact.name}</strong><p>{b.contact.phone}</p><p>{b.contact.email || "Chưa có email"}</p></section>
    </div>
    <section className="dossier-section"><h2>Ghế và vé <span className="muted">· {b.items.length} ghế</span></h2><p className="fine-print">Tên trên vé có thể lấy từ liên hệ đặt vé, không xác minh danh tính người ngồi trên ghế.</p>
      {b.items.length ? <div className="dossier-seats">{b.items.map(item => <article key={item.bookingItemId} className="dossier-seat"><strong className="operator-seat-code">{item.seatCode}</strong><div><small>Khách trên ghế</small><p>{seatPassenger(item.passengerName)}</p><small>Đơn giá: {money(item.unitPrice)}</small></div><div><small>Vé điện tử</small><strong>{item.ticket?.ticketCode || "Chưa có vé"}</strong>{item.ticket && <><p>Tên trên vé: {item.ticket.passengerName || "—"}</p>{item.ticket.status === "VOID" || b.status === "CANCELLED" ? <p>Đã vô hiệu (VOID) · Không dùng để lên xe</p> : <QRCodeSVG value={item.ticket.ticketCode} size={128} title={`Vé ${item.ticket.ticketCode}`} />}<details className="row-secondary"><summary>Thông tin vé</summary><p>Ghế trên vé: {item.ticket.seatCode} · Thanh toán #{item.ticket.paymentId}</p><p>Tạo: {dateTime(item.ticket.createdAt)}</p></details></>}</div></article>)}</div> : <p>Chưa có ghế trong đặt vé.</p>}
    </section>
    <section className="dossier-section"><div className="dossier-payment-heading"><h2>Thanh toán</h2><span className={`operator-badge operator-status-${latest?.status || "NEUTRAL"}`}>{latest ? paymentStatusLabel(latest.status) : "Chưa có giao dịch thanh toán"}</span></div>
      {latest && <p className="dossier-latest">Giao dịch gần nhất · {paymentMethodLabel(latest.method)} · <strong>{money(latest.amount)}</strong>{latest.paidAt && <> · Thanh toán: {dateTime(latest.paidAt)}</>}</p>}
      {!!b.payments.length && <details className="dossier-payment-history"><summary>Lịch sử thanh toán · {b.payments.length} giao dịch</summary><div>{b.payments.map(p => <article className="dossier-payment-row" key={p.id}><div><strong>{paymentMethodLabel(p.method)}</strong><small>Giao dịch #{p.id} · {p.transactionReference || "Chưa có mã tham chiếu"}</small></div><div><small>Tạo: {dateTime(p.createdAt)}</small><small>Thanh toán: {p.paidAt ? dateTime(p.paidAt) : "Chưa thanh toán"}</small></div><div><span className={`operator-badge operator-status-${p.status}`}>{paymentStatusLabel(p.status)}</span><strong>{money(p.amount)}</strong></div></article>)}</div></details>}
    </section>
    <details className="dossier-internal"><summary>Tài khoản và thông tin nội bộ</summary><h3>Tài khoản khách hàng</h3>{b.customer ? <><p>{b.customer.fullName} · #{b.customer.id}</p><p>{b.customer.email}</p><p>{b.customer.phone || "Chưa có số điện thoại"}</p></> : <p>Khách đặt qua điện thoại · Không có tài khoản BusGo</p>}<p>Đặt vé #{b.bookingId} · Tạo: {dateTime(b.createdAt)} · Cập nhật: {dateTime(b.updatedAt)}</p></details>
  </article>;
}

export function OperatorBookingDetailPage() {
  const query = useOperatorBooking(Number(useParams().bookingId));
  const created = useLocation().state?.reservationCreated;
  return <><OperatorPageHeader title="Chi tiết đặt vé"><Link to="/operator/bookings">Danh sách đặt vé</Link></OperatorPageHeader>
    <RefreshState query={query} />
    <OperationsQueryState query={query}>{b => <>{created && b.status === "PENDING" && <div className="notice success" role="status">Đã tạo đặt vé {b.bookingCode} và giữ chỗ thành công. Chưa thanh toán không có nghĩa là giữ chỗ thất bại. Vé điện tử được cấp sau khi thu tiền mô phỏng.</div>}<BookingDetailContent booking={b} /><AssistedBookingActions booking={b} /><CancellationSection operator bookingId={b.bookingId} bookingCode={b.bookingCode} route={b.route.name + ' · Chuyến #' + b.trip.id} pickup={b.pickup.name + ' · ' + (b.pickup.time ? dateTime(b.pickup.time) : "Chưa có giờ đón")} seats={b.items.map(s=>s.seatCode)} contact={b.contact.name + ' · ' + b.contact.phone} amount={b.totalAmount} /></>}</OperationsQueryState></>;
}

export function AssistedBookingActions({ booking: b }: { booking: OperatorBookingDetail }) {
  const auth = useAuth();
  const cache = useQueryClient();
  const [link, setLink] = useState("");
  const [copied, setCopied] = useState(false);
  const [note, setNote] = useState("");
  const [review, setReview] = useState(false);
  const record = useMutation({ mutationFn: () => operatorApi.recordPayment(b.bookingId, { method: b.paymentMethod, referenceNote: note.trim() || undefined }), onSuccess: () => { setReview(false); void cache.invalidateQueries({ queryKey: ["operator"] }); } });
  const issue = useMutation({ mutationFn: () => operatorApi.paymentLink(b.bookingId), onSuccess: result => { setLink(new URL(result.path, window.location.origin).href); setCopied(false); } });
  if (b.source !== "PHONE" || b.status === "CANCELLED" || !canManageOperator(auth.user?.roles)) return null;
  return <section className="card assisted-actions"><h2>Thu tiền mô phỏng</h2><p className="muted">BusGo demo: không có giao dịch ngân hàng hoặc thu tiền thực tế.</p><p>{paymentMethodLabel(b.paymentMethod)} · {b.status === "CONFIRMED" ? "Đã thanh toán" : "Chưa thanh toán"}</p>
    {b.status === "PENDING" && <><Field label="Ghi chú / tham chiếu (không bắt buộc)" maxLength={500} value={note} onChange={e => setNote(e.target.value)} />{review ? <><p>Xác nhận thu tiền mô phỏng {money(b.totalAmount)} cho {b.bookingCode}. Thao tác sẽ cấp vé điện tử.</p><button disabled={record.isPending} onClick={() => record.mutate()}>Xác nhận thu tiền mô phỏng</button><button className="secondary" disabled={record.isPending} onClick={() => setReview(false)}>Quay lại</button></> : <button onClick={() => setReview(true)}>Ghi nhận thu tiền mô phỏng</button>}{record.isError && <ErrorState error={record.error} />}</>}
    {b.paymentMethod === "QR_TRANSFER" && <><p>Gửi link thủ công qua Zalo hoặc kênh liên hệ của khách. Tạo link mới sẽ vô hiệu link cũ.</p><button className="secondary" disabled={issue.isPending} onClick={() => issue.mutate()}>{link ? "Tạo link mới" : "Tạo link thanh toán"}</button>{issue.isError && <ErrorState error={issue.error} />}{link && <><Field label="Link thanh toán" value={link} readOnly /><a href={link} target="_blank" rel="noreferrer">Mở trang thanh toán</a><button className="secondary" onClick={async () => { try { await navigator.clipboard.writeText(link); setCopied(true); } catch { setCopied(false); } }}>Sao chép link thanh toán mô phỏng</button><p role="status">{copied ? "Đã sao chép link. Gửi cho khách thủ công." : "Có thể chọn và sao chép link trong ô trên."}</p></>}</>}
  </section>;
}
