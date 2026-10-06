import test from 'node:test';
import assert from 'node:assert/strict';
import { canReviewPartial } from '../src/features/booking/partialCancellationModel.ts';

const item = (id, cancelled = false, allowed = true) => ({ bookingItemId: id, cancelled, eligibility: { allowed } });
test('partial review requires a nonempty unique subset and leaves an active passenger', () => {
  const all = [item(1), item(2), item(3)];
  assert.equal(canReviewPartial(all, [1, 2]), true);
  for (const selection of [[], [1, 1], [1, 2, 3], [4]]) assert.equal(canReviewPartial(all, selection), false);
});
test('historical cancellations and terminal attendance cannot be selected', () => {
  const all = [item(1, true, false), item(2), item(3, false, false)];
  assert.equal(canReviewPartial(all, [1]), false);
  assert.equal(canReviewPartial(all, [3]), false);
  assert.equal(canReviewPartial(all, [2]), true);
  assert.equal(canReviewPartial([item(1, true, false), item(2)], [2]), false);
});
