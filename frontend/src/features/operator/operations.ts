import type { BookingFilters, TripStatus, TripOccupancy } from "../../types/operator";

export const bookingStatuses = ["PENDING", "CONFIRMED", "CANCELLED", "COMPLETED"] as const;
export const paymentStatuses = ["PENDING", "PAID", "FAILED", "REFUNDED"] as const;
const labels: Record<string, string> = {
  PENDING: "Chờ xác nhận", CONFIRMED: "Đã xác nhận", CANCELLED: "Đã hủy", COMPLETED: "Hoàn thành",
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
export function operationErrorMessage(code?: string) {
  const messages: Record<string, string> = {
    CREW_NOT_READY: "Chưa sẵn sàng đón khách. Kiểm tra xe và phân công ít nhất một tài xế hợp lệ.",
    PICKUP_UNRESOLVED: "Cần ghi nhận lên xe hoặc vắng mặt cho mọi hành khách và đóng các điểm đón trước khi tiếp tục.",
    ACCESS_DENIED: "Tài khoản không có quyền truy cập hoặc nhà xe đã ngừng hoạt động.",
    INVALID_TRIP_STATUS_TRANSITION: "Trạng thái chuyến đã thay đổi hoặc thao tác không còn hợp lệ. Vui lòng kiểm tra trạng thái mới nhất.",
    PAYMENT_WINDOW_CLOSED: "Đã hết thời gian xác nhận thanh toán cho chuyến này.",
    TRIP_NOT_FOUND: "Không tìm thấy chuyến xe trong nhà xe của bạn.",
    BOOKING_NOT_FOUND: "Không tìm thấy đặt vé trong nhà xe của bạn.",
  };
  return messages[code || ""] || "Không thể hoàn tất yêu cầu. Vui lòng thử lại.";
}
