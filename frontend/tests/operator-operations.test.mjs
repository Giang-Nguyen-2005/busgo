import assert from "node:assert/strict";
import { test } from "node:test";
import { register } from "node:module";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";

register('./helpers/tsx-loader.mjs', import.meta.url);
const { bookingFilters, updateBookingFilter, operationLabel, nextTripAction, operationErrorMessage, occupancyMatrix } = await import("../src/features/operator/operations.ts");
const { BookingDetailContent, OperatorBookingsPage } = await import("../src/pages/operator/OperatorBookingsPages.tsx");
const { ManifestContent, OccupancyContent } = await import("../src/pages/operator/OperatorTripOperationsPages.tsx");
const { OperationsQueryState, OperationsError } = await import("../src/features/operator/OperationsShared.tsx");
const { Pagination } = await import("../src/features/operator/shared.tsx");
const { QueryClient, QueryClientProvider } = await import("@tanstack/react-query");
const { operatorApi } = await import("../src/api/operatorApi.ts");
const { apiClient } = await import("../src/api/client.ts");
const { useTripStatusMutation } = await import("../src/features/operator/queries.ts");
const { AuthContext } = await import("../src/features/auth/AuthProvider.tsx");
const { TripStatusAction } = await import("../src/features/operator/TripStatusAction.tsx");
const { paymentStatusLabel, dateTime } = await import("../src/utils/format.ts");
const render = (Component, props) => renderToStaticMarkup(React.createElement(MemoryRouter, null, React.createElement(Component, props)));
const stop = { tripStopId: 1, locationId: 2, name: "Bến A", time: null };
const contact = { name: "Người đặt vé", phone: "0901234567", email: null };
const timestamp = "2026-09-26T18:00:00Z";
const booking = {
  bookingId: 9, bookingCode: "BG-9", status: "CONFIRMED", route: { id: 1, name: "Tuyến A" },
  trip: { id: 4, status: "SCHEDULED", departureTime: timestamp, estimatedArrivalTime: timestamp },
  customer: { id: 1, fullName: "Chủ tài khoản", email: "a@example.com", phone: null }, contact,
  pickup: stop, dropoff: { ...stop, name: "Bến B" }, totalAmount: 100000, createdAt: timestamp, updatedAt: timestamp,
  items: [{ bookingItemId: 1, tripSeatId: 2, seatCode: "A1", passengerName: null, unitPrice: 100000,
    ticket: { id: 1, ticketCode: "T-9", passengerName: "Tên in trên vé", seatCode: "A1", paymentId: 7, createdAt: timestamp } }],
  payments: [{ id: 7, method: "MOCK_QR", status: "PAID", amount: 100000, transactionReference: null, createdAt: timestamp, paidAt: timestamp }],
};

