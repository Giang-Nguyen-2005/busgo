import axios from "axios";
import type { ApiError } from "../types/api";
const messages: Record<string, string> = {
  INVALID_TRIP_STATUS_TRANSITION: "Trạng thái chuyến đã thay đổi hoặc thao tác không còn hợp lệ. Vui lòng tải lại.",
  BUS_NOT_FOUND: "Không tìm thấy xe trong nhà xe.",
  LICENSE_PLATE_ALREADY_EXISTS: "Biển số xe đã được sử dụng.",
  NOT_FOUND: "Không tìm thấy thông tin yêu cầu.",
  FORBIDDEN: "Tài khoản không có quyền thực hiện thao tác này.",
  PAYMENT_ALREADY_COMPLETED: "Thanh toán đã hoàn tất. Vui lòng tải lại để xem vé.",
  MAINTENANCE_TRIP_CONFLICT: "Xe đã được phân công cho chuyến trùng lịch. Chọn thời gian bảo trì khác; xe của chuyến cố định sau khi tạo.",
  BUS_MAINTENANCE_CONFLICT: "Xe đang bảo trì hoặc có lịch bảo trì trùng thời gian vận hành. Hoàn tất hoặc hủy lịch phù hợp trước khi tiếp tục.",
  INVALID_MAINTENANCE_TRANSITION: "Trạng thái bảo trì đã thay đổi hoặc thao tác không hợp lệ. Tải lại trước khi tiếp tục.",
  MAINTENANCE_WINDOW_ENDED: "Lịch bảo trì đã kết thúc. Hủy lịch và tạo lịch mới để bắt đầu.",
  MAINTENANCE_NOT_FOUND: "Không tìm thấy lịch bảo trì trong nhà xe.",
  FLEET_PLAN_CHANGED: "Lịch chuyến vừa thay đổi. Tải lại và thử lại thao tác đội xe.",
  BOOKING_NOT_CANCELLABLE: "Đặt vé không còn ở trạng thái có thể hủy.",
  CANCELLATION_WINDOW_CLOSED: "Chuyến đã khởi hành hoặc kết thúc; không thể hủy.",
  CANCELLATION_ATTENDANCE_CONFLICT: "Có khách đã điểm danh, lên xe hoặc được ghi nhận vắng mặt; không thể hủy.",
  CANCELLATION_OPERATOR_SUSPENDED: "Nhà xe tạm ngưng. Hủy vé đã thanh toán cần hỗ trợ từ nền tảng.",
  CUSTOMER_CANCELLATION_CUTOFF: "Đã qua hạn hủy: 6 giờ trước giờ đón dự kiến.",
  CANCELLATION_STATE_INCONSISTENT: "Trạng thái thanh toán hoặc vé không nhất quán. Vui lòng liên hệ hỗ trợ.",
  BOOKING_INVENTORY_INCONSISTENT: "Phân bổ ghế không đầy đủ. Thao tác đã được hủy; vui lòng liên hệ hỗ trợ.",
  EMPLOYEE_NOT_FOUND: "Không tìm thấy nhân sự vận hành trong nhà xe.",
  EMPLOYEE_CODE_EXISTS: "Mã nhân sự vận hành đã được sử dụng.",
  STALE_EMPLOYEE: "Nhân sự đã được cập nhật. Tải lại trước khi chỉnh sửa.",
  EMPLOYEE_HAS_ASSIGNMENTS: "Cần gỡ phân công trên các chuyến đang hoạt động trước khi ngừng nhân sự hoặc bỏ năng lực.",
  EMPLOYEE_NOT_ELIGIBLE: "Nhân sự phải đang hoạt động và có năng lực phù hợp nhiệm vụ.",
  DRIVER_PROFILE_REQUIRED: "Vui lòng nhập đầy đủ giấy phép lái xe.",
  DRIVER_LICENCE_EXPIRED: "Giấy phép lái xe phải còn hạn đến hết thời gian vận hành chuyến.",
  CREW_SCHEDULE_CONFLICT: "Nhân sự đã được phân công cho một chuyến trùng thời gian.",
  CREW_NOT_READY: "Chưa sẵn sàng đón khách. Kiểm tra xe và phân công ít nhất một tài xế hợp lệ.",
  DUPLICATE_CREW: "Nhân sự đã có nhiệm vụ này trên chuyến.",
  CREW_WINDOW_CLOSED: "Chỉ thay đổi nhân sự khi chuyến đã lên lịch hoặc đang đón khách.",
  TICKET_NOT_FOUND: "Không tìm thấy vé thuộc chuyến này.",
  TICKET_NOT_ELIGIBLE: "Vé cần thanh toán thành công và đặt vé còn hợp lệ trước khi ghi nhận lên xe.",
  WRONG_PICKUP_STOP: "Vui lòng sử dụng đúng điểm đón trên vé.",
  PICKUP_STOP_NOT_FOUND: "Không tìm thấy điểm đón hợp lệ trên chuyến.",
  BOARDING_WINDOW_CLOSED: "Chỉ ghi nhận khi đang đón khách, hoặc tại điểm trung gian sau khởi hành.",
  PICKUP_CLOSED: "Điểm đón đã đóng; không thể ghi nhận thêm hành khách.",
  CHECK_IN_REQUIRED: "Cần check-in trước khi ghi nhận lên xe.",
  INVALID_BOARDING_TRANSITION: "Hành khách đã lên xe hoặc vắng mặt không thể đổi trạng thái.",
  BOOKING_ATTENDANCE_TERMINAL: "Đặt vé có hành khách đã ghi nhận vắng mặt; không thể thu tiền sau đó.",
  RESERVATION_NOT_ELIGIBLE: "Chỉ đặt vé điện thoại PAY_ON_BOARD chưa thanh toán được ghi nhận vắng mặt khi chưa có vé.",
  PICKUP_UNRESOLVED: "Ghi nhận lên xe cho khách đã trả tiền hoặc vắng mặt cho khách PAY_ON_BOARD không đến, rồi đóng điểm đón.",
  PAYMENT_LINK_NOT_FOUND: "Link thanh toán không hợp lệ hoặc đã được thay thế. Vui lòng liên hệ nhà xe.",
  BUS_SCHEDULE_CONFLICT: "Xe đã được phân công cho chuyến khác trong khoảng thời gian này. Vui lòng chọn xe hoặc giờ khởi hành khác.",
  BUS_NOT_AVAILABLE: "Xe chưa sẵn sàng để tạo chuyến. Vui lòng kiểm tra trạng thái xe.",
  OPERATOR_NOT_FOUND: "Không tìm thấy nhà xe.",
  STAFF_NOT_FOUND: "Không tìm thấy nhân sự trong nhà xe.",
  OPERATOR_CODE_ALREADY_EXISTS: "Mã nhà xe đã được sử dụng.",
  STAFF_CODE_ALREADY_EXISTS: "Mã nhân sự đã được sử dụng trong nhà xe.",
  STAFF_MEMBERSHIP_CONFLICT: "Tài khoản đã có liên kết nhân sự không phù hợp. Vui lòng kiểm tra lại.",
  LAST_OPERATOR_ADMIN_REQUIRED: "Nhà xe phải luôn còn ít nhất một quản trị viên đang hoạt động.",
  OPERATOR_ACTIVATION_NOT_ALLOWED: "Chưa thể kích hoạt nhà xe. Cần ít nhất một quản trị viên nhà xe đang hoạt động và có thể đăng nhập.",
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
  PAYMENT_WINDOW_CLOSED: "Thanh toán không còn khả dụng cho đặt vé này. Vui lòng kiểm tra chi tiết đặt vé.",
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
export const domainErrorMessage = (code?: string) => messages[code || ""] || "Không thể hoàn tất yêu cầu. Vui lòng thử lại.";
export const errorCode = (error: unknown) =>
  axios.isAxiosError<ApiError>(error) ? error.response?.data?.code : undefined;
export function errorMessage(error: unknown) {
  if (axios.isAxiosError(error) && !error.response)
    return "Không thể kết nối máy chủ. Vui lòng kiểm tra mạng rồi thử lại.";
  return domainErrorMessage(errorCode(error));
}
