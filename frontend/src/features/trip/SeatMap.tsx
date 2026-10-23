import { useId, useState } from "react";
import { seatTypeLabel, seatLimitDisabled } from "../customer/presentation";
import { Armchair } from "lucide-react";
import type { Seat } from "../../types/customer";
export function SeatMap({
  seats,
  selected,
  onToggle,
  disabled,
  floorNavigation = false,
}: {
  seats: Seat[];
  selected: number[];
  onToggle: (id: number) => void;
  disabled: boolean;
  floorNavigation?: boolean;
}) {
  const floors = [...new Set(seats.map((seat) => seat.floor))].sort(
    (a, b) => a - b,
  );
  const id = useId();
  const [chosenFloor, setChosenFloor] = useState<number | null>(null);
  const activeFloor = chosenFloor !== null && floors.includes(chosenFloor) ? chosenFloor : floors[0];
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
      {selected.length >= 5 && <p className="notice info" role="status">Bạn có thể chọn tối đa 5 chỗ. Bỏ chọn một chỗ để đổi lựa chọn; chỗ viền nét đứt vẫn còn trống.</p>}
      {floorNavigation && floors.length > 1 && <div className="floor-navigation" role="group" aria-label="Chọn tầng xe">{floors.map(floor => <button type="button" className="secondary" key={floor} aria-pressed={activeFloor === floor} aria-controls={`${id}-floor-${floor}`} onClick={() => setChosenFloor(floor)}>Tầng {floor}<span>{seats.filter(s => s.floor === floor && selected.includes(s.tripSeatId)).length} đã chọn</span></button>)}</div>}
      <div className="seat-floors">
        {floors.map((floor) => {
          const group = seats.filter((seat) => seat.floor === floor);
          const minRow = Math.min(...group.map((s) => s.row));
          const minColumn = Math.min(...group.map((s) => s.column));
          return (
            <section className="seat-floor" id={`${id}-floor-${floor}`} hidden={floorNavigation && floor !== activeFloor} key={floor}>
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
                        className={`seat ${selected.includes(seat.tripSeatId) ? "selected" : ""} ${!seat.available ? "unavailable" : seatLimitDisabled(seat.available, selected.includes(seat.tripSeatId), selected.length) ? "limit-disabled" : ""}`}
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
                        aria-label={`Chỗ ${seat.seatCode}, ${seatTypeLabel(seat.seatType)}, ${seat.available ? "còn trống" : "không khả dụng"}`}
                        title={seatTypeLabel(seat.seatType)}
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
