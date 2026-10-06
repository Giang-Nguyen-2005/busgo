import test from 'node:test';
import assert from 'node:assert/strict';
import { livePollingInterval } from '../src/features/trip/liveTripModel.ts';
test('detail polls active lifecycle every 45 seconds', () => {
  for(const state of ['SCHEDULED','BOARDING','DEPARTED']) assert.equal(livePollingInterval(state),45000);
});
test('terminal lifecycle and cancelled booking stop polling', () => {
  for(const state of ['COMPLETED','CANCELLED',undefined]) assert.equal(livePollingInterval(state),false);
  assert.equal(livePollingInterval('SCHEDULED','CANCELLED'),false);
});
