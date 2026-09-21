import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { get } from "../api/client";
import type { Booking } from "../types/customer";
import { Empty, ErrorState, Loading } from "../components/ui";
import { BookingSummary } from "../features/booking/BookingSummary";
import { dateTime, money, positiveId } from "../utils/format";
import { useAuth } from "../features/auth/AuthProvider";
export function BookingDetailPage() {
  const { bookingId } = useParams();
  const auth = useAuth();
  const query = useQuery({
    queryKey: ["booking", auth.user?.id, bookingId],
    queryFn: ({ signal }) =>
      get<Booking>(`/bookings/${bookingId}`, undefined, signal),
    enabled: positiveId(bookingId),
    staleTime: 0,
  });
  if (!positiveId(bookingId))
    return (
      <Empty title="Mã đặt vé không hợp lệ">
        <Link to="/my-bookings">Về Vé của tôi</Link>
      </Empty>
    );
  return (
    <>
      <Link className="back-link" to="/my-bookings">
        ← Vé của tôi
      </Link>
      <div className="page-heading">
        <div>
          <h1>Chi tiết đặt vé</h1>
          <p className="muted">Thông tin hành trình và liên hệ của bạn.</p>
        </div>
      </div>
      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => query.refetch()} />
      ) : (
        <div className="checkout-layout">
          <section className="card">
            <BookingSummary booking={query.data} />
            <h3>Ghế và hành khách</h3>
            {query.data.seats.map((seat) => (
              <div className="detail-row" key={seat.tripSeatId}>
                <span>
                  <b>{seat.seatCode}</b> ·{" "}
                  {seat.passengerName || query.data.contact.name}
                </span>
                <span>{money(seat.unitPrice)}</span>
              </div>
            ))}
            <p className="fine-print">
              Đặt lúc {dateTime(query.data.createdAt)}
            </p>
          </section>
          <aside className="card summary">
            <h2>Thông tin liên hệ</h2>
            <div className="contact-details">
              <strong>{query.data.contact.name}</strong>
              <span>{query.data.contact.phone}</span>
              <span>{query.data.contact.email}</span>
            </div>
            {query.data.status === "CONFIRMED" && (
              <Link
                className="button full"
                to={`/booking-success?bookingId=${bookingId}`}
              >
                Xem vé
              </Link>
            )}
            {query.data.status === "PENDING" && (
              <>
                <div className="notice info">
                  Đặt vé đang chờ thanh toán giả lập.
                </div>
                <Link
                  className="button full"
                  to={`/payment?bookingId=${bookingId}`}
                >
                  Tiếp tục thanh toán
                </Link>
              </>
            )}
          </aside>
        </div>
      )}
    </>
  );
}
