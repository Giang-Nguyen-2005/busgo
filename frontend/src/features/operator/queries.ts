import { useQuery } from "@tanstack/react-query";
import { allPages, operatorApi } from "../../api/operatorApi";
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
