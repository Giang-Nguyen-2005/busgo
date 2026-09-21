import type { InputHTMLAttributes, ReactNode } from "react";
import {
  AlertCircle,
  ArrowRight,
  BusFront,
  Check,
  LoaderCircle,
  MapPin,
} from "lucide-react";
import { Link } from "react-router-dom";
import { errorMessage } from "../api/errors";
import {
  date,
  dateTime,
  duration,
  money,
  time,
  tripLink,
} from "../utils/format";
import type { BookingStatus, Trip } from "../types/customer";
import type { PagedResponse } from "../types/api";

export function Loading() {
  return (
    <div className="loading" role="status">
      <LoaderCircle className="spin" size={24} />
      <span>Đang tải thông tin…</span>
    </div>
  );
}
export function ErrorState({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  return (
    <div className="notice danger" role="alert">
      <AlertCircle size={20} />
      <div>
        {errorMessage(error)}
        {retry && (
          <button className="text-button" onClick={retry}>
            Thử lại
          </button>
        )}
      </div>
    </div>
  );
}
export function Empty({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty card">
      <BusFront size={36} />
      <h2>{title}</h2>
      <div className="muted">{children}</div>
    </div>
  );
}
export function Field({
  label,
  error,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string }) {
  return (
    <label className="field">
      <span>{label}</span>
      <input aria-invalid={!!error} {...props} />
      {error && <small className="field-error">{error}</small>}
    </label>
  );
}
export const statuses: Record<BookingStatus, string> = {
  PENDING: "Chờ thanh toán",
  CONFIRMED: "Đã xác nhận",
  CANCELLED: "Đã hủy",
  COMPLETED: "Hoàn thành",
};
export function StatusBadge({ status }: { status: BookingStatus }) {
  return <span className={`badge status-${status}`}>{statuses[status]}</span>;
}
export function Steps({ current }: { current: number }) {
  return (
    <ol className="steps" aria-label="Tiến trình đặt vé">
      {["Chọn ghế", "Thông tin liên hệ", "Thanh toán", "Nhận vé"].map(
        (label, i) => (
          <li
            key={label}
            className={i <= current ? "active" : ""}
            aria-current={i === current ? "step" : undefined}
          >
            <span>{i < current ? <Check size={14} /> : i + 1}</span>
            {label}
          </li>
        ),
      )}
    </ol>
  );
}
export function Journey({
  pickup,
  dropoff,
  departure,
  arrival,
}: {
  pickup: string;
  dropoff: string;
  departure: string;
  arrival: string;
}) {
  return (
    <div className="journey">
      <div>
        <MapPin size={16} />
        <div>
          <strong>{pickup}</strong>
          <small>{dateTime(departure)}</small>
        </div>
      </div>
      <div>
        <MapPin size={16} />
        <div>
          <strong>{dropoff}</strong>
          <small>{dateTime(arrival)}</small>
        </div>
      </div>
    </div>
  );
}
export function PriceSummary({
  seats,
  unit,
  total,
}: {
  seats: string[];
  unit: number;
  total: number;
}) {
  return (
    <div className="price-summary">
      <div>
        <span>Ghế đã chọn ({seats.length})</span>
        <strong>{seats.join(", ") || "Chưa chọn ghế"}</strong>
      </div>
      <div>
        <span>Giá mỗi ghế</span>
        <span>{money(unit)}</span>
      </div>
      <div className="total">
        <strong>Tổng cộng</strong>
        <strong>{money(total)}</strong>
      </div>
    </div>
  );
}
export function TripCard({ trip }: { trip: Trip }) {
  return (
    <article className="card trip-card">
      <div className="trip-top">
        <div className="operator-icon">
          <BusFront />
        </div>
        <div>
          <h2>{trip.operator.name}</h2>
          <span className="muted">{trip.busType.name}</span>
        </div>
        <span className="badge">{trip.route.name}</span>
      </div>
      <div className="trip-main">
        <div className="trip-times">
          <div>
            <small className="time-label">Khởi hành</small>
            <strong>{time(trip.pickup.departureTime)}</strong>
            <span>{trip.pickup.name}</span>
          </div>
          <div className="trip-line">
            <small>{duration(trip.durationMinutes)}</small>
            <span>
              ○<i />
              <ArrowRight size={15} />
            </span>
          </div>
          <div>
            <small className="time-label">Đến nơi</small>
            <strong>{time(trip.dropoff.arrivalTime)}</strong>
            <span>{trip.dropoff.name}</span>
            {date(trip.pickup.departureTime) !==
              date(trip.dropoff.arrivalTime) && (
              <small className="arrival-date">
                {date(trip.dropoff.arrivalTime)}
              </small>
            )}
          </div>
        </div>
        <div className="trip-price">
          <strong>{money(trip.price)}</strong>
          <small>/ ghế</small>
        </div>
      </div>
      <div className="trip-bottom">
        <span className="muted">
          Còn <b className="green">{trip.availableSeats} chỗ</b> ·{" "}
          {dateTime(trip.pickup.departureTime)}
        </span>
        <Link
          className="button"
          to={tripLink(
            trip.tripId,
            trip.pickup.locationId,
            trip.dropoff.locationId,
          )}
        >
          Chọn chuyến <ArrowRight size={16} />
        </Link>
      </div>
    </article>
  );
}
export function Pagination({
  pagination,
  onPage,
}: {
  pagination: PagedResponse<unknown>["pagination"];
  onPage: (page: number) => void;
}) {
  return (
    <nav className="pagination" aria-label="Phân trang">
      <button
        className="secondary"
        disabled={pagination.page === 0}
        onClick={() => onPage(pagination.page - 1)}
      >
        Trước
      </button>
      <span>
        Trang {pagination.page + 1} / {Math.max(1, pagination.totalPages)}
      </span>
      <button
        className="secondary"
        disabled={pagination.page + 1 >= pagination.totalPages}
        onClick={() => onPage(pagination.page + 1)}
      >
        Sau
      </button>
    </nav>
  );
}