test("booking URL filters serialize valid values and discard malformed inputs", () => {
  const filters = bookingFilters(new URLSearchParams("q=+BG+9+&tripId=42&status=CONFIRMED&paymentStatus=PAID&date=2026-09-27&page=2&size=10"));
  assert.deepEqual(filters, { q: "BG 9", tripId: 42, status: "CONFIRMED", paymentStatus: "PAID", date: "2026-09-27", page: 2, size: 10 });
  const bad = bookingFilters(new URLSearchParams("tripId=-1&status=OTHER&paymentStatus=BAD&date=2026-02-30&page=2147483647&size=13"));
  assert.equal(bad.tripId, undefined); assert.equal(bad.status, undefined); assert.equal(bad.paymentStatus, undefined);
  assert.equal(bad.date, undefined); assert.equal(bad.page, 0); assert.equal(bad.size, 20);
});
test("booking pagination preserves filters and resets when filters or page size change", () => {
  const original = new URLSearchParams("q=BG&tripId=4&page=2&size=10");
  assert.equal(updateBookingFilter(original, "page", "3").get("q"), "BG");
  assert.equal(bookingFilters(updateBookingFilter(original, "page", "3")).page, 3);
  assert.equal(bookingFilters(updateBookingFilter(original, "status", "CONFIRMED")).page, 0);
  assert.equal(updateBookingFilter(original, "size", "50").has("page"), false);
  assert.equal(original.get("page"), "2");
  const first = render(Pagination, { pagination: { page: 0, size: 10, totalPages: 3, totalElements: 25 }, set() {} });
  assert.match(first, /disabled="">Trước/); assert.doesNotMatch(first, /disabled="">Sau/);
  const last = render(Pagination, { pagination: { page: 2, size: 10, totalPages: 3, totalElements: 25 }, set() {} });
  assert.match(last, /disabled="">Sau/); assert.match(last, /25 kết quả/);
});
test("Vietnamese status labels and Vietnam dates", () => {
  for (const status of ["PENDING", "CONFIRMED", "COMPLETED", "CANCELLED", "AVAILABLE", "HELD", "BOOKED", "BLOCKED"]) assert.notEqual(operationLabel(status), status);
  for (const status of ["PENDING", "PAID", "FAILED", "REFUNDED"]) assert.notEqual(paymentStatusLabel(status), status);
  assert.match(dateTime(timestamp), /27\/09\/2026/);
});
test("detail renders null seat identity separately from actual ticket and contact names", () => {
  const html = render(BookingDetailContent, { booking });
  assert.match(html, /Chưa có thông tin riêng/); assert.match(html, /Tên in trên vé/); assert.match(html, /Người đặt vé/);
  assert.match(html, /Khách trên ghế/); assert.match(html, /Tên trên vé/); assert.match(html, /T-9/);
  assert.match(html, /Đã thanh toán/);
  const empty = render(BookingDetailContent, { booking: { ...booking, payments: [], items: [{ ...booking.items[0], ticket: null }] } });
  assert.match(empty, /Chưa có giao dịch thanh toán/); assert.match(empty, /Chưa có vé/);
});
test("manifest empty state and row mapping preserve distinct identities and reused seats", () => {
  assert.match(render(ManifestContent, { manifest: { tripStatus: "SCHEDULED", passengers: [] } }), /Chưa có hành khách từ đặt vé đã xác nhận/);
  const row = { bookingId: 9, bookingCode: "BG-9", bookingStatus: "CONFIRMED", bookingItemId: 1, tripSeatId: 2, seatCode: "A1", pickup: stop, dropoff: { ...stop, name: "Bến B" }, passengerName: null, ticketPassengerName: "Tên trên vé A", contact, paymentStatus: "PAID", ticketCode: "T-9" };
  const html = render(ManifestContent, { manifest: { tripStatus: "BOARDING", passengers: [row, { ...row, bookingItemId: 2, bookingId: 10, bookingCode: "BG-10", passengerName: "Khách riêng" }] } });
  assert.match(html, /Chưa có thông tin riêng/); assert.match(html, /Khách riêng/); assert.match(html, /Tên trên vé A/);
  assert.match(html, /0901234567/); assert.match(html, /operator\/bookings\/10/); assert.doesNotMatch(html, /checkbox/);
});
const occupancy = {
  tripId: 4, tripStatus: "SCHEDULED", seatCount: 1, segmentCount: 2, wholeTripAvailableSeatCount: 0,
  segments: [ { tripSegmentId: 20, segmentOrder: 2, fromName: "B", toName: "C", counts: { available: 1, held: 0, booked: 0, blocked: 0 } }, { tripSegmentId: 90, segmentOrder: 1, fromName: "A", toName: "B", counts: { available: 0, held: 0, booked: 1, blocked: 0 } } ],
  seats: [{ tripSeatId: 2, seatCode: "A1", floor: 1, segments: [{ tripSegmentId: 20, status: "AVAILABLE" }, { tripSegmentId: 90, status: "BOOKED", bookingId: 9, bookingCode: "BG-9" }] }],
};
test("occupancy aligns by segment ID and never collapses a reused seat", () => {
  assert.deepEqual(occupancyMatrix(occupancy).rows[0].cells.map(c => c.status), ["BOOKED", "AVAILABLE"]);
  const html = render(OccupancyContent, { occupancy });
  assert.match(html, /Đã đặt/); assert.match(html, /Còn trống/); assert.match(html, /operator\/bookings\/9/);
  assert.match(html, /chưa chắc đã thanh toán hoặc lên xe/);
  const missing = structuredClone(occupancy); missing.seats[0].segments = [];
  assert.match(render(OccupancyContent, { occupancy: missing }), /Chưa có dữ liệu/);
  const held = structuredClone(occupancy); held.seats[0].segments[0] = { tripSegmentId: 20, status: "HELD", holdExpiresAt: timestamp, userId: "PRIVATE-HOLDER" };
  const heldHtml = render(OccupancyContent, { occupancy: held });
  assert.match(heldHtml, /Hết hạn/); assert.doesNotMatch(heldHtml, /PRIVATE-HOLDER/);
});
test("trip actions are forward-only, terminal-safe and explain operational effects", () => {
  assert.equal(nextTripAction("SCHEDULED").status, "BOARDING"); assert.match(nextTripAction("SCHEDULED").impact, /không thể giữ chỗ/);
  assert.equal(nextTripAction("BOARDING").status, "DEPARTED"); assert.match(nextTripAction("BOARDING").impact, /Điểm đón trung gian còn mở/);
  assert.equal(nextTripAction("DEPARTED").status, "COMPLETED");
  assert.equal(nextTripAction("COMPLETED"), null); assert.equal(nextTripAction("CANCELLED"), null);
});
test("known API errors use Vietnamese messages, unknown failures retain a safe fallback", () => {
  for (const code of ["INVALID_TRIP_STATUS_TRANSITION", "PAYMENT_WINDOW_CLOSED", "TRIP_NOT_FOUND", "BOOKING_NOT_FOUND"]) {
    const html = render(OperationsError, { error: { isAxiosError: true, response: { data: { code, message: "Raw backend message" } } } });
    assert.match(html, /role="alert"/); assert.ok(html.includes(operationErrorMessage(code))); assert.ok(!html.includes(code)); assert.doesNotMatch(html, /Raw backend message/);
  }
  assert.equal(operationErrorMessage("UNKNOWN"), operationErrorMessage());
  assert.match(render(OperationsQueryState, { query: { isPending: true }, children: () => "content" }), /Đang tải/);
  assert.match(render(OperationsQueryState, { query: { isError: true, error: new Error(), refetch() {} }, children: () => "content" }), /Thử lại/);
});
test("booking list renders backend pagination and links using cached API data", () => {
  const cache = new QueryClient();
  const filters = bookingFilters(new URLSearchParams("page=1"));
  cache.setQueryData(["operator", "bookings", filters], { data: [{ ...booking, tripId: 4, seatCount: 1, paymentStatus: "PAID" }], pagination: { page: 1, size: 20, totalElements: 21, totalPages: 2 } });
  const html = renderToStaticMarkup(React.createElement(QueryClientProvider, { client: cache }, React.createElement(MemoryRouter, { initialEntries: ["/operator/bookings?page=1"] }, React.createElement(OperatorBookingsPage))));
  assert.match(html, /21 kết quả/); assert.match(html, /operator\/bookings\/9/); assert.match(html, /disabled="">Sau/);
  cache.clear();
});
test("M12 API calls use the existing client, pagination, cancellation and status body", async () => {
  const requests = [];
  const original = apiClient.defaults.adapter;
  apiClient.defaults.adapter = async config => {
    requests.push(config);
    return { config, status: 200, statusText: "OK", headers: {}, data: config.url.endsWith("/bookings") ? { data: [], pagination: { page: 2, size: 10, totalElements: 21, totalPages: 3 } } : { data: { ok: true } } };
  };
  try {
    const signal = new AbortController().signal;
    const result = await operatorApi.bookings({ page: 2, size: 10, q: "BG", tripId: 4 }, signal);
    assert.equal(result.pagination.page, 2); assert.equal(requests[0].params.tripId, 4); assert.equal(requests[0].signal, signal);
    await operatorApi.booking(9); await operatorApi.passengers(4); await operatorApi.occupancy(4); await operatorApi.updateTripStatus(4, "BOARDING");
    assert.deepEqual(requests.map(r => r.url), ["/operator/bookings", "/operator/bookings/9", "/operator/trips/4/passengers", "/operator/trips/4/occupancy", "/operator/trips/4/status"]);
    assert.equal(requests[4].method, "patch"); assert.deepEqual(JSON.parse(requests[4].data), { status: "BOARDING" });
  } finally { apiClient.defaults.adapter = original; }
});

