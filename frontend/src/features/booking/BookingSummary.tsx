import { placeLabel, routeLabel } from "../customer/presentation";
import { PromotionPrice } from "./PromotionPrice";
import type { Booking } from "../../types/customer";
import { Journey, PriceSummary, StatusBadge } from "../../components/ui";
export function BookingSummary({ booking }: { booking: Booking }) {
  return (
    <>
      <div className="split">
        <span className="eyebrow">{booking.bookingCode}</span>
        <StatusBadge status={booking.status} />
      </div>
      <h2>{booking.operator.name}</h2>
      <p className="muted">{routeLabel(booking.route.name)}</p>
      <Journey
        pickup={placeLabel(booking.pickup.name)}
        dropoff={placeLabel(booking.dropoff.name)}
        departure={booking.pickup.time}
        arrival={booking.dropoff.time}
      />
      <PromotionPrice price={booking.price} />
      {booking.price?.voucherCode ? <p>Chỗ: {booking.seats.filter(s => !s.cancelled).map(s => s.seatCode).join(", ")}</p> : <PriceSummary
        seats={booking.seats.map((seat) => seat.seatCode)}
        unit={booking.pricePerSeat}
        total={booking.totalAmount}
      />}
    </>
  );
}
