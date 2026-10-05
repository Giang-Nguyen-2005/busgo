import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { cashLabel, selectionComplete, modificationLabels } from '../src/features/booking/modificationModel.ts';
test('paid positive and negative quote labels distinguish simulated collection and refund', () => {
  assert.match(cashLabel({ newAmountDue: 0, collectionRequired: 70000, refundRequired: 0 }), /thu thêm.*mô phỏng/);
  assert.match(cashLabel({ newAmountDue: 0, collectionRequired: 0, refundRequired: 70000 }), /hoàn lại.*mô phỏng/);
});
test('unpaid amount due takes precedence over fare difference wording', () => {
  assert.equal(cashLabel({ newAmountDue: 320000, collectionRequired: 0, refundRequired: 0 }), 'Số tiền cần thanh toán cho đặt vé mới');
});
test('partial seat selection and whole trip seat counts require unique complete mappings', () => {
  assert.equal(selectionComplete([1], [4]), true);
  assert.equal(selectionComplete([1,2], [4,5]), true);
  assert.equal(selectionComplete([1,2,3], [4,5,6]), true);
  assert.equal(selectionComplete([1,2,3], [4,5]), false);
  assert.equal(selectionComplete([1,2], [4,4]), false);
  assert.equal(selectionComplete([], []), false);
});
test('every modification state has a presentation label', () => {
  for (const state of ['HELD','AWAITING_PAYMENT','COMPLETED','EXPIRED','CANCELLED','FAILED']) {
    assert.ok(modificationLabels[state]); assert.notEqual(modificationLabels[state], state);
  }
});
test('workspace keeps authoritative eligibility, actor context, history and unchanged mapping', () => {
  const source=readFileSync(new URL('../src/features/booking/Modification.tsx',import.meta.url),'utf8');
  for(const expected of ['rule.message','canManageOperator','Giữ nguyên','HỖ TRỢ THAY ĐỔI BOOKING','current.quote.bookingCode','h.actorType','modificationLabels[h.status]']) assert.ok(source.includes(expected),expected);
  assert.ok(!source.includes('minusHours'));
});
