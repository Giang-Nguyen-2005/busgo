import { domainErrorMessage } from "../../api/errors";
import { statusLabels } from "../../utils/status";
import type { BookingFilters, TripStatus, TripOccupancy } from "../../types/operator";

export const bookingStatuses = ["PENDING", "CONFIRMED", "CANCELLED", "COMPLETED"] as const;
export const paymentStatuses = ["PENDING", "PAID", "FAILED", "REFUNDED"] as const;
const labels: Record<string, string> = {
  ...statusLabels.booking,
  AVAILABLE: "Còn trống", HELD: "Đang giữ chỗ", BOOKED: "Đã đặt", BLOCKED: "Đã khóa", MISSING: "Chưa có dữ liệu", PAID: "Đã thanh toán", FAILED: "Thất bại", REFUNDED: "Đã hoàn tiền",
};
export const operationLabel = (status: string) => labels[status] || "Chưa xác định";
export const seatPassenger = (name: string | null) => name || "Chưa có thông tin riêng";
export const manifestEmptyText = "Chưa có hành khách từ đặt vé đã xác nhận";
export function bookingFilters(params: URLSearchParams): BookingFilters {
  const size = [10, 20, 50, 100].includes(Number(params.get("size"))) ? Number(params.get("size")) : 20;
  const rawPage = Number(params.get("page"));
  const rawId = params.get("tripId") || "";
  const date = params.get("date") || "";
  return {
    q: params.get("q")?.trim() || undefined,
    tripId: /^\d+$/.test(rawId) && Number.isSafeInteger(Number(rawId)) && Number(rawId) > 0 ? Number(rawId) : undefined,
    status: bookingStatuses.find(s => s === params.get("status")),
    paymentStatus: paymentStatuses.find(s => s === params.get("paymentStatus")),
    date: /^\d{4}-\d{2}-\d{2}$/.test(date) && Number.isFinite(Date.parse(date)) && new Date(date).toISOString().slice(0, 10) === date ? date : undefined,
    page: Number.isInteger(rawPage) && rawPage >= 0 && rawPage <= Math.floor(2147483647 / size) ? rawPage : 0,
    size,
  };
}
export function updateBookingFilter(params: URLSearchParams, key: string, value: string) {
  const next = new URLSearchParams(params);
  value ? next.set(key, value) : next.delete(key);
  if (key !== "page") next.delete("page");
  return next;
}
export function occupancyMatrix(data: TripOccupancy) {
  const segments = [...data.segments].sort((a, b) => a.segmentOrder - b.segmentOrder);
  return { segments, rows: data.seats.map(seat => ({ seat,
    cells: segments.map(segment => seat.segments.find(cell => cell.tripSegmentId === segment.tripSegmentId)),
  })) };
}
export function nextTripAction(status: TripStatus) {
  const actions = {
    SCHEDULED: { status: "BOARDING" as const, label: "Bắt đầu đón khách", impact: "Chuyến chuyển sang đón khách. Khách hàng sẽ không thể giữ chỗ hoặc tạo đặt vé mới." },
    BOARDING: { status: "DEPARTED" as const, label: "Khởi hành", impact: "Cần đóng điểm đón đầu chuyến. Điểm đón trung gian còn mở vẫn cho phép thu tiền và lên xe sau khởi hành." },
    DEPARTED: { status: "COMPLETED" as const, label: "Hoàn thành chuyến", impact: "Cần ghi nhận lên xe hoặc vắng mặt cho mọi hành khách và đóng tất cả điểm đón. Chuyến hoàn thành không thể chuyển trạng thái tiếp." },
  };
  return status === "COMPLETED" || status === "CANCELLED" ? null : actions[status];
}
export const operationErrorMessage = domainErrorMessage;

export function operationEventLabel(action: string) {
  if (action.startsWith("TRIP_")) return statusLabels.trip[action.slice(5) as keyof typeof statusLabels.trip] || "Cập nhật chuyến";
  const labels: Record<string, string> = { CREW_ASSIGNED: "Phân công nhân sự", CREW_RELEASED: "Gỡ phân công", CHECK_IN: "Check-in", BOARD: "Lên xe", NO_SHOW: "Vắng mặt", PICKUP_CLOSED: "Đóng điểm đón" };
  return labels[action] || "Cập nhật vận hành";
}
export function operationEntityLabel(entity: string) {
  const labels: Record<string, string> = { CREW: "Phân công", TICKET: "Vé", BOOKING_ITEM: "Chỗ đặt", STOP: "Điểm đón", TRIP: "Chuyến" };
  return labels[entity] || "Bản ghi";
}
export function operationHistoryReason(action: string, reason: string | null) {
  // Lifecycle reasons are server-generated enum transitions; user-entered notes stay intact.
  if (!reason) return "";
  return action.startsWith("TRIP_") ? reason.split(" -> ").map(value => statusLabels.trip[value as keyof typeof statusLabels.trip] || "Chưa xác định").join(" → ") : reason;
}
