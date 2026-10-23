import assert from 'node:assert/strict';
import { test } from 'node:test';
import { register } from 'node:module';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
register('./helpers/tsx-loader.mjs', import.meta.url);
const { SeatMap } = await import('../src/features/trip/SeatMap.tsx');
const { TripCard } = await import('../src/components/ui.tsx');
const h = React.createElement;
const seats = [{tripSeatId:1,seatCode:'A1',row:1,column:1,floor:1,seatType:'SLEEPER',available:true},{tripSeatId:2,seatCode:'B1',row:3,column:4,floor:2,seatType:'SLEEPER',available:true}];
test('customer floor navigation retains physical geometry and does not change shared operator default', () => {
 const props = { seats, selected:[2], disabled:false, onToggle(){} };
 const customer = renderToStaticMarkup(h(SeatMap,{...props,floorNavigation:true}));
 assert.match(customer,/Chọn tầng xe/); assert.match(customer,/hidden=""/); assert.match(customer,/B1/); assert.match(customer,/aria-pressed="true"/);
 const shared = renderToStaticMarkup(h(SeatMap,props));
 assert.doesNotMatch(shared,/Chọn tầng xe|hidden=""/);
 assert.equal((shared.match(/class="seat-floor"/g)||[]).length,2);
});
test('delayed trips preserve next-day estimates and tolerate absent estimated timestamps', () => {
 const trip = { tripId:7, operator:{id:1,name:'Fixture'},route:{id:1,name:'A → B'},busType:{id:1,name:'Sleeper'},busImageUrl:null,pickup:{locationId:1,name:'A',departureTime:'2027-01-15T16:30:00Z'},dropoff:{locationId:2,name:'B',arrivalTime:'2027-01-15T22:00:00Z'},durationMinutes:330,price:100000,availableSeats:3,delayMinutes:120,operationalLabel:'Chậm 120 phút' };
 const render = t => renderToStaticMarkup(h(MemoryRouter,null,h(TripCard,{trip:t})));
 const missing=render(trip); assert.match(missing,/Đang cập nhật/); assert.match(missing,/Chậm 120 phút/);
 const nextDay=render({...trip,expectedPickupAt:'2027-01-15T18:30:00Z',expectedDropoffAt:'2027-01-16T00:00:00Z'});
 assert.match(nextDay,/16\/01\/2027 01:30/); assert.match(nextDay,/theo lịch/); assert.match(nextDay,/23:30/);
});
