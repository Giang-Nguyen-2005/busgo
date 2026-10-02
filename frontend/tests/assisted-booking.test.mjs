import assert from "node:assert/strict";
import { test } from "node:test";
import { register } from "node:module";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
register('./helpers/tsx-loader.mjs', import.meta.url);
const { PublicPaymentContent } = await import("../src/pages/PublicPaymentPage.tsx");
const { BookingDetailContent, AssistedBookingActions } = await import("../src/pages/operator/OperatorBookingsPages.tsx");
const { OperatorBookingCreatePage } = await import("../src/pages/operator/OperatorBookingCreatePage.tsx");
const { AuthContext } = await import("../src/features/auth/AuthProvider.tsx");
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { canAccessOperatorPath } = await import("../src/features/auth/access.ts");
const render = (Component, props, roles = ["OPERATOR_ADMIN"]) => renderToStaticMarkup(React.createElement(QueryClientProvider, { client: new QueryClient() }, React.createElement(AuthContext.Provider, { value: { user: { roles }, authenticated: true } }, React.createElement(MemoryRouter, null, React.createElement(Component, props)))));
const payment = { bookingCode: "BG-PHONE", operator: "Nhà xe", journey: "Tuyến A", pickup: { name: "A", time: null }, dropoff: { name: "B", time: null }, seats: ["A01"], amount: 100000, method: "QR_TRANSFER", status: "PENDING", mockPayment: true };
const booking = { bookingId: 1, bookingCode: payment.bookingCode, source: "PHONE", paymentMethod: "PAY_ON_BOARD", status: "PENDING", trip: { id: 2, status: "SCHEDULED", departureTime: "2030-09-20T01:00:00Z", estimatedArrivalTime: "2030-09-20T03:00:00Z" }, route: { name: "Tuyến A" }, customer: null, contact: { name: "Caller", phone: "0901234567", email: null }, pickup: payment.pickup, dropoff: payment.dropoff, totalAmount: 100000, createdAt: "2026-10-02T01:00:00Z", updatedAt: "2026-10-02T01:00:00Z", items: [{ bookingItemId: 1, tripSeatId: 1, seatCode: "A01", passengerName: null, unitPrice: 100000, ticket: null }], payments: [] };
test("offline detail renders contact without a fake account, payment or ticket", () => {
  const html = render(BookingDetailContent, { booking });
  assert.match(html, /PHONE/); assert.match(html, /Không có tài khoản BusGo/);
  assert.match(html, /Chưa có giao dịch thanh toán/); assert.match(html, /Chưa có vé/);
  assert.match(html, /Thu tiền khi khách lên xe/);
});
test("public payment is explicitly simulated and paid context directs ticket delivery to operator", () => {
  const html = render(PublicPaymentContent, { payment });
  assert.match(html, /mô phỏng thanh toán/); assert.match(html, /Không chuyển tiền/); assert.match(html, /A01/);
  assert.match(render(PublicPaymentContent, { payment: { ...payment, status: "CONFIRMED" } }), /Liên hệ nhà xe để nhận vé/);
});
test("staff and system admin cannot see assisted mutation controls or open creation", () => {
  for (const role of ["OPERATOR_STAFF", "SYSTEM_ADMIN", "CUSTOMER"]) {
    assert.equal(canAccessOperatorPath([role], "/operator/bookings/new"), false);
    assert.equal(render(AssistedBookingActions, { booking }, [role]), "");
    assert.match(render(OperatorBookingCreatePage, {}, [role]), /Không có quyền/);
  }
  assert.match(render(AssistedBookingActions, { booking }), /Ghi nhận đã thu tiền/);
  assert.match(render(AssistedBookingActions, { booking: { ...booking, paymentMethod: "QR_TRANSFER" } }), /Gửi link thủ công qua Zalo/);
  assert.doesNotMatch(render(AssistedBookingActions, { booking: { ...booking, status: "CONFIRMED" } }), /Ghi nhận đã thu tiền/);
});
