import { useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { QRCodeSVG } from "qrcode.react";
import { Check, BusFront } from "lucide-react";
import { get } from "../api/client";
import {
  Empty,
  ErrorState,
  Journey,
  Loading,
  StatusBadge,
  Steps,
} from "../components/ui";
import type { TicketBundle } from "../types/customer";
import {
  dateTime,
  money,
  positiveId,
  paymentMethodLabel,
  paymentStatusLabel,
} from "../utils/format";
import { useAuth } from "../features/auth/AuthProvider";
export function TicketPage() {
  const [params] = useSearchParams();
  const id = params.get("bookingId");
  const auth = useAuth();
  const query = useQuery({
    queryKey: ["tickets", auth.user?.id, id],
    queryFn: ({ signal }) =>
      get<TicketBundle>(`/bookings/${id}/ticket`, undefined, signal),
    enabled: positiveId(id),
  });
  if (!positiveId(id))
    return (
      <Empty title="Chọn vé muốn xem">
        <Link className="button" to="/my-bookings">
          Vé của tôi
        </Link>
      </Empty>
    );
  return (
    <>
      <Steps current={3} />
      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <>
          <ErrorState error={query.error} retry={() => query.refetch()} />
          <Link className="button secondary" to={`/my-bookings/${id}`}>
            Xem trạng thái đặt vé
          </Link>
        </>
      ) : (
        <>
          <div className="success-heading">
            <div className="success-icon">
              <Check size={30} />
            </div>
            <span className="eyebrow">HẸN BẠN TRÊN HÀNH TRÌNH</span>
            <h1>Đặt vé thành công!</h1>
            <p className="muted">
              Vé điện tử của bạn đã sẵn sàng. Chúc bạn một chuyến đi tốt lành.
            </p>
          </div>
          <div className="card ticket-overview">
            <div>
              <span className="eyebrow">MÃ ĐẶT VÉ</span>
              <h2>{query.data.bookingCode}</h2>
              <StatusBadge status={query.data.status} />
            </div>
            <div>
              <small className="muted">
                {paymentMethodLabel(query.data.paymentMethod)}
              </small>
              <strong>{money(query.data.amount)}</strong>
              <span className="green">
                {paymentStatusLabel(query.data.paymentStatus)}
              </span>
            </div>
          </div>
          {query.data.tickets.length === 0 ? (
            <Empty title="Chưa có vé điện tử" />
          ) : (
            query.data.tickets.map((ticket) => (
              <article className="digital-ticket" key={ticket.ticketId}>
                <div className="ticket-body">
                  <div className="split">
                    <span className="brand">
                      <BusFront size={24} />
                      BusGo
                    </span>
                    <span className="eyebrow">VÉ XE ĐIỆN TỬ</span>
                  </div>
                  <h2>{query.data.operator.name}</h2>
                  <p className="muted">{query.data.route.name}</p>
                  <Journey
                    pickup={query.data.pickup.name}
                    dropoff={query.data.dropoff.name}
                    departure={query.data.departureTime}
                    arrival={query.data.arrivalTime}
                  />
                  <div className="ticket-passenger">
                    <div>
                      <small>Hành khách</small>
                      <strong>{ticket.passengerName}</strong>
                    </div>
                    <div>
                      <small>Ghế</small>
                      <strong className="seat-number">{ticket.seatCode}</strong>
                    </div>
                  </div>
                </div>
                <div className="ticket-stub">
                  <QRCodeSVG
                    value={ticket.qrData}
                    size={152}
                    marginSize={2}
                    title={`Mã QR vé ${ticket.ticketCode}`}
                  />
                  <div className="ticket-code">
                    <small>Mã vé</small>
                    <code
                      tabIndex={0}
                      aria-label={`Mã vé ${ticket.ticketCode}`}
                    >
                      {ticket.ticketCode}
                    </code>
                  </div>
                  <small>Khởi hành {dateTime(query.data.departureTime)}</small>
                  <span className="badge">Vé đã xác nhận</span>
                </div>
              </article>
            ))
          )}
          <div className="actions center">
            <Link className="button" to="/my-bookings">
              Xem vé của tôi
            </Link>
            <Link className="button secondary" to="/">
              Về trang chủ
            </Link>
          </div>
        </>
      )}
    </>
  );
}
