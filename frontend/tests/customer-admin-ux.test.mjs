import assert from 'node:assert/strict';
import { test } from 'node:test';
import { register } from 'node:module';
import { readFileSync } from 'node:fs';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
register('./helpers/tsx-loader.mjs', import.meta.url);
const { AuthContext } = await import('../src/features/auth/AuthProvider.tsx');
const { CustomerLayout } = await import('../src/layouts/CustomerLayout.tsx');
const { TripCard, Field } = await import('../src/components/ui.tsx');
const { SeatMap } = await import('../src/features/trip/SeatMap.tsx');
const { SearchForm } = await import('../src/features/search/SearchForm.tsx');
const { SearchPage } = await import('../src/pages/SearchPage.tsx');
const { BookingPage } = await import('../src/pages/BookingPage.tsx');
const { PaymentPage } = await import('../src/pages/PaymentPage.tsx');
const { TicketPage } = await import('../src/pages/TicketPage.tsx');
const { MyBookingsPage } = await import('../src/pages/MyBookingsPage.tsx');
const { ProfilePage } = await import('../src/pages/ProfilePage.tsx');
const { AuthPage } = await import('../src/pages/AuthPage.tsx');
const { AdminHomePage, AdminOperatorCreatePage, OperatorDirectory, ContactEditor, OperatorDetail, AdminStaffTable } = await import('../src/pages/admin/AdminPages.tsx');
const model = await import('../src/features/customer/presentation.ts');
const { blockingQueryError } = await import('../src/features/customer/QueryFeedback.tsx');
const { adminTotalRequests, legacyCreatePath, ADMIN_SEARCH_DELAY } = await import('../src/features/admin/presentation.ts');
const { adminOperatorFilters } = await import('../src/features/operator/management.ts');
const { adminApi } = await import('../src/api/adminApi.ts');
const { apiClient } = await import('../src/api/client.ts');
const h=React.createElement;
const user={id:1,fullName:'Liên hệ thử nghiệm',email:'fixture@example.invalid',phone:'0900000000',roles:['CUSTOMER']};
const pickup={tripStopId:11,locationId:1,name:'Điểm A',departureTime:'2027-01-15T22:30:00+07:00',time:'2027-01-15T22:30:00+07:00'};
const dropoff={tripStopId:12,locationId:2,name:'Điểm B',arrivalTime:'2027-01-16T05:30:00+07:00',time:'2027-01-16T05:30:00+07:00'};
const trip={tripId:7,operator:{id:1,name:'Nhà xe'},route:{id:1,name:'Tuyến đầy đủ'},busType:{id:1,name:'Giường nằm'},busImageUrl:null,pickup,dropoff,durationMinutes:420,price:350000,availableSeats:5};
const seats=Array.from({length:7},(_,i)=>({tripSeatId:i+1,seatCode:'A'+(i+1),row:i+1,column:i%2?3:1,floor:1,seatType:'STANDARD',available:i!==6}));
const booking={bookingId:101,bookingCode:'BG-101',status:'PENDING',tripId:7,operator:trip.operator,route:trip.route,pickup,dropoff,contact:{name:user.fullName,email:user.email,phone:user.phone},seats:seats.slice(0,5).map(s=>({...s,passengerName:null,unitPrice:350000})),pricePerSeat:350000,totalAmount:1750000,createdAt:pickup.time};
const operator={id:2,name:'Nhà xe thử nghiệm',code:'NX-2',status:'ACTIVE',phone:null,email:null,address:null,staffCounts:{total:7,active:5,admins:2,staff:5},operationalCounts:{buses:12,routes:4,trips:38,bookings:105},createdAt:pickup.time,updatedAt:pickup.time};
function render(component,{path='/',roles=['CUSTOMER'],seed=()=>{}}={}) {
 const cache=new QueryClient({defaultOptions:{queries:{retry:false}}});cache.setQueryData(['me'],{...user,roles});seed(cache);
 globalThis.window={matchMedia:()=>({matches:false}),location:{origin:'http://localhost:5173'}};
 try{return renderToStaticMarkup(h(QueryClientProvider,{client:cache},h(AuthContext.Provider,{value:{authenticated:true,loading:false,user:{...user,roles},logout(){},login(){}}},h(MemoryRouter,{initialEntries:[path]},component))));}finally{cache.clear();delete globalThis.window;}
}
test('customer shell exposes customer destinations only to CUSTOMER roles',()=>{
 for(const role of ['SYSTEM_ADMIN','OPERATOR_ADMIN','OPERATOR_STAFF']){const html=render(h(CustomerLayout),{roles:[role]});assert.doesNotMatch(html,/href="\/(profile|my-bookings)"/);assert.match(html,/Khu vực quản lý/);}
 assert.match(render(h(CustomerLayout)),/href="\/my-bookings"/);
 assert.match(render(h(CustomerLayout),{roles:['SYSTEM_ADMIN','CUSTOMER']}),/href="\/profile"/);
});
test('journey URL roundtrip preserves IDs, labels, date and filters but resets page',()=>{
 const values={pickupLocationId:1,pickupLabel:'Điểm A',dropoffLocationId:2,dropoffLabel:'Điểm B',departureDate:'2027-01-15'};
 const p=model.journeyParams(values,new URLSearchParams('minPrice=100&page=4&sort=PRICE_ASC'));
 assert.deepEqual(model.readJourney(p),values);assert.equal(p.get('page'),null);assert.equal(p.get('sort'),'PRICE_ASC');assert.equal(p.get('minPrice'),'100');
});
test('swap exchanges the selected ID and label together, retaining date',()=>{
 const v={pickupLocationId:1,pickupLabel:'A',dropoffLocationId:2,dropoffLabel:'B',departureDate:'2027-01-15'};
 assert.deepEqual(model.swapJourney(v),{...v,pickupLocationId:2,pickupLabel:'B',dropoffLocationId:1,dropoffLabel:'A'});assert.deepEqual(model.swapJourney(model.swapJourney(v)),v);
});
const search='/?pickupLocationId=1&pickupLabel=Điểm+A&dropoffLocationId=2&dropoffLabel=Điểm+B&departureDate=2027-01-15';
test('controlled search editor initializes all selected journey fields',()=>{const html=render(h(SearchForm),{path:search});assert.match(html,/value="Điểm A"/);assert.match(html,/value="Điểm B"/);assert.match(html,/value="2027-01-15"/);assert.match(html,/Đổi điểm đón và điểm trả/);});
test('zero-result heading preserves journey without relying on result records',()=>{
 const params=new URLSearchParams(search.split('?')[1]);const request=Object.fromEntries([...params].filter(([key])=>!key.endsWith('Label')));
 const html=render(h(SearchPage),{path:search,seed:c=>c.setQueryData(['search',request],{data:[],pagination:{page:0,size:10,totalPages:0,totalElements:0}})});
 assert.match(html,/Điểm A → Điểm B/);assert.match(html,/Chưa có chuyến cho hành trình và ngày này/);
});
test('five-seat limit remains distinct from unavailable inventory and preserves geometry',()=>{
 const html=render(h(SeatMap,{seats,selected:[1,2,3,4,5],disabled:false,onToggle(){}}));assert.match(html,/Bạn có thể chọn tối đa 5 chỗ/);assert.match(html,/seat  limit-disabled/);assert.match(html,/seat  unavailable/);assert.match(html,/grid-column:3/);assert.match(html,/Chỗ tiêu chuẩn/);assert.doesNotMatch(html,/STANDARD/);
 assert.equal(model.seatLimitDisabled(true,false,5),true);assert.equal(model.seatLimitDisabled(false,false,5),false);assert.equal(model.seatLimitDisabled(true,true,5),false);
});
test('generic bus image has truthful alt text and overnight arrival date',()=>{const html=render(h(TripCard,{trip,searchContext:'/search?pickupLocationId=1'}));assert.match(html,/Ảnh xe khách minh họa/);assert.doesNotMatch(html,/alt="Xe Giường nằm của Nhà xe"/);assert.match(html,/16\/01\/2027/);assert.match(html,/search=%2Fsearch/);assert.match(html,/Chọn chỗ/);});
test('booking summary and countdown remain available alongside the contact form',()=>{
 const hold={...trip,holdToken:'fixture',expiresAt:new Date(Date.now()+600000).toISOString(),status:'ACTIVE',seats:booking.seats,pricePerSeat:350000,totalPrice:1750000};
 globalThis.sessionStorage={getItem:()=>JSON.stringify(hold)};
 try{const html=render(h(BookingPage),{path:'/booking',seed:c=>c.setQueryData(['active-hold',1,hold.expiresAt],hold)});assert.match(html,/booking-checkout/);assert.match(html,/Thông tin liên hệ đặt vé/);assert.match(html,/countdown/);assert.match(html,/A5/);assert.match(html,/1\.750\.000/);}finally{delete globalThis.sessionStorage;}
 const css=readFileSync(new URL('../src/features/customer/customer.css',import.meta.url),'utf8');assert.match(css,/grid-template-areas: "summary" "form"/);
});
test('payment states expose only appropriate confirmation or ticket actions',()=>{
 for(const status of ['PENDING','CONFIRMED','CANCELLED']){const html=render(h(PaymentPage),{path:'/payment?bookingId=101',seed:c=>c.setQueryData(['booking',1,'101'],{...booking,status})});assert.match(html,/Không có giao dịch ngân hàng/);if(status==='CONFIRMED'){assert.match(html,/Xem vé điện tử/);assert.doesNotMatch(html,/>Xác nhận thanh toán giả lập</);}if(status==='CANCELLED')assert.doesNotMatch(html,/>Xác nhận thanh toán giả lập</);}
 assert.match(model.paymentPresentation('PENDING',true),/Đang xác nhận/);
});
test('repeat tickets group journey once with one QR per seat and accurate identity labels',()=>{
 const bundle={...booking,status:'CONFIRMED',amount:1750000,paymentStatus:'PAID',paymentMethod:'MOCK_QR',departureTime:pickup.time,arrivalTime:dropoff.time,tickets:[1,2].map(i=>({ticketId:i,ticketCode:'VE-'+i,seatCode:'A'+i,passengerName:'Tên trên vé thử nghiệm',qrData:'fixture-'+i}))};
 const html=render(h(TicketPage),{path:'/booking-success?bookingId=101',seed:c=>c.setQueryData(['tickets',1,'101'],bundle)});assert.match(html,/Vé điện tử của bạn/);assert.doesNotMatch(html,/Đặt vé thành công!/);assert.equal((html.match(/class="journey"/g)||[]).length,1);assert.equal((html.match(/Tên trên vé<\/small>/g)||[]).length,2);assert.match(html,/VE-1/);assert.match(html,/VE-2/);
 assert.equal(model.ticketHeading(true),'Đặt vé thành công!');
});
test('nullable passenger identity is not silently replaced with contact name',()=>{assert.equal(model.passengerLabel(null),'Chưa cung cấp tên khách trên chỗ');assert.equal(model.passengerLabel('Lan'),'Lan');});
test('empty history distinguishes no bookings from filtered no matches',()=>{
 for(const status of ['', 'CANCELLED']){const html=render(h(MyBookingsPage),{path:'/my-bookings'+(status?'?status='+status:''),seed:c=>c.setQueryData(['bookings',1,status,0],{data:[],pagination:{page:0,size:10,totalPages:0,totalElements:0}})});assert.ok(html.includes(model.historyEmpty(!!status)));assert.match(html,status?/Xem tất cả đặt vé/:/Tìm chuyến xe/);}
});
test('profile explains immutable email and password fields expose visibility controls',()=>{const html=render(h(ProfilePage));assert.match(html,/Email dùng để đăng nhập và hiện chưa hỗ trợ thay đổi/);assert.match(html,/aria-label="Hiện mật khẩu hiện tại"/);assert.match(html,/aria-describedby=/);});
test('password completion feedback survives redirect to sign in',()=>{assert.match(render(h(AuthPage),{path:'/login?passwordChanged=1'}),/Đã đổi mật khẩu thành công/);});
test('field validation descriptions are associated with the actual input',()=>{const html=render(h(Field,{label:'Tên',description:'Gợi ý',error:'Bắt buộc',id:'name'}));assert.match(html,/aria-describedby="name-hint name-error"/);assert.match(html,/id="name-error"/);assert.match(html,/aria-labelledby="name-label"/);assert.match(html,/id="name-label"/);});
test('dashboard uses independent status requests and server totalElements',async()=>{
 const calls=[];const original=apiClient.defaults.adapter;apiClient.defaults.adapter=async config=>{calls.push(config.params);return {config,status:200,headers:{},data:{data:[],pagination:{page:0,size:1,totalElements:42,totalPages:42}}};};
 try{for(const item of adminTotalRequests)assert.equal((await adminApi.operators(item.params)).pagination.totalElements,42);}finally{apiClient.defaults.adapter=original;}
 assert.deepEqual(calls.map(c=>c.status),[undefined,'ACTIVE','INACTIVE']);assert.ok(calls.every(c=>c.size===1));
 const html=render(h(AdminHomePage),{roles:['SYSTEM_ADMIN'],seed:c=>adminTotalRequests.forEach(item=>c.setQueryData(['admin','operators','total',item.params],{data:[],pagination:{totalElements:42}}))});assert.equal((html.match(/<strong>42<\/strong>/g)||[]).length,3);
});
test('admin search has bounded debounce and serializes validated filters',()=>{assert.equal(ADMIN_SEARCH_DELAY,350);assert.deepEqual(adminOperatorFilters(new URLSearchParams('q=+Nhà+xe+&status=ACTIVE&page=2&size=10')),{q:'Nhà xe',status:'ACTIVE',page:2,size:10});});
test('new onboarding route and legacy create compatibility are explicit',()=>{assert.equal(legacyCreatePath(new URLSearchParams('create=1')),'/admin/operators/new');assert.equal(legacyCreatePath(new URLSearchParams()),null);const routes=readFileSync(new URL('../src/routes/router.tsx',import.meta.url),'utf8');assert.match(routes,/path: "operators\/new"/);const html=render(h(AdminOperatorCreatePage),{roles:['SYSTEM_ADMIN']});assert.match(html,/không có email mời/);assert.match(html,/Trạng thái ban đầu/);assert.match(html,/Quản trị viên ban đầu/);});
test('directory combines contact and accurately labels active membership/admin counts',()=>{const html=render(h(OperatorDirectory,{rows:[{...operator,activeStaffCount:5,activeAdminCount:1}]}));assert.match(html,/5 thành viên hoạt động/);assert.match(html,/1 quản trị viên hoạt động, có thể đăng nhập/);assert.match(html,/<strong>Nhà xe thử nghiệm<\/strong><\/a>/);assert.doesNotMatch(html,/Ngày tạo/);});
test('contact editor uses a stable initial snapshot and explicit dirty-discard flow',()=>{const html=render(h(ContactEditor,{operator,close(){},saved(){}}));assert.match(html,/value="Nhà xe thử nghiệm"/);assert.match(html,/Hủy chỉnh sửa/);const source=readFileSync(new URL('../src/pages/admin/AdminPages.tsx',import.meta.url),'utf8');assert.match(source,/\[initial\] = useState\(operator\)/);assert.doesNotMatch(source,/key=\{.*updatedAt/);assert.match(source,/Bỏ các thay đổi chưa lưu/);});
test('operator detail is read-only first with precise totals and consequential controls',()=>{const html=render(h(OperatorDetail,{operator}),{roles:['SYSTEM_ADMIN']});assert.match(html,/Chỉnh sửa/);assert.doesNotMatch(html,/Lưu thông tin liên hệ/);assert.match(html,/Tổng chuyến/);assert.match(html,/admin-danger/);assert.doesNotMatch(html,/Xe \/ tuyến vận hành/);});
test('staff view remains read only and distinguishes membership from account status',()=>{const html=render(h(AdminStaffTable,{rows:[{staffId:1,staffCode:null,user:{...user,status:'LOCKED'},role:'OPERATOR_STAFF',membershipStatus:'ACTIVE',createdAt:pickup.time}]}));assert.match(html,/Tài khoản: Đã khóa/);assert.match(html,/Hoạt động/);assert.doesNotMatch(html,/<button/);});
test('cached content is retained only for transient failures, never authorization/not-found',()=>{for(const status of [401,403,404,409])assert.equal(blockingQueryError({isError:true,data:{},error:{response:{status}}}),true);assert.equal(blockingQueryError({isError:true,data:{},error:new Error()}),false);assert.equal(blockingQueryError({isError:true,data:{},error:{response:{status:503}}}),false);assert.equal(blockingQueryError({isError:true,data:undefined,error:new Error()}),true);});
