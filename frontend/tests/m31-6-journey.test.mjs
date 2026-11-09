import assert from 'node:assert/strict';
import { test } from 'node:test';
import { journeyParams, readJourney, swapJourney } from '../src/features/customer/presentation.ts';

test('changing either endpoint drops the discovered route name from Search and Trip return links',()=>{
  const previous=new URLSearchParams('pickupLocationId=261&dropoffLocationId=262&routeName=HCM-DALAT&page=3&operatorId=61');
  const changed=journeyParams({...readJourney(previous),dropoffLocationId:269,dropoffLabel:'Điểm đón demo Phan Thiết',departureDate:'2026-11-20'},previous);
  assert.equal(changed.has('routeName'),false);assert.equal(changed.has('page'),false);
  assert.equal(changed.get('dropoffLocationId'),'269');assert.equal(changed.get('operatorId'),'61');
  assert.equal(journeyParams(swapJourney(readJourney(previous)),previous).has('routeName'),false);
});
test('changing only the date preserves the correct discovered route context',()=>{
  const previous=new URLSearchParams('pickupLocationId=261&dropoffLocationId=262&routeName=HCM-DALAT');
  const same=journeyParams({...readJourney(previous),departureDate:'2026-11-27'},previous);
  assert.equal(same.get('routeName'),'HCM-DALAT');assert.equal(same.get('departureDate'),'2026-11-27');
});
