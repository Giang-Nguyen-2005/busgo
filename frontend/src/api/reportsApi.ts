import { get } from "./client";
import "./operatorApi";
import type { ReportFilters, ReportSummary, ReportTable, TripPerformance, RoutePerformance } from "../types/reports";
export const reportsApi = {
  summary: (filters: ReportFilters, signal?: AbortSignal) => get<ReportSummary>("/operator/reports/summary", filters, signal),
  trips: (filters: ReportFilters, signal?: AbortSignal) => get<ReportTable<TripPerformance>>("/operator/reports/trips", filters, signal),
  routes: (filters: ReportFilters, signal?: AbortSignal) => get<ReportTable<RoutePerformance>>("/operator/reports/routes", filters, signal),
};
