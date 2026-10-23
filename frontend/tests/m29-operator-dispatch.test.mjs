import assert from 'node:assert/strict';
import {test} from 'node:test';
import {register} from 'node:module';
import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
register('./helpers/tsx-loader.mjs',import.meta.url);
const {canAccessOperatorPath,operatorNavigation}=await import('../src/features/auth/access.ts');
const {DispatchSeatGrid}=await import('../src/pages/operator/OperatorDispatchPage.tsx');
const D=await import('../src/features/operator/dispatch.ts');
test('dispatch exact access and navigation for both operator roles only',()=>{
 for(const role of ['OPERATOR_ADMIN','OPERATOR_STAFF']){assert(canAccessOperatorPath([role],'/operator/dispatch'));assert(operatorNavigation([role]).some(([p])=>p==='/dispatch'));}
 for(const roles of [[],['CUSTOMER'],['SYSTEM_ADMIN'],['SYSTEM_ADMIN','OPERATOR_ADMIN']]){assert(!canAccessOperatorPath(roles,'/operator/dispatch'));assert(!operatorNavigation(roles).some(([p])=>p==='/dispatch'));}
 for(const path of ['/operator/dispatch/new','/operator/dispatching','/operator/dispatch/1'])assert(!canAccessOperatorPath(['OPERATOR_STAFF'],path));
 for(const path of ['/operator/finance','/operator/routes','/operator/buses','/operator/bookings/new'])assert(!canAccessOperatorPath(['OPERATOR_STAFF'],path));
});
const segments=[{id:1,segmentOrder:1,fromTripStopId:10,toTripStopId:20},{id:2,segmentOrder:2,fromTripStopId:20,toTripStopId:30}];
const trip={seats:[{id:1,seatCode:'A1',floor:1,row:1,column:1},{id:2,seatCode:'A2',floor:1,row:3,column:3},{id:3,seatCode:'B1',floor:2,row:1,column:1}],segments,stops:[10,20,30].map(id=>({id}))};
const data={seats:[{tripSeatId:1,segments:[{tripSegmentId:1,status:'BOOKED',bookingId:11},{tripSegmentId:2,status:'BOOKED',bookingId:22}]}]};
test('new dispatch grid retains floor and row/column gaps, missing cells and mixed identities',()=>{
 const html=renderToStaticMarkup(React.createElement(DispatchSeatGrid,{trip,data,segments,floor:1,selected:1,onSelect(){}}));
 assert.match(html,/grid-template-columns:100px 24px 100px/);assert.match(html,/grid-row:3;grid-column:3/);assert(!html.includes('B1'));assert.match(html,/Khác nhau theo chặng/);assert.match(html,/Chưa có dữ liệu/);assert.match(html,/aria-pressed="true"/);
 assert.deepEqual(D.intersectingBookings(D.seatCells(data,1,segments)).map(b=>b.bookingId),[11,22]);assert(!D.wholeJourneyAvailable(D.seatCells(data,2,segments)));
});
test('new board requires valid context; single segment auto-selection and expired holds stay server-owned',()=>{
 assert.deepEqual(D.selectedSegments(trip,new URLSearchParams()),[]);
 assert.equal(D.selectedSegments({...trip,segments:[segments[0]]},new URLSearchParams()).length,1);
 assert.equal(D.selectedSegments(trip,new URLSearchParams('mode=journey&from=10&to=30')).length,2);
 assert.equal(D.selectedSegments(trip,new URLSearchParams('mode=journey&from=30&to=10')).length,0);
 const held={tripSegmentId:1,status:'HELD',holdExpiresAt:'2000-01-01T00:00:00Z'};const inv={seats:[{tripSeatId:1,segments:[held]}]};let calls=0;const seen=new Set();D.refreshExpiredHolds(inv,seen,Date.now(),()=>calls++);D.refreshExpiredHolds(inv,seen,Date.now(),()=>calls++);assert.equal(calls,1);assert.equal(D.cellStatus(held),'HELD');
});
