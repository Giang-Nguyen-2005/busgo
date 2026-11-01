import type { BookingStatus } from "../../types/customer";

export const customerAccess = (roles: string[] = []) => roles.includes("CUSTOMER");
export interface SearchJourney { pickupLocationId: number; pickupLabel: string; dropoffLocationId: number; dropoffLabel: string; departureDate: string }
export function readJourney(params: URLSearchParams): SearchJourney {
  return { pickupLocationId: Number(params.get("pickupLocationId")) || 0, pickupLabel: params.get("pickupLabel") || "", dropoffLocationId: Number(params.get("dropoffLocationId")) || 0, dropoffLabel: params.get("dropoffLabel") || "", departureDate: params.get("departureDate") || "" };
}
export const swapJourney = (v: SearchJourney): SearchJourney => ({ ...v, pickupLocationId: v.dropoffLocationId, pickupLabel: v.dropoffLabel, dropoffLocationId: v.pickupLocationId, dropoffLabel: v.pickupLabel });
export function journeyParams(values: SearchJourney, previous = new URLSearchParams()) {
  const next = new URLSearchParams(previous);
  Object.entries(values).forEach(([key, value]) => next.set(key, String(value)));
  next.delete("page");
  return next;
}
export const journeyTitle = (v: SearchJourney) => `${v.pickupLabel || (v.pickupLocationId ? `Điểm đón #${v.pickupLocationId}` : "Điểm đón")} → ${v.dropoffLabel || (v.dropoffLocationId ? `Điểm trả #${v.dropoffLocationId}` : "Điểm trả")}`;
export function searchReturn(value: string | null) {
  return value?.startsWith("/search?") ? value : "/search";
}
export const seatTypeLabel = (type: string) => ({ STANDARD: "Chỗ tiêu chuẩn", SLEEPER: "Giường nằm", SEAT: "Ghế ngồi" })[type] || "Chỗ trên xe";
export const seatLimitDisabled = (available: boolean, selected: boolean, count: number) => available && !selected && count >= 5;
export const passengerLabel = (name: string | null) => name || "Chưa cung cấp tên khách trên chỗ";
export const historyEmpty = (filtered: boolean) => filtered ? "Không có đặt vé khớp bộ lọc" : "Bạn chưa có đặt vé nào";
export const paymentPresentation = (status: BookingStatus, submitting = false) => submitting ? "Đang xác nhận thanh toán mô phỏng…" : status === "CONFIRMED" ? "Đặt vé đã xác nhận" : status === "PENDING" ? "Chờ thanh toán mô phỏng" : "Đặt vé không thể thanh toán";
export const ticketHeading = (fresh: boolean) => fresh ? "Đặt vé thành công!" : "Vé điện tử của bạn";
export const readonlyEmailExplanation = "Email dùng để đăng nhập và hiện chưa hỗ trợ thay đổi.";
export const passwordCompletion = "Đã đổi mật khẩu thành công. Vui lòng đăng nhập lại.";
export const fallbackBusAlt = "Ảnh xe khách minh họa, không phải ảnh xác nhận của nhà xe";

// A discovered route does not imply a departure date or available service.
export function routeSearch(route: { id: number; name: string; pickupLocationId: number; dropoffLocationId: number }, operatorId?: number) {
  const params = new URLSearchParams({ pickupLocationId: String(route.pickupLocationId), dropoffLocationId: String(route.dropoffLocationId), routeName: route.name });
  if (operatorId) params.set("operatorId", String(operatorId));
  return `/search?${params}`;
}
export function profileReturn(value: string | null) {
  return value && /^\/(?:search(?:\?|$)|trips\/\d+\?)/.test(value) && !/[\\\r\n]/.test(value) ? value : "/";
}
