import { Rating } from "../features/marketplace/Rating";
import { DomainStatusBadge } from "./DomainStatusBadge";
import { statusLabels } from "../utils/status";
import { useId, useState } from "react";
import { fallbackBusAlt } from "../features/customer/presentation";
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
export function Field({ label, error, description, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; description?: string }) {
 const generated = useId(); const id = props.id || generated;
 const described = [props["aria-describedby"], description ? id + "-hint" : "", error ? id + "-error" : ""].filter(Boolean).join(" ") || undefined;
 return <label className="field" htmlFor={id}><span id={id + "-label"}>{label}</span><input {...props} id={id} aria-labelledby={props["aria-labelledby"] || id + "-label"} aria-invalid={!!error} aria-describedby={described} />{description && <small id={id + "-hint"} className="muted">{description}</small>}{error && <small id={id + "-error"} className="field-error">{error}</small>}</label>;
}
export function PasswordField(props: Parameters<typeof Field>[0]) {
 const [visible, setVisible] = useState(false);
 return <div className="password-control"><Field {...props} type={visible ? "text" : "password"} /><button type="button" className="secondary" aria-label={(visible ? "Ẩn " : "Hiện ") + props.label.toLowerCase()} aria-pressed={visible} onClick={() => setVisible(!visible)}>{visible ? "Ẩn" : "Hiện"}</button></div>;
}
export const statuses: Record<BookingStatus, string> = statusLabels.booking;
export function StatusBadge({ status }: { status: BookingStatus }) {
  return <DomainStatusBadge domain="booking" status={status} />;
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
        <span>Chỗ đã chọn ({seats.length})</span>
        <strong className="seat-chips">{seats.length ? seats.map(seat => <span className="seat-chip" key={seat}>{seat}</span>) : "Chưa chọn chỗ"}</strong>
      </div>
      <div>
        <span>Giá mỗi chỗ</span>
        <span>{money(unit)}</span>
      </div>
      <div className="total">
        <strong>Tổng cộng</strong>
        <strong>{money(total)}</strong>
      </div>
    </div>
  );
}
export function TripCard({ trip, searchContext }: { trip: Trip; searchContext?: string }) {
 const [failed, setFailed] = useState(false);
 const illustrative = !trip.busImageUrl || failed;
 const href = tripLink(trip.tripId, trip.pickup.locationId, trip.dropoff.locationId) + (searchContext ? "&search=" + encodeURIComponent(searchContext) : "");
 return <article className="card trip-card">
 <div className="trip-card-content"><div className="trip-thumbnail-wrap"><img className="trip-thumbnail" src={illustrative ? "/images/busgo/bus-standard.jpg" : trip.busImageUrl!} alt={illustrative ? fallbackBusAlt : 'Xe ' + trip.busType.name + ' của ' + trip.operator.name} loading="lazy" onError={() => setFailed(true)} /></div>
 <div className="trip-card-details"><div className="trip-meta"><Link to={`/operators/${trip.operator.id}`}><strong>{trip.operator.name}</strong></Link><Rating {...trip.operator} /></div><div className="trip-times"><div><small className="time-label">Đón khách</small><strong>{time(trip.pickup.departureTime)}</strong>{(trip.delayMinutes ?? 0)>0 && <small>Dự kiến {time(trip.expectedPickupAt!)} · {trip.operationalLabel}</small>}<span>{trip.pickup.name}</span></div><div className="trip-line"><small>{duration(trip.durationMinutes)}</small><span>○<i /><ArrowRight size={15} /></span></div><div><small className="time-label">Trả khách</small><strong>{time(trip.dropoff.arrivalTime)}</strong>{(trip.delayMinutes ?? 0)>0 && <small>Dự kiến {time(trip.expectedDropoffAt!)}</small>}<span>{trip.dropoff.name}</span>{date(trip.pickup.departureTime) !== date(trip.dropoff.arrivalTime) && <small className="arrival-date">{date(trip.dropoff.arrivalTime)}</small>}</div></div>
 <div className="trip-meta"><span>{trip.busType.name}</span></div><p className="trip-route">Tuyến xe: {trip.route.name}</p></div></div>
 <div className="trip-bottom"><span className="muted">Còn <b className="green">{trip.availableSeats} chỗ</b></span><div className="trip-price"><strong>{money(trip.price)}</strong><small>/ chỗ</small></div><Link className="button" to={href}>Chọn chỗ <ArrowRight size={16} /></Link></div>
 </article>;
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
