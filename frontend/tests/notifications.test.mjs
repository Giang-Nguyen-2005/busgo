import test from 'node:test';
import assert from 'node:assert/strict';
import { notificationTarget } from '../src/features/notifications/notificationModel.ts';

test('notification navigation remains within the matching authenticated booking area', () => {
  assert.equal(notificationTarget('/my-bookings/42', false), '/my-bookings/42');
  assert.equal(notificationTarget('/operator/bookings/42', true), '/operator/bookings/42');
  assert.equal(notificationTarget('/operator/bookings/42', false), null);
  assert.equal(notificationTarget('/my-bookings/42', true), null);
});
test('notification navigation rejects external, malformed and unexpected targets', () => {
  for (const value of [null, '', '//example.test', 'https://example.test', '/admin', '/my-bookings/0', '/my-bookings/42?token=secret', '/my-bookings/42\n', '/my-bookings/../admin', '/my-bookings/42/modify']) {
    assert.equal(notificationTarget(value, false), null);
  }
});
