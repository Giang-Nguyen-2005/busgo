// Development-only browser fixture. Synthetic data; never authenticates or calls a backend.
import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { apiClient } from '../../src/api/client';
import { AuthContext } from '../../src/features/auth/AuthProvider';
import { OperatorLayout } from '../../src/layouts/OperatorLayout';
import { operatorTripRoute } from '../../src/routes/operatorTripRoutes';
import { OperatorHomePage } from '../../src/pages/operator/OperatorHomePage';
import { OperatorTripsPage } from '../../src/pages/operator/OperatorTripsPages';
import { OperatorBookingsPage, OperatorBookingDetailPage } from '../../src/pages/operator/OperatorBookingsPages';
import '../../src/styles.css';
const timestamp = new Date().toISOString();
const stops = [10,20,30].map((id,i) => ({id,stopOrder:i+1,locationName:['TP. Hồ Chí Minh','Đà Lạt','Nha Trang'][i],plannedArrivalTime:timestamp,plannedDepartureTime:timestamp}));
const segments = [{id:90,segmentOrder:1,fromTripStopId:10,toTripStopId:20},{id:40,segmentOrder:2,fromTripStopId:20,toTripStopId:30}];
const seats = Array.from({length:12},(_,i)=>({id:i+1,seatCode:'A'+String(i+1).padStart(2,'0'),row:Math.floor((i%6)/2)+1,column:i%2?3:1,floor:i<6?1:2}));
const trip = {id:7,status:'SCHEDULED',route:{name:'Sài Gòn → Nha Trang',routeId:1},bus:{id:2,licensePlate:'51B-12345',busTypeName:'Giường nằm'},departureTime:timestamp,estimatedArrivalTime:timestamp,seats,segments,stops};
const contact={name:'Nguyễn Minh An',phone:'0901234567',email:'fixture@example.invalid'};
const pickup={tripStopId:10,name:'TP. Hồ Chí Minh',time:timestamp}, dropoff={tripStopId:30,name:'Nha Trang',time:timestamp};
const booking = (id:number) => ({bookingId:id,bookingCode:'BG-'+id,status:'CONFIRMED',paymentStatus:'PAID',route:trip.route,tripId:7,trip,contact,pickup,dropoff,seatCount:1,totalAmount:350000,createdAt:timestamp,updatedAt:timestamp,customer:{id:1,fullName:'Fixture Account',email:'fixture@example.invalid'},items:[{bookingItemId:id,tripSeatId:1,seatCode:'A01',passengerName:null,unitPrice:350000,ticket:{ticketCode:'VE-'+id,passengerName:'Tên in trên vé',seatCode:'A01',paymentId:id,createdAt:timestamp}}],payments:[{id,status:'PAID',method:'MOCK_QR',amount:350000,createdAt:timestamp,paidAt:timestamp}]});
let fail=false, bookingRequests=0, occupancyRequests=0;
apiClient.defaults.adapter = async config => {
  await new Promise(r=>setTimeout(r,120));
  if(fail) throw new Error('Fixture temporary network failure');
  const path=config.url||'';
  let data:unknown;
  if(path.endsWith('/occupancy')) {
    occupancyRequests++;
    data={tripId:7,tripStatus:'SCHEDULED',seatCount:12,segmentCount:2,wholeTripAvailableSeatCount:8,complete:false,expectedInventoryCellCount:24,actualInventoryCellCount:23,missingInventoryCellCount:1,segments:segments.map((s,i)=>({...s,tripSegmentId:s.id,fromName:stops[i].locationName,toName:stops[i+1].locationName,counts:{available:8,held:1,booked:1,blocked:1}})),seats:seats.map((s,i)=>({...s,tripSeatId:s.id,segments:segments.map((segment,j)=>({tripSegmentId:segment.id,segmentOrder:j+1,status:i===0?'BOOKED':i===1?'HELD':i===2?'BLOCKED':i===3&&j===1?null:'AVAILABLE',missing:i===3&&j===1,holdExpiresAt:i===1?timestamp:null,bookingId:i===0?100+j:null,bookingCode:i===0?'BG-'+(100+j):null}))}))};
  } else if(path.endsWith('/passengers')) data={tripId:7,tripStatus:'SCHEDULED',passengers:[100,101].map(id=>({bookingId:id,bookingCode:'BG-'+id,bookingStatus:'CONFIRMED',bookingItemId:id,tripSeatId:1,seatCode:'A01',pickup,dropoff,passengerName:null,ticketPassengerName:'Tên in trên vé',contact,paymentStatus:'PAID',ticketCode:'VE-'+id}))};
  else if(/\/bookings\/\d+$/.test(path)){bookingRequests++;data=booking(Number(path.split('/').pop()));}
  else if(path.endsWith('/bookings')) return {config,status:200,statusText:'OK',headers:{},data:{data:[booking(100),booking(101)],pagination:{page:0,size:20,totalPages:1,totalElements:2}}};
  else if(path.endsWith('/trips')) return {config,status:200,statusText:'OK',headers:{},data:{data:[{...trip,seatCount:12,segmentCount:2}],pagination:{page:0,size:100,totalPages:1,totalElements:1}}};
  else if(path.endsWith('/routes')||path.endsWith('/buses')) return {config,status:200,statusText:'OK',headers:{},data:{data:[],pagination:{page:0,size:100,totalPages:1,totalElements:0}}};
  else data=trip;
  return {config,status:200,statusText:'OK',headers:{},data:{data}};
};
const cache=new QueryClient({defaultOptions:{queries:{retry:false}}});
const router=createMemoryRouter([{path:'/operator',Component:OperatorLayout,children:[operatorTripRoute,{index:true,Component:OperatorHomePage},{path:'trips',Component:OperatorTripsPage},{path:'bookings',Component:OperatorBookingsPage},{path:'bookings/:bookingId',Component:OperatorBookingDetailPage}]}],{initialEntries:['/operator/trips/7/seats?mode=journey&from=10&to=30']});
function Preview(){const [admin,setAdmin]=useState(false),[failed,setFailed]=useState(false),[counts,setCounts]=useState('');return <><div style={{padding:8,background:'#fff2b9'}}>SYNTHETIC FIXTURE · <button onClick={()=>setAdmin(!admin)}>{admin?'Switch to staff':'Switch to admin'}</button> <button onClick={()=>{fail=!fail;setFailed(fail);void cache.invalidateQueries();}}>{failed?'Restore network':'Fail refresh'}</button> <button onClick={()=>setCounts('Booking requests: '+bookingRequests+' · Occupancy requests: '+occupancyRequests)}>Request counts</button> <span>{counts}</span></div><QueryClientProvider client={cache}><AuthContext.Provider value={{user:{fullName:'Nhân viên thử nghiệm',roles:[admin?'OPERATOR_ADMIN':'OPERATOR_STAFF']} as any,authenticated:true,loading:false,login(){},logout(){}}}><RouterProvider router={router}/></AuthContext.Provider></QueryClientProvider></>}
createRoot(document.getElementById('root')!).render(<Preview/>);
