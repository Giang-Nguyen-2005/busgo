import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Link, useNavigate } from "react-router-dom";
import { Clock3 } from "lucide-react";
import { get, post } from "../api/client";
import { errorCode } from "../api/errors";
import { useAuth } from "../features/auth/AuthProvider";
import { contactSchema } from "../features/auth/forms";
import { readHold, saveHold } from "../features/booking/holdStore";
import {
  Empty,
  ErrorState,
  Field,
  Journey,
  Loading,
  PriceSummary,
  Steps,
} from "../components/ui";
import type { Booking, Hold } from "../types/customer";
import { tripLink } from "../utils/format";
export function BookingPage() {
  const [saved] = useState(readHold);
  const [now, setNow] = useState(Date.now());
  const navigate = useNavigate();
  const auth = useAuth();
  const cache = useQueryClient();
  const hold = useQuery({
    queryKey: ["active-hold", auth.user?.id, saved?.expiresAt],
    queryFn: ({ signal }) =>
      get<Hold>(
        `/seat-holds/${encodeURIComponent(saved!.holdToken)}`,
        undefined,
        signal,
      ),
    enabled: !!saved,
    staleTime: 0,
    refetchInterval: 10_000,
    retry: false,
  });
  const form = useForm<z.infer<typeof contactSchema>>({
    resolver: zodResolver(contactSchema),
    defaultValues: {
      contactName: auth.user?.fullName || "",
      contactPhone: auth.user?.phone || "",
      contactEmail: auth.user?.email || "",
    },
  });
  const booking = useMutation({
    mutationFn: (values: z.infer<typeof contactSchema>) =>
      post<Booking>("/bookings", { holdToken: saved!.holdToken, ...values }),
    onSuccess: (data) => {
      saveHold(null);
      cache.removeQueries({ queryKey: ["active-hold"] });
      void cache.invalidateQueries({ queryKey: ["bookings"] });
      navigate(`/payment?bookingId=${data.bookingId}`, { replace: true });
    },
  });
  const back = saved
    ? tripLink(saved.tripId, saved.pickup.locationId, saved.dropoff.locationId)
    : "/";
  const seconds = hold.data
    ? Math.max(0, Math.ceil((Date.parse(hold.data.expiresAt) - now) / 1000))
    : 0;
  const expired =
    hold.data?.status === "EXPIRED" ||
    (!!hold.data && seconds === 0) ||
    [errorCode(hold.error), errorCode(booking.error)].includes(
      "SEAT_HOLD_EXPIRED",
    );
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => {
    if (expired && !booking.isPending && !booking.isSuccess) {
      saveHold(null);
      navigate(back, {
        replace: true,
        state: { notice: "Thời gian giữ chỗ đã hết. Vui lòng chọn lại ghế." },
      });
    }
  }, [expired, booking.isPending, booking.isSuccess, back, navigate]);
  if (!saved)
    return (
      <Empty title="Bạn chưa có chỗ đang giữ">
        <p>Chọn chuyến và ghế trước khi nhập thông tin liên hệ.</p>
        <Link className="button" to="/">
          Tìm chuyến
        </Link>
      </Empty>
    );
  return (
    <>
      <Steps current={1} />
      <div className="page-heading">
        <div>
          <h1>Hoàn tất thông tin đặt vé</h1>
          <p className="muted">
            Kiểm tra hành trình và thông tin liên hệ của bạn.
          </p>
        </div>
      </div>
      {hold.isPending ? (
        <Loading />
      ) : hold.isError ? (
        <>
          <ErrorState error={hold.error} retry={() => hold.refetch()} />
          <div className="actions">
            <Link className="button secondary" to="/my-bookings">
              Kiểm tra Vé của tôi
            </Link>
            <Link className="button" to={back}>
              Chọn lại ghế
            </Link>
          </div>
        </>
      ) : (
        <div className="checkout-layout">
          <section className="card">
            <h2>Thông tin liên hệ</h2>
            <p className="muted">
              Thông tin này được sử dụng trên vé điện tử của bạn.
            </p>
            <form
              className="form-stack"
              onSubmit={form.handleSubmit((values) => booking.mutate(values))}
            >
              <Field
                label="Họ và tên"
                autoComplete="name"
                {...form.register("contactName")}
                error={form.formState.errors.contactName?.message}
              />
              <Field
                label="Số điện thoại"
                type="tel"
                autoComplete="tel"
                {...form.register("contactPhone")}
                error={form.formState.errors.contactPhone?.message}
              />
              <Field
                label="Email"
                type="email"
                autoComplete="email"
                {...form.register("contactEmail")}
                error={form.formState.errors.contactEmail?.message}
              />
              {booking.isError && (
                <>
                  <ErrorState error={booking.error} />
                  <p className="fine-print">
                    Nếu kết nối bị ngắt sau khi gửi, hãy{" "}
                    <Link to="/my-bookings">kiểm tra Vé của tôi</Link> trước khi
                    đặt lại.
                  </p>
                </>
              )}
              <button
                disabled={booking.isPending || expired || hold.isFetching}
              >
                {booking.isPending
                  ? "Đang tạo đặt vé…"
                  : "Tiếp tục đến thanh toán"}
              </button>
            </form>
          </section>
          <aside className="card summary sticky">
            <h2>{saved.operatorName || "Hành trình của bạn"}</h2>
            {saved.routeName && <p className="muted">{saved.routeName}</p>}
            <Journey
              pickup={hold.data.pickup.name}
              dropoff={hold.data.dropoff.name}
              departure={hold.data.pickup.departureTime}
              arrival={hold.data.dropoff.arrivalTime}
            />
            <PriceSummary
              seats={hold.data.seats.map((s) => s.seatCode)}
              unit={hold.data.pricePerSeat}
              total={hold.data.totalPrice}
            />
            <div className={`notice ${seconds < 60 ? "warning" : "info"}`}>
              <Clock3 size={20} />
              <div>
                Chỗ ngồi đang được giữ tạm thời.
                <strong className="countdown">
                  {Math.floor(seconds / 60)}:
                  {String(seconds % 60).padStart(2, "0")}
                </strong>
              </div>
            </div>
            <p className="fine-print">
              Giá cuối cùng do hệ thống xác nhận khi tạo đặt vé.
            </p>
          </aside>
        </div>
      )}
    </>
  );
}
