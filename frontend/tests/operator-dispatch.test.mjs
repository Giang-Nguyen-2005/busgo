import assert from 'node:assert/strict';
import { test } from 'node:test';
import { register } from 'node:module';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter, createMemoryRouter, RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
register('./helpers/tsx-loader.mjs', import.meta.url);
const D = await import('../src/features/operator/dispatch.ts');
const { SeatBoard, HoldState, CompletenessWarning, BookingInspectionContent } = await import('../src/pages/operator/OperatorSeatsPage.tsx');
const { OperationsQueryState } = await import('../src/features/operator/OperationsShared.tsx');
const { operatorTripRoute } = await import('../src/routes/operatorTripRoutes.ts');
const { canAccessOperatorPath } = await import('../src/features/auth/access.ts');
const { AuthContext } = await import('../src/features/auth/AuthProvider.tsx');
const { nextTripAction } = await import('../src/features/operator/operations.ts');
const { operatorApi } = await import('../src/api/operatorApi.ts');
const { apiClient } = await import('../src/api/client.ts');
const h = React.createElement;
const render = (c, props) => renderToStaticMarkup(h(MemoryRouter, null, h(c, props)));
const stops = [10,20,30].map((id, i) => ({ id, stopOrder: i + 1, locationName: ['A','B','C'][i] }));
const segments = [{ id: 90, segmentOrder: 1, fromTripStopId: 10, toTripStopId: 20 }, { id: 40, segmentOrder: 2, fromTripStopId: 20, toTripStopId: 30 }];
const trip = { id: 7, route: { name: 'Tuyến thử nghiệm', routeId: 1 }, bus: { licensePlate: '51B-12345', busTypeName: 'Giường nằm' }, status: 'SCHEDULED', departureTime: '2026-09-29T02:00:00Z', estimatedArrivalTime: '2026-09-29T06:00:00Z', stops, segments: [...segments].reverse(), seats: [{ id: 1, seatCode: 'A01', row: 1, column: 1, floor: 1 }, { id: 2, seatCode: 'A02', row: 3, column: 3, floor: 1 }, { id: 3, seatCode: 'B01', row: 1, column: 1, floor: 2 }] };
const cell = (id, status = 'AVAILABLE', extra = {}) => ({ tripSegmentId: id, segmentOrder: id === 90 ? 1 : 2, status, missing: false, bookingId: null, holdExpiresAt: null, ...extra });
const occupancy = (cells = [cell(40), cell(90)]) => ({ tripId: 7, tripStatus: 'SCHEDULED', seatCount: 3, segmentCount: 2, wholeTripAvailableSeatCount: 3, complete: true, expectedInventoryCellCount: 6, actualInventoryCellCount: 6, missingInventoryCellCount: 0, segments: segments.map(s => ({ ...s, tripSegmentId: s.id, fromName: 'A', toName: 'B', counts: { available: 3, held: 0, booked: 0, blocked: 0 } })), seats: trip.seats.map(s => ({ ...s, tripSeatId: s.id, segments: cells })) });

