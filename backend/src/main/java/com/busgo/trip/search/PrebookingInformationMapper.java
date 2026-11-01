package com.busgo.trip.search;

import static com.busgo.common.time.BusGoTime.api;
import static com.busgo.trip.search.TripSearchDtos.*;

import com.busgo.location.entity.Location;
import com.busgo.trip.entity.Trip;
import com.busgo.trip.entity.TripStop;

/** Read-only explanation of M17/M20/M21, never a booking eligibility decision. */
public final class PrebookingInformationMapper {
    private PrebookingInformationMapper() {}

    public static PrebookingInformation of(Trip trip, TripStop pickup, TripStop dropoff) {
        var type = trip.getBus().getBusType();
        var departure = pickup.getPlannedDepartureTime();
        return new PrebookingInformation(location(pickup.getLocation()), location(dropoff.getLocation()),
                new VehicleInformation(type.getSeatCount(), text(type.getDescription())),
                new PrebookingPolicy(departure == null ? null : api(departure.minusHours(6)),
                    "Hủy toàn bộ: áp dụng có điều kiện cho đặt vé chờ thanh toán hoặc đã xác nhận, khi chuyến còn ở trạng thái chờ chạy hoặc đang đón khách; điểm đón chưa đóng và không có khách còn vé đã điểm danh, lên xe hoặc ghi nhận vắng mặt.",
                    "Hủy một phần: chuyến phải còn chờ chạy, chọn hành khách đủ điều kiện và giữ lại ít nhất một hành khách. Không hủy người đã điểm danh, lên xe hoặc ghi nhận vắng mặt.",
                    "Đổi ghế/chuyến: chuyến phải còn chờ chạy, nhà xe đang hoạt động, điểm đón chưa đóng và không có khách còn vé đã điểm danh, lên xe hoặc vắng mặt. Ghế/chuyến thay thế và chênh lệch giá cần được hệ thống kiểm tra.",
                    "Hoàn tiền và thu thêm hiện là mô phỏng, không chuyển tiền ngân hàng. Số tiền được hệ thống tính và hiển thị trước khi xác nhận.",
                    "Hạn yêu cầu của khách là 6 giờ trước giờ đón theo lịch của chặng đã chọn. Trạng thái đặt vé, hạn thanh toán, nhà xe/tuyến, điểm đón, điểm danh và thay đổi đang chờ có thể hạn chế thao tác. Sau đặt vé, xem điều kiện tại chi tiết đặt vé; hệ thống kiểm tra lại khi xác nhận."));
    }

    private static LocationContext location(Location location) {
        // Do not infer an exact boarding address from a city name or an unverified address.
        return new LocationContext(text(location.getProvince()), text(location.getDistrict()));
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
