import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { allPages, operatorApi } from "../../api/operatorApi";
import { get } from "../../api/client";
import { Field, ErrorState, Loading } from "../../components/ui";
import { SeatMap } from "../../features/trip/SeatMap";
import { OperatorPageHeader } from "../../features/operator/shared";
import { useAuth } from "../../features/auth/AuthProvider";
import { canManageOperator } from "../../features/auth/access";
import { dateTime, money, paymentMethodLabel, today } from "../../utils/format";
import type { SeatMapData } from "../../types/customer";
import type { PaymentMethod } from "../../types/operator";

function StepHeading({ number, title }: { number: string; title: string }) {
  return <><span className="assisted-step-label">Bước {number}</span><span className="assisted-step-title">{title}</span></>;
}

export function OperatorBookingCreatePage() {
  const [params] = useSearchParams();
  const [date, setDate] = useState(today());
  const [tripId, setTripId] = useState(Number(params.get("tripId")) || 0);
  const [pickup, setPickup] = useState(0);
  const [dropoff, setDropoff] = useState(0);
  const [selected, setSelected] = useState<number[]>([]);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("PAY_ON_BOARD");
  const [review, setReview] = useState(false);
  const navigate = useNavigate();
  const cache = useQueryClient();
  const auth = useAuth();
  const trips = useQuery({ queryKey: ["operator", "assisted-trips", date], queryFn: () => allPages(p => operatorApi.trips({ ...p, businessDate: date, status: "SCHEDULED" })) });
  const trip = useQuery({ queryKey: ["operator", "trips", tripId], queryFn: () => operatorApi.trip(tripId), enabled: tripId > 0 });
  useEffect(() => {
    if (trip.data && trip.data.id === Number(params.get("tripId"))) {
      setDate(new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(trip.data.departureTime)));
    }
  }, [trip.data?.id, params]);
  const seats = useQuery({ queryKey: ["operator", "assisted-seats", tripId, pickup, dropoff], queryFn: () => get<SeatMapData>(`/trips/${tripId}/seats`, { pickupLocationId: pickup, dropoffLocationId: dropoff }), enabled: !!(tripId && pickup && dropoff), staleTime: 0 });
  const create = useMutation({ mutationFn: () => operatorApi.createBooking({ tripId, pickupLocationId: pickup, dropoffLocationId: dropoff, tripSeatIds: selected, contactName: name.trim(), contactPhone: phone.trim(), contactEmail: email.trim() || undefined, paymentMethod: method }), onSuccess: b => { void cache.invalidateQueries({ queryKey: ["operator"] }); navigate(`/operator/bookings/${b.bookingId}`, { state: { reservationCreated: true } }); }, onError: () => { setReview(false); setSelected([]); void seats.refetch(); } });
  const changeJourney = () => { setSelected([]); setReview(false); };
  const validSeats = !!seats.data && !seats.isError && !seats.isFetching && selected.length > 0 && selected.every(id => seats.data!.seats.some(s => s.tripSeatId === id && s.available));
  if (!canManageOperator(auth.user?.roles)) return <p role="alert">Không có quyền tạo đặt vé.</p>;
  return <div className="assisted-booking">
    <OperatorPageHeader title="Tạo đặt vé qua điện thoại"><Link to="/operator/bookings">Danh sách đặt vé</Link></OperatorPageHeader>
    <p className="muted">Chọn hành trình, ghế và liên hệ cho khách gọi điện. Chỗ được giữ sau khi tạo đặt vé thành công.</p>
    <ol className="assisted-progress" aria-label="Tiến trình đặt vé điện thoại">{["Hành trình", "Ghế", "Liên hệ", "Thanh toán", "Kiểm tra"].map((label, i) => <li key={label} aria-current={i === (review ? 4 : !tripId || !pickup || !dropoff ? 0 : !selected.length ? 1 : !name || !phone ? 2 : 3) ? "step" : undefined}>{i + 1}. {label}</li>)}</ol>
    <form onSubmit={e => { e.preventDefault(); if (!validSeats || !name.trim() || !phone.trim()) return; if (review) create.mutate(); else setReview(true); }}>
      <fieldset disabled={create.isPending || review} className="card">
        <legend><StepHeading number="01" title="Chọn chuyến" /></legend>
        <div className="assisted-fields">
          <Field label="Ngày đi (Việt Nam)" type="date" required value={date} onChange={e => { setDate(e.target.value); setTripId(0); setPickup(0); setDropoff(0); changeJourney(); }} />
          <label className="field">Chuyến xe<select aria-label="Chuyến xe" required value={tripId || ""} onChange={e => { setTripId(Number(e.target.value)); setPickup(0); setDropoff(0); changeJourney(); }}><option value="">Chọn chuyến xe</option>{trips.data?.map(t => <option key={t.id} value={t.id}>{dateTime(t.departureTime)} · {t.route.name} · {t.bus.licensePlate}</option>)}{tripId > 0 && !trips.data?.some(t => t.id === tripId) && trip.data && <option value={tripId}>{dateTime(trip.data.departureTime)} · {trip.data.route.name} · {trip.data.bus.licensePlate}</option>}</select></label>
          <label className="field">Điểm đón<select aria-label="Điểm đón" required value={pickup || ""} onChange={e => { setPickup(Number(e.target.value)); setDropoff(0); changeJourney(); }}><option value="">Chọn điểm đón</option>{trip.data?.stops.filter(s => s.allowPickup).map(s => <option key={s.id} value={s.locationId}>{s.locationName} · {s.plannedDepartureTime && dateTime(s.plannedDepartureTime)}</option>)}</select></label>
          <label className="field">Điểm trả<select aria-label="Điểm trả" required value={dropoff || ""} onChange={e => { setDropoff(Number(e.target.value)); changeJourney(); }}><option value="">Chọn điểm trả</option>{trip.data?.stops.filter(s => s.allowDropoff && s.stopOrder > (trip.data.stops.find(p => p.locationId === pickup)?.stopOrder ?? Infinity)).map(s => <option key={s.id} value={s.locationId}>{s.locationName}</option>)}</select></label>
        </div>
        {!trips.isPending && !trips.isError && trips.data?.length === 0 && <p role="status">Không có chuyến đã lên lịch trong ngày này. Chọn ngày khác.</p>}{trips.isPending && <Loading />}{trips.isError && <ErrorState error={trips.error} retry={() => trips.refetch()} />}{tripId > 0 && trip.isPending && <Loading />}{trip.isError && <ErrorState error={trip.error} />}
      </fieldset>
      <fieldset disabled={create.isPending || review} className="card"><legend><StepHeading number="02" title="Chọn ghế" /></legend>
        {!(pickup && dropoff) && <p className="muted">Chọn điểm đón và điểm trả để xem ghế trống.</p>}
        {!!(pickup && dropoff) && seats.isPending && <Loading />}{seats.isError && <ErrorState error={seats.error} retry={() => seats.refetch()} />}
        {seats.data && !seats.isError && <><div className="assisted-seat-heading"><div><strong className="assisted-price">{money(seats.data.price)} <small>/ ghế</small></strong><span className="assisted-availability">{seats.data.availableSeatCount} ghế còn trống</span></div><button type="button" className="secondary assisted-refresh" disabled={seats.isFetching} onClick={() => { setSelected([]); void seats.refetch(); }}>Cập nhật ghế trống</button></div><SeatMap seats={seats.data.seats} selected={selected} disabled={create.isPending || review || seats.isFetching} onToggle={id => setSelected(previous => previous.includes(id) ? previous.filter(x => x !== id) : [...previous, id])} />{selected.length > 0 && <div className="assisted-selection" aria-live="polite"><div><span>Ghế đã chọn</span><strong>{selected.map(id => seats.data?.seats.find(s => s.tripSeatId === id)?.seatCode).join(", ")}</strong></div><div><span>Tổng tiền · {selected.length} ghế</span><strong>{money(seats.data.price * selected.length)}</strong></div></div>}</>}
      </fieldset>
      <fieldset disabled={create.isPending || review} className="card"><legend><StepHeading number="03" title="Thông tin khách hàng" /></legend><p className="muted">Đây là liên hệ riêng của đặt vé, không tạo tài khoản hoặc gộp khách theo điện thoại/email.</p><div className="assisted-fields">
        <Field label="Tên liên hệ" required maxLength={100} value={name} onChange={e => setName(e.target.value)} />
        <Field label="Điện thoại" required type="tel" maxLength={20} value={phone} onChange={e => setPhone(e.target.value)} />
        <Field label="Email (không bắt buộc)" type="email" maxLength={150} value={email} onChange={e => setEmail(e.target.value)} />
      </div></fieldset>
      <fieldset disabled={create.isPending || review} className="card"><legend><StepHeading number="04" title="Thanh toán" /></legend><p className="muted">Thanh toán mô phỏng · Chưa thu tiền khi tạo đặt vé.</p><div className="assisted-fields">
        <label className="field">Phương thức thanh toán<select aria-label="Phương thức thanh toán" value={method} onChange={e => setMethod(e.target.value as PaymentMethod)}><option value="PAY_ON_BOARD">Thu tiền khi khách lên xe</option><option value="QR_TRANSFER">QR / link thanh toán mô phỏng</option></select></label>
      </div></fieldset>
      <section className="card assisted-review" aria-live="polite"><h2><StepHeading number="05" title={review ? "Kiểm tra đặt vé" : "Tóm tắt đặt vé"} /></h2><div className="assisted-review-grid"><div><p className="assisted-journey">{seats.data ? `${seats.data.pickup.name} → ${seats.data.dropoff.name}` : "Chưa chọn hành trình"}</p>{trip.data && <p className="muted">{dateTime(trip.data.departureTime)}</p>}<dl className="assisted-key-values"><div><dt>Ghế</dt><dd>{selected.map(id => seats.data?.seats.find(s => s.tripSeatId === id)?.seatCode).join(", ") || "Chưa chọn"}</dd></div><div><dt>Liên hệ</dt><dd>{name || "Chưa nhập"}{phone && <span className="assisted-availability">{phone}</span>}</dd></div><div><dt>Thanh toán</dt><dd>{paymentMethodLabel(method)}</dd></div></dl></div><div className="assisted-total"><span>Tổng tiền · {selected.length} ghế</span><strong>{seats.data && !seats.isError ? money(seats.data.price * selected.length) : "Chưa có giá"}</strong><span className="domain-badge tone-warning">Chưa thanh toán</span></div></div><p className="muted">Vé điện tử được cấp sau khi thanh toán. Link thanh toán được nhân viên gửi thủ công.</p>
        {create.isError && <ErrorState error={create.error} />}
        {review && <button type="button" className="secondary" disabled={create.isPending} onClick={() => setReview(false)}>Chỉnh sửa</button>}
        <button disabled={!validSeats || create.isPending}>{create.isPending ? "Đang tạo…" : review ? "Tạo đặt vé" : "Kiểm tra và tạo đặt vé"}</button>
      </section>
    </form>
  </div>;
}
