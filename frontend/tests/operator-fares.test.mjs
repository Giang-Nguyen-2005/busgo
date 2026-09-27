import assert from "node:assert/strict";
import { test } from "node:test";
import { fareSchema } from "../src/features/operator/fareSchema.ts";

// IDs deliberately differ from stop order: direction must use order, not IDs.
const stops = [
  { id: 90, stopOrder: 1, status: "ACTIVE" },
  { id: 20, stopOrder: 2, status: "ACTIVE" },
  { id: 10, stopOrder: 3, status: "ACTIVE" },
  { id: 60, stopOrder: 4, status: "INACTIVE" },
];
const schema = fareSchema(stops);
const fare = (from, to, price = 100000) => ({
  fromRouteStopId: from,
  toRouteStopId: to,
  price,
});

test("retains the complete fare set, including unchanged and edited rows", () => {
  const existing = [fare(90, 20), fare(90, 10), fare(20, 10)];
  const edited = existing.map((row, i) =>
    i === 1 ? { ...row, price: 120000 } : row,
  );
  assert.deepEqual(schema.parse({ fares: edited }), { fares: edited });
  assert.deepEqual(schema.parse({ fares: edited.filter((_, i) => i !== 1) }), {
    fares: [existing[0], existing[2]],
  });
});

test("accepts an intentionally empty replacement set", () => {
  assert.deepEqual(schema.parse({ fares: [] }), { fares: [] });
  assert.equal(schema.safeParse({}).success, false);
});

test("requires active, owned stops in forward order", () => {
  assert.equal(schema.safeParse({ fares: [fare(90, 10)] }).success, true);
  for (const row of [fare(10, 90), fare(20, 20), fare(90, 60), fare(90, 999)]) {
    assert.equal(schema.safeParse({ fares: [row] }).success, false);
  }
});

test("rejects duplicate pairs even with different prices", () => {
  assert.equal(
    schema.safeParse({ fares: [fare(90, 10), fare(90, 10, 300000)] }).success,
    false,
  );
});

test("matches the backend positive amount, precision and maximum constraints", () => {
  for (const price of [0.01, 100000, 123.45, 9999999999.99]) {
    assert.equal(
      schema.safeParse({ fares: [fare(90, 10, price)] }).success,
      true,
    );
  }
  for (const price of [0, -1, 0.001, 123.456, 10000000000, NaN, Infinity]) {
    assert.equal(
      schema.safeParse({ fares: [fare(90, 10, price)] }).success,
      false,
    );
  }
});
