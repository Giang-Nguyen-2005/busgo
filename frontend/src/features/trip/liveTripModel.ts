export interface LiveTripState {
  lifecycle: string; delayMinutes: number; delayReason: string | null; label: string;
  scheduledDepartureAt: string; scheduledArrivalAt: string;
  expectedDepartureAt: string; expectedArrivalAt: string;
  selectedPickupScheduledAt: string | null; selectedPickupEstimatedAt: string | null;
  actualDepartureAt: string | null; actualArrivalAt: string | null; operationalUpdatedAt: string | null;
}
export function livePollingInterval(lifecycle?: string, bookingStatus?: string): number | false {
  return !lifecycle || lifecycle === "COMPLETED" || lifecycle === "CANCELLED" || bookingStatus === "CANCELLED" ? false : 45_000;
}
