import type {
  SeatTemplateResponse,
  TripSeatResponse,
} from "../../types/operator";
export function SeatLayoutPreview({
  seats,
}: {
  seats: (SeatTemplateResponse | TripSeatResponse)[];
}) {
  if (!seats.length) return <p>Chưa có sơ đồ ghế.</p>;
  return (
    <div className="operator-seat-layout">
      {[...new Set(seats.map((s) => s.floor))]
        .sort((a, b) => a - b)
        .map((floor) => {
          const deck = seats.filter((s) => s.floor === floor);
          const minRow = Math.min(...deck.map((s) => s.row));
          const minCol = Math.min(...deck.map((s) => s.column));
          return (
            <section key={floor}>
              <h3>Tầng {floor}</h3>
              <div className="operator-seat-scroll">
                <div className="operator-seat-grid">
                  {deck.map((seat) => (
                    <div
                      key={seat.id}
                      className={`operator-seat ${"active" in seat && !seat.active ? "operator-seat-inactive" : ""}`}
                      style={{
                        gridRow: seat.row - minRow + 1,
                        gridColumn: seat.column - minCol + 1,
                      }}
                      title={`${seat.seatCode} · ${seat.seatType} · Hàng ${seat.row}, cột ${seat.column}`}
                    >
                      <strong>{seat.seatCode}</strong>
                      <small>
                        {"active" in seat && !seat.active
                          ? "Không dùng"
                          : seat.seatType}
                      </small>
                    </div>
                  ))}
                </div>
              </div>
            </section>
          );
        })}
    </div>
  );
}
