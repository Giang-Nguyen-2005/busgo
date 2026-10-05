import { ModificationSection } from "../features/booking/Modification";
import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { get } from "../api/client";
import type { Booking } from "../types/customer";
import { Empty, ErrorState, Loading, Journey, StatusBadge, PriceSummary } from "../components/ui";
import { dateTime, money, positiveId } from "../utils/format";
import { useAuth } from "../features/auth/AuthProvider";
import { passengerLabel } from "../features/customer/presentation";
import { blockingQueryError, RefreshNotice } from "../features/customer/QueryFeedback";
import { CancellationSection } from "../features/booking/CancellationSection";
export function BookingDetailPage() {
 const { bookingId } = useParams(); const auth = useAuth();
 const query = useQuery({ queryKey: ["booking", auth.user?.id, bookingId], queryFn: ({ signal }) => get<Booking>('/bookings/' + bookingId, undefined, signal), enabled: positiveId(bookingId), staleTime: 0 });
 if (!positiveId(bookingId)) return <Empty title="Mã đặt vé không hợp lệ"><Link to="/my-bookings">Về Vé của tôi</Link></Empty>;
 if (query.isPending) return <Loading />;
 if (blockingQueryError(query)) return <ErrorState error={query.error} retry={() => query.refetch()} />;
 const b = query.data!;
 return <><Link className="back-link" to="/my-bookings">← Vé của tôi</Link><RefreshNotice query={query} /><div className="page-heading"><div><span className="eyebrow">CHI TIẾT ĐẶT VÉ</span><h1>{b.bookingCode}</h1><StatusBadge status={b.status} /></div>{b.status === "CANCELLED" && b.recovery?.tickets.length ? <Link className="button secondary" to={'/booking-success?bookingId=' + bookingId}>Xem vé đã vô hiệu</Link> : b.status === "CONFIRMED" ? <Link className="button" to={'/booking-success?bookingId=' + bookingId}>Xem vé điện tử</Link> : b.status === "PENDING" ? <Link className="button secondary" to={'/payment?bookingId=' + bookingId}>Xem thanh toán</Link> : null}</div>
 <div className="card"><section className="detail-section"><h2>{b.operator.name}</h2><p className="muted">Tuyến xe: {b.route.name}</p><Journey pickup={b.pickup.name} dropoff={b.dropoff.name} departure={b.pickup.time} arrival={b.dropoff.time} /></section>
 <section className="detail-section"><h2>Chỗ và khách trên chỗ</h2>{b.seats.map(seat => <div className="detail-row" key={seat.tripSeatId}><span><b>Chỗ {seat.seatCode}</b> · {passengerLabel(seat.passengerName)}</span><span>{money(seat.unitPrice)}</span></div>)}</section>
 <section className="detail-section"><h2>Liên hệ đặt vé</h2><div className="contact-details"><strong>{b.contact.name}</strong><span>{b.contact.phone}</span><span>{b.contact.email}</span></div><p className="fine-print">Thông tin liên hệ không thay thế tên khách trên từng chỗ. Tên trên vé được hiển thị trong vé điện tử do hệ thống phát hành.</p></section>
 <PriceSummary seats={b.seats.map(s => s.seatCode)} unit={b.pricePerSeat} total={b.totalAmount} />
 {b.status === "PENDING" && <p className="notice info">Đặt vé đang chờ thanh toán mô phỏng. Khả năng thanh toán được hệ thống kiểm tra khi xác nhận.</p>}
 <p className="fine-print">Đặt lúc {dateTime(b.createdAt)}</p></div><ModificationSection bookingId={b.bookingId} /><CancellationSection bookingId={b.bookingId} bookingCode={b.bookingCode} route={b.route.name + ' · Chuyến #' + b.tripId} pickup={b.pickup.name + ' · ' + dateTime(b.pickup.time)} seats={b.seats.map(s=>s.seatCode)} contact={b.contact.name} amount={b.totalAmount} /></>;
}
