import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { QRCodeSVG } from "qrcode.react";
import { get } from "../api/client";
import { Empty, ErrorState, Journey, Loading, StatusBadge, Steps } from "../components/ui";
import type { TicketBundle } from "../types/customer";
import { money, positiveId, paymentMethodLabel, paymentStatusLabel } from "../utils/format";
import { useAuth } from "../features/auth/AuthProvider";
import { ticketHeading } from "../features/customer/presentation";
import { blockingQueryError, RefreshNotice } from "../features/customer/QueryFeedback";
export function TicketPage() {
 const [params] = useSearchParams(); const id = params.get("bookingId"); const auth = useAuth(); const location = useLocation();
 const [fresh] = useState(() => { try { return !!location.state?.justPaid && !sessionStorage.getItem('busgo.ticketSeen.' + id); } catch { return false; } });
 const query = useQuery({ queryKey: ["tickets", auth.user?.id, id], queryFn: ({ signal }) => get<TicketBundle>('/bookings/' + id + '/ticket', undefined, signal), enabled: positiveId(id), staleTime: 0 });
 useEffect(() => { if (query.isSuccess) { try { sessionStorage.setItem('busgo.ticketSeen.' + id, '1'); } catch { /* Presentation only. */ } } }, [id, query.isSuccess]);
 if (!positiveId(id)) return <Empty title="Chọn vé muốn xem"><Link to="/my-bookings">Vé của tôi</Link></Empty>;
 if (query.isPending) return <Loading />;
 if (blockingQueryError(query)) return <><ErrorState error={query.error} retry={() => query.refetch()} /><Link to={'/my-bookings/' + id}>Xem trạng thái đặt vé</Link></>;
 const bundle = query.data!;
 return <><RefreshNotice query={query} /><Steps current={3} /><div className="success-heading"><Link className="back-link" to={'/my-bookings/' + id}>← Chi tiết đặt vé</Link><h1>{ticketHeading(fresh)}</h1>{fresh && <p>Thanh toán giả lập đã được xác nhận. Vé điện tử của bạn đã sẵn sàng.</p>}</div>
 <section className="card ticket-journey"><div className="split"><strong>{bundle.bookingCode}</strong><StatusBadge status={bundle.status} /></div><h2>{bundle.operator.name}</h2><p className="muted">Tuyến xe: {bundle.route.name}</p><Journey pickup={bundle.pickup.name} dropoff={bundle.dropoff.name} departure={bundle.departureTime} arrival={bundle.arrivalTime} /><div className="split"><span>{paymentMethodLabel(bundle.paymentMethod)} · {paymentStatusLabel(bundle.paymentStatus)}</span><strong>{money(bundle.amount)}</strong></div></section>
 {bundle.tickets.length ? <div className="ticket-list">{bundle.tickets.map(ticket => <article className="digital-ticket" key={ticket.ticketId}><div className="ticket-body"><div className="split"><h2>Chỗ {ticket.seatCode}</h2><span className="badge">Vé điện tử</span></div><div><small className="muted">Tên trên vé</small><p><strong>{ticket.passengerName}</strong></p></div></div><div className="ticket-stub"><QRCodeSVG value={ticket.qrData} size={152} marginSize={2} title={'Mã QR vé ' + ticket.ticketCode + ', chỗ ' + ticket.seatCode} /><div className="ticket-code"><small>Mã vé · Chỗ {ticket.seatCode}</small><code tabIndex={0}>{ticket.ticketCode}</code></div></div></article>)}</div> : <Empty title="Chưa có vé điện tử"><Link to={'/my-bookings/' + id}>Kiểm tra trạng thái đặt vé</Link></Empty>}
 <div className="actions center"><Link className="button" to="/my-bookings">Vé của tôi</Link><Link className="button secondary" to="/">Tìm chuyến mới</Link></div></>;
}
