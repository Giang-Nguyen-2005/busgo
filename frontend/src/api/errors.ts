import axios from "axios";
import type { ApiError } from "../types/api";
const messages: Record<string, string> = {
  TRIP_NOT_FOUND: "Không tìm thấy chuyến xe.",
  TRIP_NOT_BOOKABLE: "Chuyến xe hiện không thể đặt. Vui lòng tìm chuyến khác.",
  INVALID_PICKUP_STOP: "Điểm đón không hợp lệ.",
  INVALID_DROPOFF_STOP: "Điểm đến không hợp lệ.",
  INVALID_ROUTE_DIRECTION: "Điểm đến phải nằm sau điểm đón trên hành trình.",
  SEAT_NOT_AVAILABLE:
    "Ghế vừa được khách khác chọn. Sơ đồ ghế đang được cập nhật, vui lòng chọn lại.",
  SEAT_HOLD_NOT_FOUND:
    "Không còn tìm thấy lượt giữ chỗ. Nếu bạn vừa đặt vé, hãy kiểm tra Vé của tôi trước khi đặt lại.",
  SEAT_HOLD_EXPIRED: "Thời gian giữ chỗ đã hết. Vui lòng chọn lại ghế.",
  SEAT_HOLD_ACCESS_DENIED: "Bạn không có quyền sử dụng lượt giữ chỗ này.",
  BOOKING_NOT_FOUND: "Không tìm thấy đặt vé của bạn.",
  BOOKING_NOT_PAYABLE:
    "Đặt vé này không thể thanh toán. Vui lòng kiểm tra lại chi tiết.",
  TICKET_NOT_AVAILABLE:
    "Vé chưa sẵn sàng. Vui lòng kiểm tra trạng thái thanh toán.",
  INVALID_CREDENTIALS:
    "Email hoặc mật khẩu không đúng, hoặc tài khoản không còn hoạt động.",
  EMAIL_ALREADY_EXISTS: "Email này đã được đăng ký.",
  INVALID_PASSWORD: "Mật khẩu cần ít nhất 8 ký tự và tối đa 72 byte UTF-8.",
  UNAUTHORIZED: "Vui lòng đăng nhập để tiếp tục.",
  ACCESS_TOKEN_EXPIRED: "Phiên đăng nhập đã hết hạn.",
  REFRESH_TOKEN_INVALID: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
  ACCESS_DENIED: "Tài khoản không có quyền thực hiện thao tác này.",
  VALIDATION_ERROR:
    "Thông tin chưa hợp lệ. Vui lòng kiểm tra các trường đã nhập.",
};
export const errorCode = (error: unknown) =>
  axios.isAxiosError<ApiError>(error) ? error.response?.data?.code : undefined;
export function errorMessage(error: unknown) {
  if (axios.isAxiosError(error) && !error.response)
    return "Không thể kết nối máy chủ. Vui lòng kiểm tra mạng rồi thử lại.";
  return (
    messages[errorCode(error) || ""] ||
    "Không thể hoàn tất yêu cầu. Vui lòng thử lại."
  );
}
