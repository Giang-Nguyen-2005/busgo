import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { BookingFilters, TripStatus } from "../../types/operator";
import { allPages, operatorApi } from "../../api/operatorApi";
export const useOperatorBookings = (filters: BookingFilters) => useQuery({
  queryKey: ["operator", "bookings", filters], queryFn: ({ signal }) => operatorApi.bookings(filters, signal),
});
export const useOperatorBooking = (id: number) => useQuery({
  queryKey: ["operator", "bookings", id], queryFn: ({ signal }) => operatorApi.booking(id, signal),
});
export const usePassengers = (id: number) => useQuery({
  queryKey: ["operator", "trips", id, "passengers"], queryFn: ({ signal }) => operatorApi.passengers(id, signal),
});
export const useOccupancy = (id: number) => useQuery({
  queryKey: ["operator", "trips", id, "occupancy"], queryFn: ({ signal }) => operatorApi.occupancy(id, signal),
});
export function useTripStatusMutation(id: number) {
  const cache = useQueryClient();
  return useMutation({
    mutationFn: (status: TripStatus) => operatorApi.updateTripStatus(id, status),
    // Refresh on failure too: a competing operator may have advanced the trip.
    onSettled: async () => {
      await Promise.all([
        cache.invalidateQueries({ queryKey: ["operator", "trips"] }),
        cache.invalidateQueries({ queryKey: ["operator", "bookings"] }),
      ]);
    },
  });
}
export const useBusTypes = () =>
  useQuery({
    queryKey: ["operator", "bus-types"],
    queryFn: ({ signal }) => operatorApi.busTypes(signal),
  });
export const useRouteChoices = () =>
  useQuery({
    queryKey: ["operator", "routes", "choices"],
    queryFn: ({ signal }) => allPages((p) => operatorApi.routes(p, signal)),
  });
export const useBusChoices = () =>
  useQuery({
    queryKey: ["operator", "buses", "choices"],
    queryFn: ({ signal }) => allPages((p) => operatorApi.buses(p, signal)),
  });
