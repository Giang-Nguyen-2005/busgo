// Domain-specific presentation: PENDING means different things for a booking and a payment.
export const statusLabels = {
  booking: { PENDING: "Đã giữ chỗ", CONFIRMED: "Đã xác nhận", CANCELLED: "Đã hủy", COMPLETED: "Hoàn tất" },
  payment: { PENDING: "Chưa thanh toán", PAID: "Đã thanh toán", REFUNDED: "Đã hoàn tiền mô phỏng", FAILED: "Thanh toán chưa thành công" },
  ticket: { VALID: "Còn hiệu lực", VOID: "Đã vô hiệu · Không dùng để lên xe" },
  trip: { SCHEDULED: "Đã lên lịch", BOARDING: "Đang đón khách", DEPARTED: "Đang chạy", COMPLETED: "Hoàn tất", CANCELLED: "Đã hủy" },
  attendance: { EXPECTED: "Chưa ghi nhận", CHECKED_IN: "Đã check-in", BOARDED: "Đã lên xe", NO_SHOW: "Vắng mặt" },
  bus: { AVAILABLE: "Sẵn sàng", MAINTENANCE: "Bảo trì", INACTIVE: "Ngừng hoạt động" },
  maintenance: { SCHEDULED: "Đã lên lịch", IN_PROGRESS: "Đang bảo trì", COMPLETED: "Hoàn tất", CANCELLED: "Đã hủy" },
  operator: { ACTIVE: "Hoạt động", INACTIVE: "Ngừng hoạt động" },
  user: { ACTIVE: "Hoạt động", INACTIVE: "Ngừng hoạt động", LOCKED: "Đã khóa" },
} as const;
export type StatusDomain = keyof typeof statusLabels;
export function statusPresentation(domain: StatusDomain, status: string) {
  const label = (statusLabels[domain] as Record<string, string>)[status];
  const tone = !label ? "neutral" : ["CANCELLED", "VOID", "FAILED", "LOCKED"].includes(status) ? "danger"
    : ["PENDING", "MAINTENANCE", "IN_PROGRESS", "NO_SHOW"].includes(status) ? "warning"
    : ["INACTIVE", "EXPECTED"].includes(status) ? "neutral"
    : ["SCHEDULED", "BOARDING", "DEPARTED", "REFUNDED"].includes(status) ? "info" : "success";
  return { label: label || "Chưa xác định", tone };
}
