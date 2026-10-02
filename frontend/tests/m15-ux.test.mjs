import assert from 'node:assert/strict';
import { test } from 'node:test';
import { register } from 'node:module';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter, createMemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
register('./helpers/tsx-loader.mjs', import.meta.url);
const { bookingPaymentNavigation, rememberBooking, bookingRecovery } = await import('../src/features/booking/recovery.ts');
const { SeatBoard, BookingInspectionContent } = await import('../src/pages/operator/OperatorSeatsPage.tsx');
const { OccupancyContent } = await import('../src/pages/operator/OperatorTripOperationsPages.tsx');
const { OperatorBookingRow } = await import('../src/pages/operator/OperatorBookingsPages.tsx');
const { bookingFilters, updateBookingFilter } = await import('../src/features/operator/operations.ts');
const { PaymentPage } = await import('../src/pages/PaymentPage.tsx');
const { AuthContext } = await import('../src/features/auth/AuthProvider.tsx');
const h = React.createElement;
const render = (component, props) => renderToStaticMarkup(h(MemoryRouter, null, h(component, props)));
const stop = { locationId:1, tripStopId:10, name:'A', time:null };
const booking = { bookingId:101, bookingCode:'BG-101', status:'PENDING', paymentStatus:'PENDING', contact:{name:'Liên hệ An', phone:'0901234567',email:'a@example.invalid'}, customer:{id:1,fullName:'Tài khoản khác'}, operator:{id:1,name:'Nhà xe'}, route:{id:1,name:'A → B'}, tripId:7, trip:{id:7,status:'SCHEDULED',departureTime:'2027-01-15T12:00:00Z',estimatedArrivalTime:'2027-01-15T14:00:00Z'}, pickup:stop, dropoff:{...stop,name:'B',locationId:2}, seatCount:2,totalAmount:700000,createdAt:'2027-01-15T10:00:00Z',items:[{bookingItemId:1,tripSeatId:1,seatCode:'A01',passengerName:null,ticket:null}],payments:[],seats:[{seatCode:'A01'}] };
const segment = {id:90,tripSegmentId:90,segmentOrder:1,fromTripStopId:10,toTripStopId:20,fromName:'A',toName:'B',counts:{available:1,booked:1,held:1,blocked:1}};
const trip = {seats:['BOOKED','AVAILABLE','HELD','BLOCKED','MISSING'].map((_,i)=>({id:i+1,seatCode:`A0${i+1}`,row:i+1,column:i%2?3:1,floor:1})), stops:[{id:10,locationName:'A'},{id:20,locationName:'B'}],segments:[segment]};
const occupancy = {complete:false,missingInventoryCellCount:1,actualInventoryCellCount:4,expectedInventoryCellCount:5,tripStatus:'SCHEDULED',seatCount:5,segmentCount:1,wholeTripAvailableSeatCount:1,segments:[segment],seats:trip.seats.map((s,i)=>({...s,tripSeatId:s.id,segments:[{tripSegmentId:90,status:['BOOKED','AVAILABLE','HELD','BLOCKED',null][i],missing:i===4,bookingId:i===0?101:null,bookingCode:i===0?'BG-101':null}]}))};

