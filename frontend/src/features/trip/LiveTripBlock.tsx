import type { LiveTripState } from "./liveTripModel";
import { dateTime } from "../../utils/format";

export function LiveTripBlock({ state }: { state?: LiveTripState }) {
  if (!state) return null;
  const active = state.lifecycle !== "COMPLETED" && state.lifecycle !== "CANCELLED";
  return <section className="detail-section" aria-label="Tình trạng chuyến xe"><h2>{state.label}</h2>
    {active && (state.lifecycle !== "DEPARTED" || state.selectedPickupEstimatedAt) && <><p>Dự kiến {state.selectedPickupEstimatedAt ? "đón" : "khởi hành"}: <strong>{dateTime(state.selectedPickupEstimatedAt ?? state.expectedDepartureAt)}</strong></p>
      <p>Theo lịch: {dateTime(state.selectedPickupScheduledAt ?? state.scheduledDepartureAt)}</p></>}
    {active && <p>Dự kiến đến: {dateTime(state.expectedArrivalAt)}</p>}
    {state.actualDepartureAt && <p>Khởi hành thực tế: {dateTime(state.actualDepartureAt)}</p>}
    {state.actualArrivalAt && <p>Đến lúc: {dateTime(state.actualArrivalAt)}</p>}
    {active && state.delayReason && <p>Lý do: {state.delayReason}</p>}
    {state.operationalUpdatedAt && <p className="fine-print">Cập nhật: {dateTime(state.operationalUpdatedAt)} · Giờ Việt Nam</p>}
  </section>;
}
