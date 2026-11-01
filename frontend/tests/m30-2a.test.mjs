import assert from 'node:assert/strict';
import { test } from 'node:test';
import { routeSearch, profileReturn, journeyParams, readJourney } from '../src/features/customer/presentation.ts';

test('discovery starts an undated journey and scopes profile search without claiming availability', () => {
  const path = routeSearch({id:5,name:'A → B',pickupLocationId:23,dropoffLocationId:97},8);
  const params = new URL(path,'https://fixture.invalid').searchParams;
  assert.equal(params.get('departureDate'),null);
  assert.equal(params.get('pickupLocationId'),'23');
  assert.equal(params.get('dropoffLocationId'),'97');
  assert.equal(params.get('routeName'),'A → B');
  assert.equal(params.get('operatorId'),'8');
  params.set('sort','PRICE_ASC'); params.set('page','3');
  const submitted = journeyParams({...readJourney(params),departureDate:'2027-01-15'}, params);
  assert.equal(submitted.get('operatorId'),'8'); assert.equal(submitted.get('sort'),'PRICE_ASC');
  assert.equal(submitted.get('page'),null); assert.equal(submitted.get('departureDate'),'2027-01-15');
});

test('operator profile return accepts only customer search/trip context and preserves nested query', () => {
  const back = '/trips/7?pickupLocationId=1&dropoffLocationId=2&search='+encodeURIComponent('/search?sort=PRICE_ASC&page=2');
  assert.equal(profileReturn(back),back);
  assert.equal(profileReturn('/search?sort=PRICE_DESC&page=3'),'/search?sort=PRICE_DESC&page=3');
  for (const input of [null,'https://example.invalid','//example.invalid','/operator/trips','/login','/trips/7?\\evil','/search?\nfoo']) assert.equal(profileReturn(input),'/');
});