test('replacement checkout history returns to search and retains payment forward recovery', async () => {
  const router = createMemoryRouter([{path:'*',element:null}], { initialEntries:['/search','/trips/7'], initialIndex:1 });
  await router.navigate('/booking',{replace:true});
  const target=bookingPaymentNavigation(101);
  await router.navigate(target.to,target.options);
  await router.navigate(-1);
  assert.equal(router.state.location.pathname,'/search');
  await router.navigate(1);
  assert.equal(router.state.location.pathname,'/payment');
  assert.equal(router.state.location.search,'?bookingId=101');
  router.dispose();
});
test('trip recovery is scoped to the account and exact journey and stores no contact', () => {
  const data=new Map(); globalThis.sessionStorage={getItem:k=>data.get(k)||null,setItem:(k,v)=>data.set(k,v)};
  try {
    rememberBooking(1,booking);
    assert.equal(bookingRecovery(1,7,1,2),101);
    assert.equal(bookingRecovery(2,7,1,2),undefined);
    assert.equal(bookingRecovery(1,7,2,1),undefined);
    assert.doesNotMatch([...data.values()].join(''),/0901234567|Liên hệ An/);
    data.set('busgo.completed-bookings','invalid');
    assert.equal(bookingRecovery(1,7,1,2),undefined);
    data.set('busgo.completed-bookings','[null,{}, {"userId":1,"bookingId":"bad"}]');
    assert.equal(bookingRecovery(1,7,1,2),undefined);
  } finally { delete globalThis.sessionStorage; }
});
test('payment explicitly returns to this booking detail', () => {
  const cache=new QueryClient(); cache.setQueryData(['booking',1,'101'],booking);
  const html=renderToStaticMarkup(h(QueryClientProvider,{client:cache},h(AuthContext.Provider,{value:{user:{id:1},authenticated:true}},h(MemoryRouter,{initialEntries:['/payment?bookingId=101']},h(PaymentPage)))));
  assert.match(html,/href="\/my-bookings\/101"[^>]*>← Quay lại chi tiết đặt vé/);
});
test('physical seats expose distinct status classes, contact and booking mappings', () => {
  const html=render(SeatBoard,{trip,data:occupancy,segments:[segment],inspect(){},bookings:{101:booking}});
  for (const status of ['BOOKED','AVAILABLE','HELD','BLOCKED','MISSING']) assert.match(html,new RegExp(`operator-seat operator-status-${status}`));
  assert.match(html,/Liên hệ An/);assert.match(html,/0901234567/);assert.match(html,/BG-101/);
  assert.match(html,/Đã đặt/);assert.match(html,/Còn trống/);assert.match(html,/grid-row:2;grid-column:3/);
});
test('inspection maps only selected seat, with truthful contact, coverage and pending payment', () => {
  const html=render(BookingInspectionContent,{booking:{...booking,items:[...booking.items,{bookingItemId:2,tripSeatId:2,seatCode:'OTHER-SEAT',passengerName:'Different'}]},seatId:1});
  for (const text of ['BG-101','Liên hệ An','0901234567','a@example.invalid','A → B','A01','Chưa có giao dịch thanh toán','Chưa có thông tin riêng']) assert.ok(html.includes(text));
  assert.doesNotMatch(html,/OTHER-SEAT|Different/);
});
test('booking rows give clickable code/contact, secondary email/time and status badges', () => {
  const html=renderToStaticMarkup(h(MemoryRouter,null,h('table',null,h('tbody',null,h(OperatorBookingRow,{booking})))));
  assert.match(html,/operator-booking-code/); assert.match(html,/operator-contact-name/);
  assert.match(html,/<small class="muted">a@example.invalid/);assert.match(html,/0901234567/);
  assert.match(html,/2 ghế/);assert.match(html,/operator-status-PENDING/);assert.doesNotMatch(html,/Tài khoản khác/);
});
test('filter changes reset pagination, serialize supported values and clearing restores defaults', () => {
  let params=new URLSearchParams('q=An&tripId=7&status=PENDING&paymentStatus=PAID&date=2026-10-02&page=2');
  params=updateBookingFilter(params,'paymentStatus','PENDING');
  assert.equal(bookingFilters(params).page,0);assert.equal(bookingFilters(params).tripId,7);
  params=updateBookingFilter(params,'q','');assert.equal(bookingFilters(params).q,undefined);
  assert.deepEqual(bookingFilters(new URLSearchParams()),{q:undefined,tripId:undefined,status:undefined,paymentStatus:undefined,date:undefined,page:0,size:20});
});
test('matrix renders all statuses, aggregate counts and missing inventory as non-available', () => {
  const html=render(OccupancyContent,{occupancy});
  for (const status of ['BOOKED','AVAILABLE','HELD','BLOCKED','MISSING']) assert.match(html,new RegExp(`operator-cell-${status}`));
  assert.match(html,/operator-single-segment/);assert.match(html,/Chưa có dữ liệu: <strong>1/);
  const missing=html.slice(html.indexOf('operator-cell-MISSING'));
  assert.doesNotMatch(missing,/operator-status-AVAILABLE/);
  assert.match(html,/Không coi ô thiếu là ghế trống/);
});

