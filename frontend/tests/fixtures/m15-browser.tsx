// Isolated development fixture. All records below are synthetic; no backend calls or real credentials.
import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { apiClient } from '../../src/api/client';
import { AuthContext } from '../../src/features/auth/AuthProvider';
import { router as productionRouter } from '../../src/routes/router';
import '../../src/styles.css';
const departure='2027-01-15T22:30:00+07:00', arrival='2027-01-16T05:30:00+07:00';
const locations=[{id:1,name:'Bến xe Thành phố Hồ Chí Minh',province:'TP. Hồ Chí Minh',district:null},{id:2,name:'Bến xe liên tỉnh Đà Lạt',province:'Lâm Đồng',district:null}];
const pickup={tripStopId:11,locationId:1,name:locations[0].name,departureTime:departure,time:departure},dropoff={tripStopId:12,locationId:2,name:locations[1].name,arrivalTime:arrival,time:arrival};
const seats=Array.from({length:16},(_,i)=>({tripSeatId:i+1,seatCode:'A'+String(i+1).padStart(2,'0'),row:Math.floor(i%8/2)+1,column:i%2?3:1,floor:i<8?1:2,seatType:'STANDARD',available:i!==7}));
const trip={tripId:7,operator:{id:1,name:'Nhà xe thử nghiệm Hành Trình Việt Nam'},route:{id:1,name:'Thành phố Hồ Chí Minh – Đà Lạt'},busType:{id:1,name:'Giường nằm hai tầng'},busImageUrl:null,pickup,dropoff,durationMinutes:420,price:350000,availableSeats:15,status:'SCHEDULED',stops:[{...pickup,name:pickup.name,stopOrder:1,arrivalTime:null,allowPickup:true,allowDropoff:false},{...dropoff,name:dropoff.name,stopOrder:2,departureTime:null,allowPickup:false,allowDropoff:true}]};
const user={id:1,fullName:'Nguyễn Thị Minh Anh – Tài khoản thử nghiệm',email:'fixture@example.invalid',phone:'0900000000',roles:['CUSTOMER']};
let created=false;
let booking={bookingId:101,bookingCode:'BG-FIXTURE-101',status:'PENDING',tripId:7,operator:trip.operator,route:trip.route,pickup,dropoff,contact:{name:user.fullName,email:user.email,phone:user.phone},seats:seats.slice(0,2).map(s=>({...s,passengerName:null,unitPrice:350000})),pricePerSeat:350000,totalAmount:700000,createdAt:departure};
const hold={holdToken:'fixture-only-hold',tripId:7,pickup,dropoff,tripSeatIds:[1,2,3,4,5],seats:seats.slice(0,5),pricePerSeat:350000,totalPrice:1750000,expiresAt:new Date(Date.now()+600000).toISOString(),status:'ACTIVE',operatorName:trip.operator.name,routeName:trip.route.name};
let operator={id:2,code:'NX-FIXTURE',name:'Nhà xe thử nghiệm Hành Trình Việt Nam',phone:'0900000000',email:'operator@example.invalid',address:'Địa chỉ thử nghiệm do fixture cung cấp',status:'ACTIVE',staffCounts:{total:7,active:5,admins:2,staff:5},operationalCounts:{buses:12,routes:4,trips:38,bookings:105},createdAt:departure,updatedAt:departure};
const staff=[{staffId:1,staffCode:'NV-001',membershipStatus:'ACTIVE',role:'OPERATOR_ADMIN',user:{...user,status:'ACTIVE'},createdAt:departure},{staffId:2,staffCode:null,membershipStatus:'INACTIVE',role:'OPERATOR_STAFF',user:{...user,id:2,fullName:'Trần Văn Nhân viên thử nghiệm',status:'LOCKED'},createdAt:departure}];
let fail=false, empty=false, rejectStatus=false;
const requests: string[]=[];
apiClient.defaults.adapter=async config=>{
 await new Promise(r=>setTimeout(r,100));
 const path=config.url||''; requests.push((config.method||'get')+' '+path+' '+JSON.stringify(config.params||{}));
 if(fail) throw new Error('Fixture network failure');
 const body=config.data ? JSON.parse(config.data) : {};
 const response=(data:unknown,pagination?:unknown)=>({config,status:200,statusText:'OK',headers:{},data:pagination ? {data,pagination} : {data}});
 const page=(data:unknown[],total=data.length)=>response(data,{page:0,size:config.params?.size||10,totalElements:total,totalPages:Math.ceil(total/(config.params?.size||10))});
 if(path.startsWith('/operator/')) {
   const opTrip={id:7,status:'SCHEDULED',route:trip.route,bus:{id:2,licensePlate:'51B-FIXTURE',busTypeName:trip.busType.name},departureTime:departure,estimatedArrivalTime:arrival,seats:seats.map(s=>({...s,id:s.tripSeatId})),stops:[{id:11,stopOrder:1,locationName:pickup.name},{id:12,stopOrder:2,locationName:dropoff.name}],segments:[{id:90,segmentOrder:1,fromTripStopId:11,toTripStopId:12}]};
   const items=booking.seats.map((s,i)=>({...s,bookingItemId:i+1,ticket:booking.status==='CONFIRMED'?{ticketCode:'VE-FIXTURE-'+(i+1),passengerName:booking.contact.name,seatCode:s.seatCode,paymentId:1,createdAt:departure}:null}));
   const detail={...booking,trip:opTrip,tripId:7,customer:user,items,payments:booking.status==='CONFIRMED'?[{id:1,status:'PAID',method:'MOCK_QR',amount:700000,createdAt:departure,paidAt:departure}]:[],updatedAt:departure,seatCount:booking.seats.length,paymentStatus:booking.status==='CONFIRMED'?'PAID':'PENDING'};
   if(path.endsWith('/occupancy')) return response({tripId:7,tripStatus:'SCHEDULED',seatCount:16,segmentCount:1,wholeTripAvailableSeatCount:11,complete:false,expectedInventoryCellCount:16,actualInventoryCellCount:15,missingInventoryCellCount:1,segments:[{tripSegmentId:90,segmentOrder:1,fromTripStopId:11,toTripStopId:12,fromName:pickup.name,toName:dropoff.name,counts:{available:11,held:1,booked:2,blocked:1}}],seats:seats.map((s,i)=>({...s,tripSeatId:s.tripSeatId,segments:[{tripSegmentId:90,segmentOrder:1,status:booking.seats.some(b=>b.tripSeatId===s.tripSeatId)?'BOOKED':i===2?'HELD':i===3?'BLOCKED':i===4?null:'AVAILABLE',missing:i===4,holdExpiresAt:i===2?new Date(Date.now()+600000).toISOString():null,bookingId:booking.seats.some(b=>b.tripSeatId===s.tripSeatId)?101:null,bookingCode:booking.seats.some(b=>b.tripSeatId===s.tripSeatId)?booking.bookingCode:null}]}))});
   if(path.endsWith('/passengers')) return response({tripId:7,tripStatus:'SCHEDULED',passengers:booking.status==='CONFIRMED'?items.map(item=>({...item,...booking,tripSeatId:item.tripSeatId,bookingItemId:item.bookingItemId,seatCode:item.seatCode,bookingStatus:booking.status,paymentStatus:'PAID',passengerName:null,ticketPassengerName:booking.contact.name,ticketCode:item.ticket?.ticketCode})):[]});
   if(path.endsWith('/bookings/101')) return response(detail);
   if(path.endsWith('/bookings')) return page([detail]);
   if(path.endsWith('/trips')) return page([{...opTrip,seatCount:16,segmentCount:1}]);
   if(path.endsWith('/routes')||path.endsWith('/buses')) return page([]);
   return response(opTrip);
 }
 if(path==='/users/me') return response(user);
 if(path==='/users/me/change-password') return response({});
 if(path==='/locations') return response(locations.filter(l=>!config.params?.q || l.name.toLowerCase().includes(config.params.q.toLowerCase())));
 if(path==='/trips/search') return page(empty||config.params?.minPrice ? [] : [trip,{...trip,tripId:8,price:400000,busImageUrl:'/images/busgo/bus-sleeper.jpg'}]);
 if(path.endsWith('/seats')) return response({...hold,price:350000,availableSeatCount:created?13:15,seats:seats.map(s=>({...s,available:s.available && !(created && booking.seats.some(b=>b.tripSeatId===s.tripSeatId))}))});
 if(path.startsWith('/trips/')) return response(trip);
 if(path==='/seat-holds' && config.method==='post') return response({...hold,seats:seats.filter(s=>body.tripSeatIds.includes(s.tripSeatId)),tripSeatIds:body.tripSeatIds,totalPrice:body.tripSeatIds.length*350000});
 if(path.startsWith('/seat-holds/')) { const saved=JSON.parse(sessionStorage.getItem('busgo.hold')||'null'); return response(saved||hold); }
 if(path==='/bookings/me') return page(empty || (config.params?.status && config.params.status!==booking.status) ? [] : [{...booking,operatorName:trip.operator.name,routeName:trip.route.name,departureTime:departure,seats:booking.seats.map(s=>s.seatCode)}]);
 if(path.endsWith('/payments/mock-confirm')) { booking={...booking,status:'CONFIRMED'}; return response({bookingId:101,paymentStatus:'PAID',bookingStatus:'CONFIRMED'}); }
 if(path.endsWith('/ticket')) return response({...booking,paymentStatus:'PAID',paymentMethod:'MOCK_QR',amount:booking.totalAmount,departureTime:departure,arrivalTime:arrival,tickets:booking.seats.map((s,i)=>({ticketId:i+1,ticketCode:'VE-FIXTURE-'+(i+1),passengerName:user.fullName,seatCode:s.seatCode,qrData:'FIXTURE-TICKET-'+(i+1)}))});
 if(path==='/bookings' && config.method==='post') {const saved=JSON.parse(sessionStorage.getItem('busgo.hold')||'null');created=true;booking={...booking,status:'PENDING',contact:{name:body.contactName,phone:body.contactPhone,email:body.contactEmail},seats:saved.seats.map((s:any)=>({...s,passengerName:null,unitPrice:350000})),totalAmount:saved.seats.length*350000};return response(booking);}
 if(path==='/bookings/101') return response(booking);
 if(path==='/admin/operators' && config.method==='post') {operator={...operator,...body,id:2};return response(operator);}
 if(path==='/admin/operators') return page(empty?[]:[{...operator,activeStaffCount:5,activeAdminCount:1}],empty?0:config.params?.status==='ACTIVE'?17:config.params?.status==='INACTIVE'?3:20);
 if(path.endsWith('/staff')) return page(empty?[]:staff);
 if(path.endsWith('/status')) {if(rejectStatus) throw {isAxiosError:true,config,response:{status:409,data:{code:'OPERATOR_ACTIVATION_NOT_ALLOWED'}}}; operator={...operator,...body};return response(operator);}
 if(path==='/admin/operators/2') {if(config.method==='patch')operator={...operator,...body,updatedAt:new Date().toISOString()};return response(operator);}
 throw new Error('Unmapped fixture endpoint '+path);
};
const cache=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
const search='/search?'+new URLSearchParams({pickupLocationId:'1',pickupLabel:pickup.name,dropoffLocationId:'2',dropoffLabel:dropoff.name,departureDate:'2027-01-15'});
const paths=[['Home','/'],['Search',search],['Seats','/trips/7?pickupLocationId=1&dropoffLocationId=2&search='+encodeURIComponent(search)],['Booking','/booking'],['Payment','/payment?bookingId=101'],['Tickets','/booking-success?bookingId=101'],['History','/my-bookings'],['Detail','/my-bookings/101'],['Profile','/profile'],['Operator dashboard','/operator'],['Operator seats','/operator/trips/7/seats'],['Operator bookings','/operator/bookings'],['Operator occupancy','/operator/trips/7/occupancy'],['Operator passengers','/operator/trips/7/passengers'],['Operator trips','/operator/trips'],['Admin','/admin'],['Directory','/admin/operators'],['Onboarding','/admin/operators/new'],['Operator detail','/admin/operators/2']];
productionRouter.dispose();
window.history.replaceState(null,'','/');
const router=createBrowserRouter(productionRouter.routes);
function Preview(){const [role,setRole]=useState('CUSTOMER'),[log,setLog]=useState('');return <><details open data-fixture-controls><summary style={{padding:10,minHeight:44}}>Fixture controls · dữ liệu thử nghiệm</summary><div style={{padding:8,background:'#fff2b9',display:'flex',flexWrap:'wrap',gap:6,fontSize:12}}><strong>SYNTHETIC FIXTURE · no backend</strong>{paths.map(([label,path])=><button key={label} style={{padding:6,fontSize:12}} onClick={()=>{setRole(path.startsWith('/operator')?'OPERATOR_STAFF':path.startsWith('/admin')?'SYSTEM_ADMIN':'CUSTOMER');cache.setQueryData(['me'],{...user,roles:[path.startsWith('/operator')?'OPERATOR_STAFF':path.startsWith('/admin')?'SYSTEM_ADMIN':'CUSTOMER']});if(path==='/booking')sessionStorage.setItem('busgo.hold',JSON.stringify(hold));void router.navigate(path);}}>{label}</button>)}<button onClick={()=>setRole("OPERATOR_ADMIN")}>Operator admin role</button><button onClick={()=>{fail=!fail;void cache.invalidateQueries();}}>Toggle refresh failure</button><button onClick={()=>{empty=!empty;void cache.invalidateQueries();}}>Toggle empty</button><button onClick={()=>{rejectStatus=!rejectStatus;}}>Toggle activation rejection</button><button onClick={()=>{operator={...operator,name:'Tên mới từ máy chủ',updatedAt:new Date().toISOString()};void cache.invalidateQueries();}}>Refresh operator record</button><button onClick={()=>setLog(requests.slice(-8).join('\n'))}>Request log</button><output style={{width:'100%',whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{log}</output></div></details><QueryClientProvider client={cache}><AuthContext.Provider value={{authenticated:true,loading:false,user:{...user,roles:[role]},login(){},logout(){cache.clear();void router.navigate('/login?passwordChanged=1');}}}><RouterProvider router={router}/></AuthContext.Provider></QueryClientProvider></>}
cache.setQueryData(['me'],user);
createRoot(document.getElementById('root')!).render(<Preview/>);
