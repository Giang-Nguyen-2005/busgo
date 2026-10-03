import assert from "node:assert/strict";
import { test } from "node:test";
import { register } from "node:module";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
register('./helpers/tsx-loader.mjs', import.meta.url);
const { AttendanceContent } = await import("../src/features/operator/CrewBoarding.tsx");
const { canAccessOperatorPath } = await import("../src/features/auth/access.ts");
const base = { bookingItemId: 1, bookingId: 2, bookingCode: "PHONE", seatCode: "A01", passengerName: "Passenger", phone: "0901234567", paymentMethod: "PAY_ON_BOARD", paymentStatus: "PENDING", ticketId: null, ticketCode: null, boardingStatus: null, pickupStopId: 1, pickupName: "Origin", dropoffName: "Destination" };
const render = (row, props = {}) => renderToStaticMarkup(React.createElement(MemoryRouter, null, React.createElement(AttendanceContent, { rows: [row], manage: true, tripStatus: "BOARDING", closedStops: [], pending: false, action: () => {}, ...props })));
test("unpaid passenger remains visible without an invented ticket or attendance", () => {
  const html = render(base); assert.match(html, /Chưa thu tiền/); assert.match(html, /Chưa ghi nhận/); assert.match(html, /Ghi nhận đã thu tiền/); assert.match(html, /disabled="">Check-in/);
});
test("PHONE unpaid no-show is available while check-in stays blocked; terminal no-show hides collection", () => {
  const row = { ...base, source: "PHONE", paymentBlocked: false };
  assert.match(render(row), /<button class="secondary">Vắng mặt<\/button>/);
  assert.match(render(row), /disabled="">Check-in/);
  assert.doesNotMatch(render({ ...row, boardingStatus: "NO_SHOW", paymentBlocked: true }), /<button/);
  assert.doesNotMatch(render({ ...row, paymentBlocked: true }), /Ghi nhận đã thu tiền/);
  assert.match(render({ ...row, source: "WEB" }), /disabled="">Vắng mặt/);
  assert.match(render(row, { closedStops: [1] }), /disabled="">Vắng mặt/);
});
test("paid check-in enables board, terminal attendance has no mutation controls", () => {
  assert.match(render({ ...base, paymentStatus: "PAID", ticketId: 9, boardingStatus: "CHECKED_IN" }), /<button>Lên xe<\/button>/);
  for(const boardingStatus of ["BOARDED","NO_SHOW"]) assert.doesNotMatch(render({ ...base, paymentStatus: "PAID", ticketId: 9, boardingStatus }), /<button/);
  assert.match(render({ ...base, paymentStatus: "PAID", ticketId: 9, boardingStatus: "CHECKED_IN" }, { closedStops: [1] }), /disabled="">Lên xe/);
});
test("staff reads operational employees and has no attendance mutation controls", () => {
  assert.equal(canAccessOperatorPath(["OPERATOR_STAFF"],"/operator/employees"), true);
  assert.doesNotMatch(render(base, { manage: false }), /<button/);
  assert.equal(canAccessOperatorPath(["SYSTEM_ADMIN"],"/operator/employees"), false);
});
