// Real backend/API acceptance at all required widths. Only this script's new fixtures are changed.
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
const require=createRequire(import.meta.url);
const { chromium }=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const origin=process.env.BUSGO_BROWSER_ORIGIN || 'http://127.0.0.1:5178';
const directory=new URL(process.env.BUSGO_BROWSER_OUTPUT || '../../.tools/m18a-browser/',import.meta.url);mkdirSync(directory,{recursive:true});
async function call(method,path,token,body) {
  const response=await fetch(origin+'/api/v1'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{})});
  return {status:response.status,...await response.json()};
}
async function login(email,password) { const r=await call('POST','/auth/login',null,{email,password});assert.equal(r.status,200,JSON.stringify(r));return r.data.accessToken; }
async function ok(method,path,token,body,status=200) { const r=await call(method,path,token,body);assert.equal(r.status,status,JSON.stringify(r));return r.data; }
async function uiLogin(page,email,password) { await page.goto(origin+'/login');await page.getByLabel('Email',{exact:true}).fill(email);await page.getByLabel('Mật khẩu',{exact:true}).fill(password);await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await page.waitForURL(u=>u.pathname!='/login'); }
function businessDate(time) { return new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(time)); }
function sql(statement) { execFileSync(process.env.MYSQL_CLIENT || 'C:/Program Files/MySQL/MySQL Server 9.2/bin/mysql.exe',['--no-defaults','-h','127.0.0.1','-P',process.env.M18_MYSQL_PORT || '13317','-u','root',process.env.M18_BROWSER_SCHEMA || 'busgo_m18a_browser','-e',statement],{env:{...process.env,MYSQL_PWD:''}}); }
const admin=await login('operator.admin@anphu-demo.example','DemoOperator!2026');const staff=await login('operator.staff@anphu-demo.example','DemoStaff!2026');
const list=await ok('GET','/operator/trips?size=100',admin);const template=await ok('GET',`/operator/trips/${list[0].id}`,admin);
const start=Math.max(Date.now(),...list.map(t=>Date.parse(t.estimatedArrivalTime)))+7*86400000;
const made=await ok('POST','/operator/trips',admin,{operatorRouteId:template.route.operatorRouteId,busId:template.bus.id,departureTime:new Date(start).toISOString()},201);
const trip=await ok('GET',`/operator/trips/${made.id}`,admin);const pickup=trip.stops.find(s=>s.allowPickup),dropoff=trip.stops.at(-1);
const seats=(await ok('GET',`/trips/${trip.id}/seats?pickupLocationId=${pickup.locationId}&dropoffLocationId=${dropoff.locationId}`,null)).seats;
async function phone(index,count,method) { return ok('POST','/operator/bookings',admin,{tripId:trip.id,pickupLocationId:pickup.locationId,dropoffLocationId:dropoff.locationId,tripSeatIds:seats.slice(index,index+count).map(s=>s.tripSeatId),contactName:'M18A Fixture',contactPhone:'0901234567',paymentMethod:method},201); }
const qr=await phone(0,2,'QR_TRANSFER');const link=await ok('POST',`/operator/bookings/${qr.bookingId}/payment-link`,admin);await ok('POST',`/public/payments/${link.path.slice('/pay/'.length)}/mock-confirm`,null);await ok('POST',`/operator/bookings/${qr.bookingId}/cancel`,admin,{});
const cash=await phone(2,2,'PAY_ON_BOARD');await ok('POST',`/operator/bookings/${cash.bookingId}/payments`,admin,{method:'PAY_ON_BOARD'});
const email=`m18a-${Date.now()}@example.test`,password='BrowserCustomer!2026';await ok('POST','/auth/register',null,{fullName:'M18A Customer',email,phone:'090'+String(Date.now()).slice(-7),password},201);const customer=await login(email,password);
const hold=await ok('POST','/seat-holds',customer,{tripId:trip.id,pickupLocationId:pickup.locationId,dropoffLocationId:dropoff.locationId,tripSeatIds:[seats[4].tripSeatId]},201);
const web=await ok('POST','/bookings',customer,{holdToken:hold.holdToken,contactName:'M18A Customer',contactPhone:'0901234567',contactEmail:email},201);await ok('POST',`/bookings/${web.bookingId}/payments/mock-confirm`,customer);
const absent=await phone(5,1,'PAY_ON_BOARD');await phone(6,1,'QR_TRANSFER');
const employee=await ok('POST','/operator/employees',admin,{employeeCode:'M18A-'+Date.now(),fullName:'M18A Driver',phone:'0901234567',status:'ACTIVE',capabilities:['DRIVER'],licenceNumber:'M18A',licenceClass:'DEMO',licenceExpiryDate:'2099-12-31'},201);
await ok('PUT',`/operator/trips/${trip.id}/crew`,admin,{assignments:[{employeeId:employee.id,duty:'DRIVER'}]});await ok('PATCH',`/operator/trips/${trip.id}/status`,admin,{status:'BOARDING'});
const attendance=await ok('GET',`/operator/trips/${trip.id}/attendance`,admin);
const cashItems=attendance.filter(a=>a.bookingId===cash.bookingId);
await ok('POST',`/operator/trips/${trip.id}/tickets/${cashItems[0].ticketId}/direct-board`,admin,{stopId:pickup.id});
await ok('POST',`/operator/trips/${trip.id}/tickets/${cashItems[1].ticketId}/check-in`,admin,{stopId:pickup.id});
await ok('POST',`/operator/trips/${trip.id}/tickets/${attendance.find(a=>a.bookingId===web.bookingId).ticketId}/no-show`,admin,{stopId:pickup.id});
await ok('POST',`/operator/trips/${trip.id}/booking-items/${attendance.find(a=>a.bookingId===absent.bookingId).bookingItemId}/no-show`,admin,{stopId:pickup.id});
const today=businessDate(Date.now()),departure=businessDate(trip.departureTime);const params=new URLSearchParams({fromDate:today,toDate:departure,tripId:String(trip.id)});
const report=await ok('GET','/operator/reports/summary?'+params,admin);
assert.equal(report.collections.totals.grossMockCollections,qr.totalAmount+cash.totalAmount+web.totalAmount);assert.equal(report.collections.totals.mockRefunds,qr.totalAmount);assert.equal(report.collections.totals.netMockCollections,cash.totalAmount+web.totalAmount);
assert.equal(report.bookings.bookingsCreated,5);assert.deepEqual(report.bookings.bySource,{WEB:1,PHONE:4});assert.equal(report.bookings.ticketsIssued,5);assert.equal(report.bookings.validTickets,3);assert.equal(report.bookings.voidTickets,2);
assert.equal(report.attendance.boardedTickets,1);assert.equal(report.attendance.noShowTickets,1);assert.equal(report.attendance.checkedInNotBoarded,1);assert.equal(report.attendance.ticketlessNoShows,1);assert.equal(report.attendance.unresolvedAttendance,1);assert.equal(report.attendance.boardingRate,.5);
const segmentCount=trip.stops.length-1;assert.equal(report.load.paidCells,3*segmentCount);assert.equal(report.load.reservedCells,5*segmentCount);assert.equal(report.load.sellableCells,seats.length*segmentCount);
for(const method of ['MOCK_ONLINE','QR_TRANSFER','PAY_ON_BOARD'])assert.ok(report.collections.byPaymentMethod[method].paidPaymentCount>0);
const route=(await ok('GET','/operator/reports/routes?'+params,admin)).data[0];assert.equal(route.load.paidSegmentLoad,report.load.paidSegmentLoad);
for(const endpoint of ['summary','trips','routes'])assert.equal((await call('GET',`/operator/reports/${endpoint}?${params}`,staff)).status,403);
const browser=await chromium.launch({headless:true,channel:'msedge'});const results=[];let page;
async function screenshot(name) { assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'No horizontal page overflow');await page.screenshot({path:new URL(name+'.png',directory).pathname.replace(/^\/([A-Z]:)/,'$1'),fullPage:true}); }
async function apply(from,to,source='') {
  await page.getByLabel('Từ ngày',{exact:true}).fill(from);await page.getByLabel('Đến ngày',{exact:true}).fill(to);await page.getByLabel('ID chuyến',{exact:true}).fill(String(trip.id));await page.getByLabel('Nguồn booking',{exact:true}).selectOption(source);
  await page.getByRole('button',{name:'Áp dụng',exact:true}).click();
  await page.waitForFunction(({from,to,source})=>{const report=document.querySelector('.report-content');return report?.dataset.fromDate===from&&report?.dataset.toDate===to&&report?.dataset.source===source;},{from,to,source});
  return call('GET','/operator/reports/summary?'+new URLSearchParams({fromDate:from,toDate:to,tripId:String(trip.id),...(source?{bookingSource:source}:{})}),admin);
}
try {
 for(const width of [390,820,1440]) {
  const context=await browser.newContext({viewport:{width,height:1000}});page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));await uiLogin(page,'operator.admin@anphu-demo.example','DemoOperator!2026');await page.goto(origin+'/operator/reports');await page.getByRole('heading',{name:'Báo cáo',exact:true}).waitFor();await page.getByText('Thu tiền mô phỏng · ngày giao dịch',{exact:true}).waitFor();
  const populated=await apply(today,departure);assert.equal(populated.data.collections.totals.grossMockCollections,report.collections.totals.grossMockCollections);await page.getByText('5',{exact:true}).first().waitFor();
  for(const [label,value] of [['Tổng thu mô phỏng',report.collections.totals.grossMockCollections],['Hoàn tiền mô phỏng',report.collections.totals.mockRefunds],['Thu ròng mô phỏng',report.collections.totals.netMockCollections]]) {
    const shown=await page.locator('.report-metrics div').filter({has:page.getByText(label,{exact:true})}).first().locator('dd').innerText();assert.equal(shown,new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND'}).format(value));
  }
  await screenshot(width+'-overview');
  await page.getByRole('button',{name:'Thu tiền',exact:true}).click();for(const method of ['MOCK_ONLINE','QR_TRANSFER','PAY_ON_BOARD'])await page.getByRole('rowheader',{name:new RegExp(method)}).waitFor();await screenshot(width+'-collections');
  const webOnly=await apply(today,departure,'WEB');assert.deepEqual(webOnly.data.bookings.bySource,{WEB:1,PHONE:0});assert.equal(webOnly.data.collections.totals.grossMockCollections,web.totalAmount);
  const empty=await apply('2020-01-01','2020-01-01');assert.equal(empty.data.collections.totals.grossMockCollections,0);await page.getByText(/Không có hoạt động trong kỳ/).waitFor();await screenshot(width+'-empty');await apply(today,departure);
  await page.getByRole('button',{name:'Đặt vé & vé',exact:true}).click();await page.getByRole('heading',{name:'WEB / PHONE · booking mới',exact:true}).waitFor();await screenshot(width+'-bookings');
  await page.getByRole('button',{name:'Chuyến / Tuyến',exact:true}).click();await page.locator('.report-table-wrap').getByRole('link',{name:new RegExp('#'+trip.id)}).count();await page.getByRole('button',{name:'Chuyến',exact:true}).click();await page.getByRole('link',{name:new RegExp('#'+trip.id)}).waitFor();await screenshot(width+'-trips');
  await page.getByRole('button',{name:'Hành khách',exact:true}).click();await page.getByText('Chưa ghi nhận',{exact:true}).waitFor();await screenshot(width+'-attendance');
  await page.goto(origin+'/operator');await page.getByRole('heading',{name:'Tổng quan quản lý hôm nay',exact:true}).waitFor();await screenshot(width+'-dashboard');
  const staffPage=await context.newPage();await uiLogin(staffPage,'operator.staff@anphu-demo.example','DemoStaff!2026');await staffPage.goto(origin+'/operator/reports');assert.equal(await staffPage.getByRole('heading',{name:'Thu tiền mô phỏng · ngày giao dịch'}).count(),0);assert.equal(await staffPage.getByRole('link',{name:'Báo cáo',exact:true}).count(),0);
  assert.deepEqual(errors,[]);results.push({width,admin:true,dateFilter:true,moneyMatchesFixture:true,sources:true,methods:true,load:true,attendance:true,staffDenied:true,overflow:false,pageErrors:errors});await context.close();
 }
 // Remove an available cell of this newly created trip only, and prove API/UI suppression.
 assert.match(String(trip.id),/^\d+$/);assert.match(String(seats[7].tripSeatId),/^\d+$/);
 sql(`DELETE v FROM trip_seat_segment_inventory v JOIN trip_segments g ON g.id=v.trip_segment_id WHERE g.trip_id=${trip.id} AND v.trip_seat_id=${seats[7].tripSeatId} AND g.segment_order=1 AND v.status='AVAILABLE'`);
 const incomplete=await ok('GET','/operator/reports/summary?'+params,admin);assert.equal(incomplete.load.complete,false);assert.equal(incomplete.load.missingCells,1);assert.equal(incomplete.load.paidSegmentLoad,null);
 for(const width of [390,820,1440]) {const context=await browser.newContext({viewport:{width,height:1000}});page=await context.newPage();await uiLogin(page,'operator.admin@anphu-demo.example','DemoOperator!2026');await page.goto(origin+'/operator/reports');await apply(today,departure);await page.getByText(/Inventory chưa đầy đủ · thiếu 1/).waitFor();await screenshot(width+'-incomplete');results.find(r=>r.width===width).incompleteWarning=true;await context.close();}
} catch(error) {if(page&&!page.isClosed()){console.log((await page.locator('body').innerText()).slice(0,5000));await screenshot('failure');}throw error;}finally{await browser.close();writeFileSync(new URL('results.json',directory),JSON.stringify({tripId:trip.id,report,results},null,2));}
console.log(JSON.stringify(results,null,2));
