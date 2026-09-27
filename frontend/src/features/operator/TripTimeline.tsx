import type { RouteStopResponse, TripStopResponse } from "../../types/operator";
import { dateTime } from "../../utils/format";
export function TripTimeline({ stops }: { stops: TripStopResponse[] }) {
  return (
    <ol className="operator-timeline">
      {[...stops]
        .sort((a, b) => a.stopOrder - b.stopOrder)
        .map((s) => (
          <li key={s.id}>
            <strong>
              {s.stopOrder}. {s.locationName}
            </strong>
            <p>
              Đến: {s.plannedArrivalTime ? dateTime(s.plannedArrivalTime) : "—"}{" "}
              · Đi:{" "}
              {s.plannedDepartureTime ? dateTime(s.plannedDepartureTime) : "—"}
            </p>
            <small>
              {s.allowPickup ? "Đón khách" : "Không đón"} ·{" "}
              {s.allowDropoff ? "Trả khách" : "Không trả"}
            </small>
          </li>
        ))}
    </ol>
  );
}
export function RouteStops({ stops }: { stops: RouteStopResponse[] }) {
  return (
    <ol className="operator-timeline">
      {[...stops]
        .sort((a, b) => a.stopOrder - b.stopOrder)
        .map((s) => (
          <li key={s.id}>
            <strong>
              {s.stopOrder}. {s.location.name}
            </strong>
            <p>
              +{s.estimatedOffsetMinutes} phút ·{" "}
              {s.allowPickup ? "Đón khách" : "Không đón"} ·{" "}
              {s.allowDropoff ? "Trả khách" : "Không trả"}
            </p>
          </li>
        ))}
    </ol>
  );
}
