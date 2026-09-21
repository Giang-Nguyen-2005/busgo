export const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
    value,
  );
export const date = (value: string) => {
  const parsed = new Date(
    /^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00+07:00` : value,
  );
  if (!Number.isFinite(parsed.getTime())) return "—";
  return new Intl.DateTimeFormat("vi-VN", {
    timeZone: "Asia/Ho_Chi_Minh",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(parsed);
};
export const dateTime = (value: string) => `${time(value)} • ${date(value)}`;
export const duration = (minutes: number) =>
  `${Math.floor(minutes / 60)} giờ${minutes % 60 ? ` ${minutes % 60} phút` : ""}`;
export const paymentMethodLabel = (value: string) =>
  ({ MOCK_QR: "QR giả lập", CASH: "Tiền mặt" })[value] || "Phương thức khác";
export const paymentStatusLabel = (value: string) =>
  ({
    PAID: "Đã thanh toán",
    PENDING: "Chờ thanh toán",
    REFUNDED: "Đã hoàn tiền",
    FAILED: "Thanh toán chưa thành công",
  })[value] || "Chưa xác định";
export const time = (value: string) =>
  new Intl.DateTimeFormat("vi-VN", {
    timeZone: "Asia/Ho_Chi_Minh",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
export const today = () =>
  new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
export const positiveId = (value: string | null | undefined) =>
  !!value &&
  /^\d+$/.test(value) &&
  Number.isSafeInteger(Number(value)) &&
  Number(value) > 0;
export function safeReturn(value: string | null) {
  if (
    !value ||
    !value.startsWith("/") ||
    value.startsWith("//") ||
    /[\\\r\n]/.test(value)
  )
    return "/";
  const url = new URL(value, window.location.origin);
  return url.origin === window.location.origin &&
    !["/login", "/register"].includes(url.pathname)
    ? url.pathname + url.search
    : "/";
}
export const tripLink = (tripId: number, pickup: number, dropoff: number) =>
  `/trips/${tripId}?pickupLocationId=${pickup}&dropoffLocationId=${dropoff}`;