test('Vietnam today switches at Vietnam midnight, and validates business date', () => {
  assert.equal(D.vietnamToday(new Date('2026-09-28T16:59:59Z')), '2026-09-28');
  assert.equal(D.vietnamToday(new Date('2026-09-28T17:00:00Z')), '2026-09-29');
  assert.equal(D.businessDate('2026-02-30', new Date('2026-09-28T17:00:00Z')), '2026-09-29');
  assert.equal(D.businessDate('2026-02-28'), '2026-02-28');
});
test('trip API sends businessDate, IDs, status and pagination without legacy date', async () => {
  const original = apiClient.defaults.adapter; let request;
  apiClient.defaults.adapter = async config => { request = config; return { status: 200, headers: {}, config, data: { data: [], pagination: {} } }; };
  try {
    const filters = { businessDate: '2026-09-29', routeId: 3, busId: 4, status: 'BOARDING', page: 2, size: 10 };
    await operatorApi.trips(filters);
    assert.deepEqual(request.params, filters); assert.equal(request.params.date, undefined);
  } finally { apiClient.defaults.adapter = original; }
});
test('single segment auto-selects, multi-segment requires explicit IDs and contiguous forward journey', () => {
  assert.deepEqual(D.selectedSegments({ ...trip, segments: [segments[0]] }, new URLSearchParams()), [segments[0]]);
  assert.deepEqual(D.selectedSegments(trip, new URLSearchParams()), []);
  assert.deepEqual(D.selectedSegments(trip, new URLSearchParams('segment=40')), [segments[1]]);
  assert.deepEqual(D.selectedSegments(trip, new URLSearchParams('mode=journey&from=10&to=30')), segments);
  for (const p of ['mode=journey&from=30&to=10','mode=journey&from=10&to=10','segment=999','mode=journey&from=A&to=C']) assert.deepEqual(D.selectedSegments(trip,new URLSearchParams(p)),[]);
  assert.deepEqual(D.selectedSegments({ ...trip, segments: [segments[0], { ...segments[1], fromTripStopId: 999 }] },new URLSearchParams('mode=journey&from=10&to=30')), []);
});
test('every real single-segment status has an explicit label; missing beats AVAILABLE', () => {
  for (const status of ['AVAILABLE','HELD','BOOKED','BLOCKED']) assert.equal(D.seatSummary([cell(90,status)]), D.cellLabels[status]);
  assert.equal(D.seatSummary([cell(90,'AVAILABLE',{ missing: true })]), 'Chưa có dữ liệu');
  assert.equal(D.seatSummary([undefined]), 'Chưa có dữ liệu');
});
test('mixed journeys preserve segment order and reused bookings are separate', () => {
  const data = occupancy([cell(40,'AVAILABLE'), cell(90,'BOOKED',{ bookingId: 100 })]);
  const cells = D.seatCells(data,1,segments);
  assert.deepEqual(cells.map(c=>c.status),['BOOKED','AVAILABLE']);
  assert.equal(D.seatSummary(cells),'Khác nhau theo chặng');
  const reused = [cell(90,'BOOKED',{ bookingId:100 }), cell(40,'BOOKED',{ bookingId:200 })];
  assert.equal(D.seatSummary(reused),'Khác nhau theo chặng');
  assert.deepEqual(D.intersectingBookings(reused),[{ bookingId:100,segmentIds:[90] },{ bookingId:200,segmentIds:[40] }]);
});
test('whole-journey availability requires every expected cell, including entirely missing seats', () => {
  assert.equal(D.wholeJourneyAvailable([cell(90),cell(40)]),true);
  for (const cells of [[],[cell(90),undefined],[cell(90),cell(40,'AVAILABLE',{missing:true})],[cell(90),cell(40,'HELD')]]) assert.equal(D.wholeJourneyAvailable(cells),false);
  assert.equal(D.wholeJourneyAvailable(D.seatCells(occupancy(),999,segments)),false);
  assert.equal(D.wholeJourneyAvailable(D.seatCells(occupancy([cell(90)]),1,segments)),false);
});
test('physical board preserves floor and geometric gaps and neutral mixed summary', () => {
  const html = render(SeatBoard,{ trip, data:occupancy([cell(90,'BOOKED',{bookingId:100}),cell(40)]), segments, inspect() {}, selectedSeatId:2 });
  assert.match(html,/Tầng 1/); assert.match(html,/Tầng 2/); assert.match(html,/grid-row:3;grid-column:3/);
  assert.match(html,/Khác nhau theo chặng/); assert.match(html,/1\. Đã đặt/); assert.match(html,/2\. Còn trống/);
  assert.match(html,/aria-pressed="true"/); assert.match(html,/aria-haspopup="dialog"/);
  assert.doesNotMatch(html,/Tài xế|Nhà vệ sinh|Cầu thang/);
  assert.match(render(SeatBoard,{trip,data:occupancy(),segments:[],inspect() {}}),/disabled=""/);
});
test('completeness warning uses backend counts and never labels missing cells available', () => {
  const data = { ...occupancy([cell(90),cell(40,null,{missing:true})]), complete:false,actualInventoryCellCount:3,missingInventoryCellCount:3 };
  assert.match(render(CompletenessWarning,{data}),/3 ô ghế × chặng/);
  assert.match(render(CompletenessWarning,{data}),/3\/6/);
  const html=render(SeatBoard,{trip,data,segments:[segments[1]],inspect() {}});
  assert.match(html,/operator-status-MISSING/);assert.doesNotMatch(html,/operator-status-AVAILABLE/);
});
test('held expiry triggers one refetch per observed expiry, never a local inventory transition', () => {
  const expires='2026-09-29T03:00:00Z'; const held=cell(90,'HELD',{holdExpiresAt:expires}); const data=occupancy([held]); const seen=new Set(); let calls=0;
  const tick=now=>D.refreshExpiredHolds(data,seen,Date.parse(now),()=>calls++);
  tick('2026-09-29T02:59:59Z');assert.equal(calls,0);
  tick(expires);assert.equal(calls,1);tick('2026-09-29T03:00:30Z');assert.equal(calls,1);
  assert.equal(held.status,'HELD');assert.match(render(HoldState,{cell:held,now:Date.parse(expires)}),/Đã đến hạn giữ chỗ · đang cập nhật/);
  assert.match(render(HoldState,{cell:held,now:0}),/Hết hạn:/);
});
test('booking drawer maps only the selected seat and preserves nullable passenger identity', () => {
  const booking={ bookingId:100, bookingCode:'BG-100',status:'CONFIRMED',pickup:{name:'A'},dropoff:{name:'C'},contact:{name:'Liên hệ thật',phone:'0901234567'},payments:[{id:1,status:'PAID'}],items:[{bookingItemId:1,tripSeatId:1,seatCode:'A01',passengerName:null,ticket:{ticketCode:'T-100',passengerName:'Tên in trên vé'}},{bookingItemId:2,tripSeatId:2,seatCode:'A02',passengerName:'Không thuộc ghế đang xem'}] };
  const html=render(BookingInspectionContent,{booking,seatId:1});
  for(const text of ['Khách trên ghế','Chưa có thông tin riêng','Tên trên vé','Tên in trên vé','Liên hệ đặt vé','Liên hệ thật','T-100','0901234567','Sao chép số','Đã thanh toán','/operator/bookings/100']) assert.ok(html.includes(text),text);
  assert.doesNotMatch(html,/Không thuộc ghế đang xem/);
});
test('manifest searches all six operational fields and groups by stop IDs, not names', () => {
  const row={seatCode:'A01',bookingCode:'BG-100',passengerName:'Nguyễn An',ticketPassengerName:'Tên Vé',contact:{name:'Chủ liên hệ',phone:'0901234567'},pickup:{tripStopId:10,name:'Trùng tên'},dropoff:{tripStopId:20,name:'Trùng tên'}};
  for(const search of ['A01','BG-100','nguyen an','ten ve','chu lien he','090123']) assert.equal(D.manifestGroups([row],search).length,1);
  assert.equal(D.manifestGroups([row],'không có').length,0);
  assert.equal(D.manifestGroups([row,{...row,pickup:{...row.pickup,tripStopId:30}}],'').length,2);
});
test('transient refresh preserves content, authorization failures suppress it', () => {
  const base={data:{secret:'PRIVATE'},isError:true,error:new Error('offline'),refetch() {}};
  const html=render(OperationsQueryState,{query:base,children:d=>d.secret});
  assert.match(html,/PRIVATE/);assert.match(html,/Cập nhật tạm thời thất bại/);
  for(const status of [401,403]) assert.doesNotMatch(render(OperationsQueryState,{query:{...base,error:{response:{status}}},children:d=>d.secret}),/PRIVATE/);
});
test('dispatch priority and lifecycle Vietnamese labels do not infer departure', () => {
  const scheduled={...trip,seatCount:3,segmentCount:2};
  assert.equal(D.overdue(scheduled,Date.parse('2026-09-30')),true);
  assert.equal(D.overdue({...scheduled,status:'DEPARTED'},Date.parse('2026-09-30')),false);
  assert.equal(D.dispatchOrder([scheduled,{...scheduled,status:'BOARDING'}])[0].status,'BOARDING');
  assert.equal(D.tripLabels.DEPARTED,'Đang chạy'); assert.equal(nextTripAction('SCHEDULED').label,'Bắt đầu đón khách');
});
test('staff can deep-link to seats but cannot access management or trip creation', () => {
  assert.equal(canAccessOperatorPath(['OPERATOR_STAFF'],'/operator/trips/7/seats'),true);
  for(const path of ['/operator/trips/new','/operator/buses','/operator/routes','/operator/staff']) assert.equal(canAccessOperatorPath(['OPERATOR_STAFF'],path),false);
});
test('actual lazy trip routes support reload, tab navigation and back with a shared read-only header', async () => {
  const cache=new QueryClient({defaultOptions:{queries:{retry:false,staleTime:Infinity}}});
  cache.setQueryData(['operator','trips',7],trip);cache.setQueryData(['operator','trips',7,'occupancy'],occupancy());cache.setQueryData(['operator','trips',7,'passengers'],{tripId:7,tripStatus:'SCHEDULED',passengers:[]});
  const router=createMemoryRouter([{path:'/operator',children:[operatorTripRoute]}],{initialEntries:['/operator/trips/7/seats?mode=journey&from=10&to=30']});
  if(!router.state.initialized) await new Promise(resolve=>{const unsub=router.subscribe(state=>{if(state.initialized){unsub();resolve();}});});
  const html=()=>renderToStaticMarkup(h(QueryClientProvider,{client:cache},h(AuthContext.Provider,{value:{user:{roles:['OPERATOR_STAFF']}}},h(RouterProvider,{router}))));
  try {
    assert.match(html(),/Sơ đồ ghế/);assert.match(html(),/51B-12345/);assert.match(html(),/Trống suốt hành trình/);assert.doesNotMatch(html(),/Bắt đầu đón khách/);
    for(const [path,label] of D.tripTabs){await router.navigate('/operator/trips/7'+(path?'/'+path:''));const markup=html();assert.match(markup,/Tuyến thử nghiệm/);assert.ok(markup.includes(label));assert.match(markup,/aria-current="page"/);}
    await router.navigate(-1);assert.equal(router.state.location.pathname,'/operator/trips/7/passengers');
  } finally {router.dispose();cache.clear();}
});

