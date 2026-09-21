import { useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { ArrowRight, Armchair } from "lucide-react";
import { apiClient } from "../api/client";
import type { BookingListItem, BookingStatus } from "../types/customer";
import type { PagedResponse } from "../types/api";
import {
  Empty,
  ErrorState,
  Loading,
  Pagination,
  StatusBadge,
  statuses,
} from "../components/ui";
import { dateTime, money } from "../utils/format";
import { useAuth } from "../features/auth/AuthProvider";
export function MyBookingsPage() {
  const [params, setParams] = useSearchParams();
  const status = params.get("status") || "";
  const page = Math.max(0, Number(params.get("page")) || 0);
  const auth = useAuth();
  const query = useQuery({
    queryKey: ["bookings", auth.user?.id, status, page],
    queryFn: async ({ signal }) =>
      (
        await apiClient.get<PagedResponse<BookingListItem>>("/bookings/me", {
          params: { status: status || undefined, page, size: 10 },
          signal,
        })
      ).data,
  });
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">HÀNH TRÌNH CỦA BẠN</span>
          <h1>Vé của tôi</h1>
          <p className="muted">Theo dõi đặt vé và xem lại những chuyến đi.</p>
        </div>
        <Link className="button secondary" to="/">
          Đặt chuyến mới <ArrowRight size={16} />
        </Link>
      </div>
      <nav className="tabs" aria-label="Lọc trạng thái">
        {[["", "Tất cả"], ...Object.entries(statuses)].map(([value, label]) => (
          <button
            key={value}
            className={status === value ? "active" : ""}
            aria-pressed={status === value}
            onClick={() => setParams(value ? { status: value } : {})}
          >
            {label}
          </button>
        ))}
      </nav>
      {query.isPending ? (
        <Loading />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => query.refetch()} />
      ) : (
        <>
          {query.data.data.length === 0 ? (
            <Empty title="Chưa có đặt vé nào">
              <p>
                Chuyến đi tiếp theo đang chờ bạn. Hãy tìm hành trình phù hợp.
              </p>
              <Link className="button" to="/">
                Khám phá chuyến xe
              </Link>
            </Empty>
          ) : (
            <div className="booking-list">
              {query.data.data.map((booking) => (
                <article key={booking.bookingId} className="card booking-card">
                  <div className="split">
                    <span className="eyebrow">{booking.bookingCode}</span>
                    <StatusBadge status={booking.status as BookingStatus} />
                  </div>
                  <h2>
                    {booking.pickup.name} → {booking.dropoff.name}
                  </h2>
                  <p className="muted">
                    {booking.operatorName} · {booking.routeName}
                  </p>
                  <div className="booking-card-info">
                    <span>{dateTime(booking.departureTime)}</span>
                    <span>
                      <Armchair size={17} />
                      {booking.seats.join(", ")}
                    </span>
                    <strong>{money(booking.totalAmount)}</strong>
                  </div>
                  <Link
                    className="text-button"
                    to={`/my-bookings/${booking.bookingId}`}
                  >
                    Xem chi tiết <ArrowRight size={16} />
                  </Link>
                </article>
              ))}
            </div>
          )}
          <Pagination
            pagination={query.data.pagination}
            onPage={(next) =>
              setParams({ ...(status ? { status } : {}), page: String(next) })
            }
          />
        </>
      )}
    </>
  );
}
