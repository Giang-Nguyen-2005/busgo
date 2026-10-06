package com.busgo.notification;

public enum NotificationType {
    BOOKING_CONFIRMED("Đặt vé thành công", Category.BOOKING_PAYMENT, true),
    PAYMENT_SUCCEEDED("Thanh toán thành công", Category.BOOKING_PAYMENT, false),
    PAYMENT_FAILED("Thanh toán không thành công", Category.BOOKING_PAYMENT, false),
    BOOKING_MODIFIED("Chuyến đi của bạn đã được cập nhật", Category.BOOKING_CHANGE, true),
    PARTIAL_CANCELLATION_COMPLETED("Đã hủy một phần vé", Category.BOOKING_CHANGE, true),
    BOOKING_CANCELLED("Đặt vé đã được hủy", Category.BOOKING_CHANGE, true),
    TRIP_REMINDER_24H("Chuyến xe của bạn sẽ khởi hành sau khoảng 24 giờ", Category.TRIP_REMINDER, false),
    TRIP_REMINDER_2H("Chuyến xe của bạn sẽ khởi hành sau khoảng 2 giờ", Category.TRIP_REMINDER, false);

    public enum Category { BOOKING_PAYMENT, BOOKING_CHANGE, TRIP_REMINDER }
    public final String title;
    public final Category category;
    public final boolean operator;
    NotificationType(String title, Category category, boolean operator) {
        this.title=title; this.category=category; this.operator=operator;
    }
}
