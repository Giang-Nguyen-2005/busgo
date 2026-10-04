// Real API + browser fixtures; run manually against the isolated M18B schema.
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const origin=process.env.BUSGO_BROWSER_ORIGIN || 'http://127.0.0.1:5179';
const output=new URL('../../.tools/m18b-browser/',import.meta.url);mkdirSync(output,{recursive:true});
const stamp=Date.now();
async function call(method,path,token,body) { const r=await fetch(origin+'/api/v1'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{})});return {status:r.status,...await r.json()}; }
async function ok(method,path,token,body,status=200) { const r=await call(method,path,token,body);assert.equal(r.status,status,JSON.stringify(r));return r.data; }
async function login(email,password) { return (await ok('POST','/auth/login',null,{email,password})).accessToken; }
async function uiLogin(page,email,password) {await page.goto(origin+'/login');await page.getByLabel('Email',{exact:true}).fill(email);await page.getByLabel('Mật khẩu',{exact:true}).fill(password);await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await page.waitForURL(u=>u.pathname!='/login');}
function sql(statement) {return execFileSync(process.env.MYSQL_CLIENT || 'C:/Program Files/MySQL/MySQL Server 9.2/bin/mysql.exe',['--no-defaults','-h','127.0.0.1','-P','13318','-u','root','busgo_m18b_browser','-e',statement],{env:{...process.env,MYSQL_PWD:''},encoding:'utf8'});}
const admin=await login('operator.admin@anphu-demo.example','DemoOperator!2026');
const staff=await login('operator.staff@anphu-demo.example','DemoStaff!2026');
const initial=await ok('GET','/operator/customers?q=empty-m18b-'+stamp,admin);assert.equal(initial.pagination.totalElements,0);
const list=await ok('GET','/operator/trips?size=100',admin);const template=await ok('GET',`/operator/trips/${list[0].id}`,admin);
const made=await ok('POST','/operator/trips',admin,{operatorRouteId:template.route.operatorRouteId,busId:template.bus.id,departureTime:new Date(Math.max(Date.now(),...list.map(t=>Date.parse(t.estimatedArrivalTime)))+7*86400000).toISOString()},201);
const trip=await ok('GET',`/operator/trips/${made.id}`,admin);const pickup=trip.stops.find(s=>s.allowPickup),dropoff=trip.stops.at(-1);
const seats=(await ok('GET',`/trips/${trip.id}/seats?pickupLocationId=${pickup.locationId}&dropoffLocationId=${dropoff.locationId}`,null)).seats;
async function phone(index,count,method,name) {return ok('POST','/operator/bookings',admin,{tripId:trip.id,pickupLocationId:pickup.locationId,dropoffLocationId:dropoff.locationId,tripSeatIds:seats.slice(index,index+count).map(s=>s.tripSeatId),contactName:name,contactPhone:'0901234567',contactEmail:`phone-${stamp}@example.test`,paymentMethod:method},201);}
const refunded=await phone(0,2,'QR_TRANSFER','M18B Refund');const link=await ok('POST',`/operator/bookings/${refunded.bookingId}/payment-link`,admin);await ok('POST',`/public/payments/${link.path.slice(5)}/mock-confirm`,null);await ok('POST',`/operator/bookings/${refunded.bookingId}/cancel`,admin,{});
const mixed=await phone(2,2,'PAY_ON_BOARD','M18B Mixed');await ok('POST',`/operator/bookings/${mixed.bookingId}/payments`,admin,{method:'PAY_ON_BOARD'});
const absent=await phone(5,1,'PAY_ON_BOARD','M18B Absent');
const email=`m18b-${stamp}@example.test`,password='BrowserCustomer!2026';
await ok('POST','/auth/register',null,{fullName:'Global Account Profile',email,phone:'0909999999',password},201);const customer=await login(email,password);
const me=await ok('GET','/users/me',customer);
async function web(index,name) {const hold=await ok('POST','/seat-holds',customer,{tripId:trip.id,pickupLocationId:pickup.locationId,dropoffLocationId:dropoff.locationId,tripSeatIds:[seats[index].tripSeatId]},201);return ok('POST','/bookings',customer,{holdToken:hold.holdToken,contactName:name,contactPhone:'0901234567',contactEmail:email},201);}
const webBooking=await web(4,'M18B Web Contact');await ok('POST',`/bookings/${webBooking.bookingId}/payments/mock-confirm`,customer);await web(6,'M18B Latest Contact');
// Enough genuine history for the existing UI's minimum page size (10).
for(let i=0;i<9;i++) {const b=await web(7,'M18B Latest Contact');await ok('POST',`/bookings/${b.bookingId}/cancel`,customer,{});}
for(let i=0;i<8;i++) {const b=await phone(8,1,'PAY_ON_BOARD','M18B Directory Page '+i);await ok('POST',`/operator/bookings/${b.bookingId}/cancel`,admin,{});}
const employee=await ok('POST','/operator/employees',admin,{employeeCode:'M18B-'+stamp,fullName:'M18B Driver',phone:'0901234567',status:'ACTIVE',capabilities:['DRIVER'],licenceNumber:'M18B',licenceClass:'DEMO',licenceExpiryDate:'2099-12-31'},201);
await ok('PUT',`/operator/trips/${trip.id}/crew`,admin,{assignments:[{employeeId:employee.id,duty:'DRIVER'}]});await ok('PATCH',`/operator/trips/${trip.id}/status`,admin,{status:'BOARDING'});
const manifest=await ok('GET',`/operator/trips/${trip.id}/attendance`,admin);const mixedItems=manifest.filter(a=>a.bookingId===mixed.bookingId);
await ok('POST',`/operator/trips/${trip.id}/tickets/${mixedItems[0].ticketId}/direct-board`,admin,{stopId:pickup.id});
await ok('POST',`/operator/trips/${trip.id}/tickets/${mixedItems[1].ticketId}/no-show`,admin,{stopId:pickup.id});
await ok('POST',`/operator/trips/${trip.id}/tickets/${manifest.find(a=>a.bookingId===webBooking.bookingId).ticketId}/check-in`,admin,{stopId:pickup.id});
await ok('POST',`/operator/trips/${trip.id}/booking-items/${manifest.find(a=>a.bookingId===absent.bookingId).bookingItemId}/no-show`,admin,{stopId:pickup.id});
const accountKey='ACCOUNT:'+me.id;const refundKey='CONTACT:'+refunded.bookingId;const mixedKey='CONTACT:'+mixed.bookingId;const absentKey='CONTACT:'+absent.bookingId;
const account=await ok('GET','/operator/customers/'+accountKey,admin);assert.equal(account.summary.totalBookings,11);assert.equal(account.summary.displayName,'M18B Latest Contact');assert.equal(account.summary.attendance.checkedIn,1);assert.equal(account.summary.attendance.unrecorded,10);
const refund=await ok('GET','/operator/customers/'+refundKey,admin);assert.deepEqual(refund.summary.money,{grossMockPaid:refunded.totalAmount,mockRefunds:refunded.totalAmount,netMockPaid:0});assert.equal(refund.bookings.data[0].voidTickets,2);
const mix=await ok('GET','/operator/customers/'+mixedKey,admin);assert.deepEqual(mix.summary.attendance,{boarded:1,noShow:1,checkedIn:0,unrecorded:0});assert.equal(mix.summary.boardedJourneys,1);
const noShow=await ok('GET','/operator/customers/'+absentKey,admin);assert.equal(noShow.summary.attendance.noShow,1);assert.equal(noShow.bookings.data[0].validTickets,0);assert.equal(noShow.summary.money.grossMockPaid,0);
// Create a new platform-test account only, grant its fixture role, then create foreign operator via API.
const sysEmail=`m18b-system-${stamp}@example.test`;const sys=await ok('POST','/auth/register',null,{fullName:'M18B Platform fixture',email:sysEmail,phone:'0907777777',password},201);assert.match(String(sys.id),/^\d+$/);
sql(`DELETE FROM user_roles WHERE user_id=${sys.id}; INSERT INTO user_roles(user_id,role_id) SELECT ${sys.id},id FROM roles WHERE code='SYSTEM_ADMIN';`);
const system=await login(sysEmail,password);const foreignEmail=`m18b-foreign-${stamp}@example.test`;
await ok('POST','/admin/operators',system,{name:'M18B Foreign',code:'M18B-'+stamp,status:'ACTIVE',initialAdmin:{fullName:'M18B Foreign Admin',email:foreignEmail,phone:'0908888888',password,staffCode:'ADMIN'}},201);
const foreign=await login(foreignEmail,password);
const foreignDirectory=await ok('GET','/operator/customers',foreign);assert.equal(foreignDirectory.data.length,0);
assert.equal((await call('GET','/operator/customers/'+accountKey,foreign)).status,404);assert.equal((await call('GET','/operator/customers/'+refundKey,foreign)).status,404);
const route=await ok('POST','/operator/routes',foreign,{routeId:template.route.routeId},201);
const bus=await ok('POST','/operator/buses',foreign,{licensePlate:'M18B-'+stamp,busTypeId:template.bus.busTypeId},201);
const foreignTrip=await ok('POST','/operator/trips',foreign,{operatorRouteId:route.id,busId:bus.id,departureTime:new Date(Date.now()+30*86400000).toISOString()},201);
// Isolation is also proven for the same account using a foreign operator, through a fresh WEB hold.
// Fare/booking fixtures below use the same journey price from the source operator.
const foreignDetail=await ok('GET',`/operator/trips/${foreignTrip.id}`,foreign);
const sourceFares=await ok('GET',`/operator/routes/${template.route.operatorRouteId}/fares`,admin);
await ok('PUT',`/operator/routes/${route.id}/fares`,foreign,{fares:sourceFares.map(f=>({fromRouteStopId:f.fromRouteStopId,toRouteStopId:f.toRouteStopId,price:f.price,status:f.status}))});
const fp=foreignDetail.stops.find(s=>s.allowPickup),fd=foreignDetail.stops.at(-1);const fs=(await ok('GET',`/trips/${foreignTrip.id}/seats?pickupLocationId=${fp.locationId}&dropoffLocationId=${fd.locationId}`,null)).seats;
const fh=await ok('POST','/seat-holds',customer,{tripId:foreignTrip.id,pickupLocationId:fp.locationId,dropoffLocationId:fd.locationId,tripSeatIds:[fs[0].tripSeatId]},201);
const fb=await ok('POST','/bookings',customer,{holdToken:fh.holdToken,contactName:'Foreign secret contact',contactPhone:'0901234567',contactEmail:'foreign-secret@example.test'},201);
const offlineForeign=await ok('POST','/operator/bookings',foreign,{tripId:foreignTrip.id,pickupLocationId:fp.locationId,dropoffLocationId:fd.locationId,tripSeatIds:[fs[1].tripSeatId],contactName:'Foreign phone secret',contactPhone:'0901234567',paymentMethod:'PAY_ON_BOARD'},201);
assert.equal((await ok('GET','/operator/customers/'+accountKey,admin)).summary.totalBookings,11);assert.equal((await ok('GET','/operator/customers/'+accountKey,foreign)).summary.totalBookings,1);
assert.equal((await call('GET','/operator/customers/CONTACT:'+offlineForeign.bookingId,admin)).status,404);
assert.equal((await ok('GET','/operator/customers?q=Foreign',admin)).data.length,0);
for(const token of [staff,customer,system,null]) for(const path of ['/operator/customers','/operator/customers/'+accountKey]) assert.equal((await call('GET',path,token)).status,token?403:401);
const browser=await chromium.launch({headless:true,channel:'msedge'});const results=[];
try {
  for(const width of [390,820,1440]) {
    const context=await browser.newContext({viewport:{width,height:1000}});const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await uiLogin(page,'operator.admin@anphu-demo.example','DemoOperator!2026');await page.goto(origin+'/operator/customers');await page.getByRole('heading',{name:'Khách hàng',exact:true}).waitFor();await page.getByText('M18B Latest Contact',{exact:true}).filter({visible:true}).first().waitFor();
    async function shot(name) { assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Horizontal page overflow');await page.screenshot({path:new URL(width+'-'+name+'.png',output).pathname.replace(/^\/([A-Z]:)/,'$1'),fullPage:true}); }
    await shot('directory');
    for(const q of ['M18B Web Contact','0901234567',email]) { await page.getByLabel('Tìm tên, điện thoại, email hoặc mã booking').fill(q);await page.getByRole('button',{name:'Tìm kiếm',exact:true}).click();await page.waitForURL(u=>u.searchParams.get('q')===q);await page.getByText('M18B Latest Contact',{exact:true}).filter({visible:true}).first().waitFor(); }
    await page.goto(origin+'/operator/customers?q=not-found-contact');await page.getByText('Chưa có khách hàng phù hợp',{exact:true}).waitFor();await shot('empty');
    for(const [key,label] of [[accountKey,'account'],[refundKey,'refund'],[mixedKey,'mixed'],[absentKey,'ticketless']]) {
      await page.goto(origin+'/operator/customers/'+encodeURIComponent(key));await page.getByRole('heading',{name:'Lịch sử đặt vé',exact:true}).waitFor();
      if(label==='account') {await page.getByText('Khách có tài khoản',{exact:true}).waitFor();assert.ok((await page.locator('body').innerText()).includes('M18B Web Contact'));assert.equal(await page.getByText('Global Account Profile',{exact:true}).count(),0);}
      if(label==='mixed') await page.getByText('1 đã lên xe / 1 vắng mặt',{exact:true}).waitFor();
      if(label==='refund') {await page.getByText(/Hủy: Nhà xe hủy/).waitFor();await page.getByText('Giao dịch mô phỏng',{exact:true}).click();await page.getByText(/Hoàn tiền mô phỏng:/).waitFor();}
      if(label==='ticketless') await page.getByText('1 vắng mặt',{exact:true}).waitFor();
      assert.ok(await page.locator('a[href^="/operator/bookings/"]').count());await shot(label);
    }
    await page.goto(origin+'/operator/customers/'+encodeURIComponent(accountKey)+'?size=10');await page.getByRole('button',{name:'Sau',exact:true}).click();await page.waitForURL(u=>u.searchParams.get('page')==='1');await page.getByText('M18B Web Contact',{exact:false}).first().waitFor();await shot('history-page2');
    await page.goto(origin+'/operator/customers?size=10');await page.getByRole('button',{name:'Sau',exact:true}).click();await page.waitForURL(u=>u.searchParams.get('page')==='1');await shot('directory-page2');
    await page.goto(origin+'/operator/customers?q=Foreign');await page.getByText('Chưa có khách hàng phù hợp',{exact:true}).waitFor();
    const staffPage=await context.newPage();await uiLogin(staffPage,'operator.staff@anphu-demo.example','DemoStaff!2026');await staffPage.goto(origin+'/operator/customers');await staffPage.getByText('403 — Không có quyền truy cập',{exact:true}).waitFor();assert.equal(await staffPage.getByRole('link',{name:'Khách hàng',exact:true}).count(),0);
    const foreignPage=await context.newPage();await uiLogin(foreignPage,foreignEmail,password);await foreignPage.goto(origin+'/operator/customers');await foreignPage.getByText('Foreign phone secret',{exact:true}).filter({visible:true}).first().waitFor();assert.equal(await foreignPage.getByText('M18B Latest Contact',{exact:true}).count(),0);
    assert.deepEqual(errors,[]);results.push({width,account:true,offline:true,refund:true,mixedAttendance:true,ticketlessNoShow:true,search:true,pagination:true,isolation:true,staffDenied:true,pageOverflow:false,pageErrors:errors});await context.close();
  }
} finally {await browser.close();writeFileSync(new URL('results.json',output),JSON.stringify({tripId:trip.id,account,refund,mix,noShow,foreignBooking:fb.bookingId,results},null,2));}
console.log(JSON.stringify(results,null,2));

