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
      <p className="muted">{booking.route.name}</p>
      <Journey
        pickup={booking.pickup.name}
        dropoff={booking.dropoff.name}
        departure={booking.pickup.time}
        arrival={booking.dropoff.time}
      />
      <PriceSummary
        seats={booking.seats.map((seat) => seat.seatCode)}
        unit={booking.pricePerSeat}
        total={booking.totalAmount}
      />
    </>
  );
}
