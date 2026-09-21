import { Armchair } from "lucide-react";
import type { Seat } from "../../types/customer";
export function SeatMap({
  seats,
  selected,
  onToggle,
  disabled,
}: {
  seats: Seat[];
  selected: number[];
  onToggle: (id: number) => void;
  disabled: boolean;
}) {
  const floors = [...new Set(seats.map((seat) => seat.floor))].sort(
    (a, b) => a - b,
  );
  return (
    <>
      <div className="seat-legend">
        <span>
          <i />
          Còn trống
        </span>
        <span>
          <i className="selected" />
          Đang chọn
        </span>
        <span>
          <i className="unavailable" />
          Không khả dụng
        </span>
      </div>
      <div className="seat-floors">
        {floors.map((floor) => {
          const group = seats.filter((seat) => seat.floor === floor);
          const minRow = Math.min(...group.map((s) => s.row));
          const minColumn = Math.min(...group.map((s) => s.column));
          return (
            <section className="seat-floor" key={floor}>
              <h3>Tầng {floor}</h3>
              <div className="seat-scroll">
                <div className="bus-interior">
                  <div className="bus-front">ĐẦU XE</div>
                  <div
                    className="seat-grid"
                    style={{
                      gridTemplateColumns: `repeat(${Math.max(...group.map((s) => s.column)) - minColumn + 1}, 52px)`,
                    }}
                  >
                    {group.map((seat) => (
                      <button
                        type="button"
                        key={seat.tripSeatId}
                        className={`seat ${selected.includes(seat.tripSeatId) ? "selected" : ""}`}
                        style={{
                          gridRow: seat.row - minRow + 1,
                          gridColumn: seat.column - minColumn + 1,
                        }}
                        disabled={
                          disabled ||
                          !seat.available ||
                          (selected.length >= 5 &&
                            !selected.includes(seat.tripSeatId))
                        }
                        aria-pressed={selected.includes(seat.tripSeatId)}
                        aria-label={`Ghế ${seat.seatCode}, ${seat.seatType}, ${seat.available ? "còn trống" : "không khả dụng"}`}
                        title={seat.seatType}
                        onClick={() => onToggle(seat.tripSeatId)}
                      >
                        <Armchair size={21} />
                        <span>{seat.seatCode}</span>
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </section>
          );
        })}
      </div>
    </>
  );
}
