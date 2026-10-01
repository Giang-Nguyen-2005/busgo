import type { RouteObject } from "react-router-dom";
export const operatorTripRoute: RouteObject = {
  path: "trips/:tripId",
  lazy: async () => ({ Component: (await import("../features/operator/TripWorkspace")).TripWorkspace }),
  children: [
    { index: true, lazy: async () => ({ Component: (await import("../pages/operator/OperatorTripsPages")).OperatorTripDetailPage }) },
    { path: "seats", lazy: async () => ({ Component: (await import("../pages/operator/OperatorSeatsPage")).OperatorSeatsPage }) },
    { path: "passengers", lazy: async () => ({ Component: (await import("../pages/operator/OperatorTripOperationsPages")).OperatorPassengersPage }) },
    { path: "occupancy", lazy: async () => ({ Component: (await import("../pages/operator/OperatorTripOperationsPages")).OperatorOccupancyPage }) },
  ],
};