test('operator authorization loss clears the session; late failures cannot clear a replacement session', async () => {
  const { setTokens, getTokens } = await import('../src/api/session.ts');
  const storage=new Map(); const oldStorage=globalThis.sessionStorage, oldWindow=globalThis.window;
  globalThis.sessionStorage={getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)};
  globalThis.window=new EventTarget();let cleared=0;window.addEventListener('busgo:auth',()=>{if(!getTokens())cleared++;});
  const original=apiClient.defaults.adapter;
  try {
    setTokens({accessToken:'fixture-old',refreshToken:'fixture-refresh'});
    apiClient.defaults.adapter=async config=>{throw {config,response:{status:403}};};
    await assert.rejects(operatorApi.occupancy(7));assert.equal(getTokens(),null);assert.equal(cleared,1);
    setTokens({accessToken:'fixture-current',refreshToken:'fixture-refresh'});
    apiClient.defaults.adapter=async config=>{setTokens(null);setTokens({accessToken:'fixture-new',refreshToken:'fixture-refresh-new'});throw {config,response:{status:403}};};
    await assert.rejects(operatorApi.occupancy(7));assert.equal(getTokens().accessToken,'fixture-new');
  } finally {apiClient.defaults.adapter=original;globalThis.sessionStorage=oldStorage;globalThis.window=oldWindow;}
});