test("status mutation refreshes trips, occupancy, manifest and bookings on success or conflict", async () => {
  for (const fail of [false, true]) {
    const cache = new QueryClient();
    const invalidated = [];
    cache.invalidateQueries = async ({ queryKey }) => { invalidated.push(queryKey); };
    let mutation;
    function Harness() { mutation = useTripStatusMutation(4); return null; }
    renderToStaticMarkup(React.createElement(QueryClientProvider, { client: cache }, React.createElement(Harness)));
    const original = apiClient.defaults.adapter;
    apiClient.defaults.adapter = async config => {
      if (fail) throw { isAxiosError: true, response: { status: 409, data: { code: "INVALID_TRIP_STATUS_TRANSITION" } } };
      return { config, status: 200, statusText: "OK", headers: {}, data: { data: { tripId: 4, status: "BOARDING" } } };
    };
    try {
      if (fail) await assert.rejects(mutation.mutateAsync("BOARDING"));
      else await mutation.mutateAsync("BOARDING");
      assert.deepEqual(invalidated, [["operator", "trips"], ["operator", "bookings"], ["operator", "crew", 4], ["operator", "attendance", 4], ["operator", "pickups", 4], ["operator", "history", 4]]);
    } finally { apiClient.defaults.adapter = original; cache.clear(); }
  }
});
test("trip controls include accessible confirmation and omit terminal actions", () => {
  const cache = new QueryClient();
  const html = status => renderToStaticMarkup(React.createElement(QueryClientProvider, { client: cache }, React.createElement(AuthContext.Provider, { value: { user: { roles: ["OPERATOR_ADMIN"] } } }, React.createElement(TripStatusAction, { id: 4, status }))));
  assert.match(html("SCHEDULED"), /<dialog/);
  assert.match(html("SCHEDULED"), /aria-describedby="trip-status-impact"/);
  assert.match(html("BOARDING"), /Điểm đón trung gian còn mở/);
  assert.doesNotMatch(html("COMPLETED"), /<button|<dialog/);
  assert.doesNotMatch(html("CANCELLED"), /<button|<dialog/);
  cache.clear();
});
