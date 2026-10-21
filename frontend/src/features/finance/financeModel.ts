export const financeLabels: Record<string, string> = {
  PLATFORM_MEDIATED_SIMULATED: "Qua nền tảng (mô phỏng)",
  OPERATOR_RECORDED_SIMULATED: "Nhà xe ghi nhận (mô phỏng)",
  UNKNOWN_OR_UNVERIFIED: "Chưa xác minh bên thu",
  COLLECTION: "Thu tiền", REFUND: "Hoàn tiền", DRAFT: "Bản nháp", ACTIVE: "Đã kích hoạt",
  CLEAN: "Khớp số liệu", EXCEPTIONS: "Cần kiểm tra",
  NOT_CHECKED: "Xem lịch sử đối soát để kiểm tra kỳ này",
  MISSING_EVENT: "Thiếu bút toán", POLICY_SNAPSHOT_MISMATCH: "Sai chính sách hoa hồng",
  SOURCE_LINK_MISMATCH: "Sai liên kết nguồn", SOURCE_AMOUNT_OR_TIME_MISMATCH: "Sai số tiền hoặc thời điểm",
  CHANNEL_MISMATCH: "Sai bên thu", CHANNEL_UNVERIFIED: "Chưa xác minh bên thu",
  COMMISSION_UNPRICED: "Chưa cấu hình hoa hồng", REFUND_ALLOCATION_MISMATCH: "Sai phân bổ hoàn tiền",
  ALLOCATION_IMBALANCE: "Công nợ không cân đối",
  FARE_CONTEXT_UNAVAILABLE: "Chưa xác định được giá gốc và giảm giá tại lúc thu",
};
export const financeLabel = (value: string) => financeLabels[value] || "Cần kiểm tra";
export const financeMoney = (value: number | null) => value == null ? "Chưa xác định" :
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 2 }).format(value);
export const financeDate = (value: string) => new Intl.DateTimeFormat("vi-VN", {
  day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit", timeZone: "Asia/Ho_Chi_Minh",
}).format(new Date(value));
export function validFinancePeriod(from: string, to: string) {
  const days = (Date.parse(to) - Date.parse(from)) / 86400000;
  return Number.isFinite(days) && days >= 0 && days <= 92;
}
export function policyLifecycle(state: string, from: string, to: string | null, now = Date.now()) {
  if (state === "DRAFT") return "Bản nháp";
  if (Date.parse(from) > now) return "Đã kích hoạt · Sắp áp dụng";
  if (to && Date.parse(to) <= now) return "Đã kích hoạt · Đã hết hiệu lực";
  return "Đã kích hoạt · Đang áp dụng";
}
