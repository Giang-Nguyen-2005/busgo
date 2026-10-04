import { blockingQueryError, RefreshNotice } from "../features/customer/QueryFeedback";
import { paymentPresentation } from "../features/customer/presentation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { QrCode, ShieldCheck } from "lucide-react";
import { get, post } from "../api/client";
import type { Booking, Payment } from "../types/customer";
import { Empty, ErrorState, Loading, Steps } from "../components/ui";
import { BookingSummary } from "../features/booking/BookingSummary";
import { dateTime, money, positiveId } from "../utils/format";
import { useAuth } from "../features/auth/AuthProvider";
export function PaymentPage() {
  const [params] = useSearchParams();
  const id = params.get("bookingId");
  const navigate = useNavigate();
  const cache = useQueryClient();
  const auth = useAuth();
  const booking = useQuery({
    queryKey: ["booking", auth.user?.id, id],
    queryFn: ({ signal }) => get<Booking>(`/bookings/${id}`, undefined, signal),
    enabled: positiveId(id),
    staleTime: 0,
  });
  const payment = useMutation({
    mutationFn: () => post<Payment>(`/bookings/${id}/payments/mock-confirm`),
    onSuccess: (data) => {
      void cache.invalidateQueries({ queryKey: ["booking"] });
      void cache.invalidateQueries({ queryKey: ["bookings"] });
      if (data.paymentStatus === "PAID" && data.bookingStatus === "CONFIRMED")
        navigate(`/booking-success?bookingId=${data.bookingId}`, {
          replace: true, state: { justPaid: true },
        });
    },
    onError: () => {
      void booking.refetch();
    },
  });
  if (!positiveId(id))
    return (
      <Empty title="Chọn đặt vé cần thanh toán">
        <Link className="button" to="/my-bookings">
          Vé của tôi
        </Link>
      </Empty>
    );
  return (
    <>
      <Link className="back-link" to={`/my-bookings/${id}`}>← Quay lại chi tiết đặt vé</Link>
      <Steps current={2} /><RefreshNotice query={booking} />
      <div className="page-heading">
        <div>
          <h1>Thanh toán đặt vé</h1>
          <p className="muted">Chỉ còn một bước để nhận vé điện tử.</p>
        </div>
      </div>
      {booking.isPending ? (
        <Loading />
      ) : blockingQueryError(booking) ? (
        <ErrorState error={booking.error} retry={() => booking.refetch()} />
      ) : (
        <div className="checkout-layout">
          <section className="card payment-card">
            <span className="badge">THANH TOÁN MÔ PHỎNG</span><h2>{booking.data!.bookingCode}</h2><div className="payment-amount">{money(booking.data!.totalAmount)}</div><p className="notice info" role="status">{paymentPresentation(booking.data!.status, payment.isPending)}</p>
            <h2>Thanh toán QR mô phỏng</h2>
            {booking.data!.status === "PENDING" && booking.data!.recovery?.paymentDueAt && <p>Hạn thanh toán: {dateTime(booking.data!.recovery.paymentDueAt)}</p>}
            <p className="muted">Mã đặt vé {booking.data!.bookingCode}</p>
            {booking.data!.status === "PENDING" && <div className="mock-qr">
              <QrCode size={96} strokeWidth={1} />
              <strong>QR minh họa</strong>
              <small>Không dùng để chuyển tiền</small>
            </div>}

            <p className="muted">
              Không có giao dịch ngân hàng hoặc khoản tiền thực tế nào được thực
              hiện.
            </p>
            {payment.isError && <ErrorState error={payment.error} />}
            {booking.data!.status === "PENDING" ? (
              <button
                className="full"
                disabled={payment.isPending || booking.isFetching || booking.isError}
                onClick={() => payment.mutate()}
              >
                {payment.isPending
                  ? "Đang xác nhận…"
                  : "Xác nhận thanh toán mô phỏng"}
              </button>
            ) : booking.data!.status === "CONFIRMED" ? (
              <>
                <div className="notice success">
                  Đặt vé đã được xác nhận. Vé điện tử của bạn đã sẵn sàng.
                </div>
                <Link
                  className="button full"
                  to={`/booking-success?bookingId=${id}`}
                >
                  Xem vé điện tử
                </Link>
              </>
            ) : (
              <div className="notice warning">
                Đặt vé này không thể tiếp tục thanh toán.
              </div>
            )}
            <div className="search-note">
              <ShieldCheck size={16} />
              Kết quả thanh toán được xác nhận bởi hệ thống.
            </div>
          </section>
          <aside className="card summary sticky">
            <BookingSummary booking={booking.data!} />
            <Link className="text-button" to={`/my-bookings/${id}`}>
              Xem chi tiết đặt vé
            </Link>
          </aside>
        </div>
      )}
    </>
  );
}
