// Live browser verification. Uses real API fixtures; no inventory/payment/operations stubs.
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const origin = process.env.BUSGO_BROWSER_ORIGIN || 'http://127.0.0.1:5176';
const api = origin + '/api/v1';
async function call(method,path,token,body) {
  const response = await fetch(api+path,{ method,headers:{ 'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{}) },...(body?{body:JSON.stringify(body)}:{}) });
  return { status:response.status,...await response.json() };
}
const auth = await call('POST','/auth/login',null,{email:'operator.admin@anphu-demo.example',password:'DemoOperator!2026'});
assert.equal(auth.status,200); const token=auth.data.accessToken;
const listed=(await call('GET','/operator/trips?size=100',token)).data;
const fixtureStart=Math.max(Date.now(),...listed.map(t=>Date.parse(t.estimatedArrivalTime)))+7*86400000;
const candidates=[];
for(const summary of listed) {
  const t=(await call('GET',`/operator/trips/${summary.id}`,token)).data;
  if(t.stops.filter(s=>s.allowPickup).length>1 && new Date(t.departureTime)>new Date()) candidates.push(t);
}
assert.ok(candidates.length>=1,'A future intermediate-pickup trip template is required');
const directory=new URL(process.env.BUSGO_BROWSER_OUTPUT || '../../.tools/m16b-browser/',import.meta.url); mkdirSync(directory,{recursive:true});
const browser=await chromium.launch({headless:true,channel:'msedge'}); const results=[]; let lastPage;
async function screenshot(page,name) {
  await page.evaluate(()=>window.scrollTo(0,0));
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'No document horizontal overflow');
  await page.screenshot({path:new URL(name+'.png',directory).pathname.replace(/^\/([A-Z]:)/,'$1'),fullPage:true});
}
async function mutation(page,method,path,click,status=200) {
  const wait=page.waitForResponse(r=>r.url().endsWith('/api/v1'+path)&&r.request().method()===method);
  const [,response]=await Promise.all([click(),wait]); assert.equal(response.status(),status,await response.text()); return (await response.json()).data;
}
async function lifecycle(page,id,label,status=200) {
  await page.getByRole('button',{name:label,exact:true}).click();
  return mutation(page,'PATCH',`/operator/trips/${id}/status`,()=>page.getByRole('dialog').getByRole('button',{name:'Xác nhận',exact:true}).click(),status);
}
async function close(page,id,stop) {
  await page.getByRole('button',{name:`Đóng điểm đón ${stop.locationName}`,exact:true}).click();
  await mutation(page,'POST',`/operator/trips/${id}/stops/${stop.id}/close-pickup`,()=>page.getByRole('button',{name:'Xác nhận đóng điểm đón',exact:true}).click());
}
async function prepareBooking(trip,pickup,seat,paid) {
  const dropoff=trip.stops.at(-1);
  const b=await call('POST','/operator/bookings',token,{tripId:trip.id,pickupLocationId:pickup.locationId,dropoffLocationId:dropoff.locationId,tripSeatIds:[seat.tripSeatId],contactName:'M16B '+seat.seatCode,contactPhone:'0901234567',paymentMethod:'PAY_ON_BOARD'});
  assert.equal(b.status,201,JSON.stringify(b));
  if(paid) assert.equal((await call('POST',`/operator/bookings/${b.data.bookingId}/payments`,token,{method:'PAY_ON_BOARD'})).status,200);
  return b.data;
}
try {
 for(const [index,width] of [390,820,1440].entries()) {
  const template=candidates[0];
  const departure=new Date(fixtureStart+index*3*86400000); departure.setUTCSeconds(0,0);
  const created=await call('POST','/operator/trips',token,{operatorRouteId:template.route.operatorRouteId,busId:template.bus.id,departureTime:departure.toISOString()}); assert.equal(created.status,201,JSON.stringify(created));
  const trip=(await call('GET',`/operator/trips/${created.data.id}`,token)).data;
  const otherBus=listed.find(t=>t.bus.id!==template.bus.id).bus.id;
  const conflict=await call('POST','/operator/trips',token,{operatorRouteId:template.route.operatorRouteId,busId:otherBus,departureTime:departure.toISOString()}); assert.equal(conflict.status,201,JSON.stringify(conflict));
  const overlap=conflict.data;
  const pickup=trip.stops.filter(s=>s.allowPickup); const start=pickup[0], intermediate=pickup[1];
  const seats=(await call('GET',`/trips/${trip.id}/seats?pickupLocationId=${start.locationId}&dropoffLocationId=${trip.stops.at(-1).locationId}`,null)).data.seats.filter(s=>s.available);
  assert.ok(seats.length>=4);
  const paid=await prepareBooking(trip,start,seats[0],true);
  const unpaid=await prepareBooking(trip,intermediate,seats[1],false);
  const absent=await prepareBooking(trip,start,seats[2],true);
  const unpaidAbsent=await prepareBooking(trip,start,seats[3],false);
  const context=await browser.newContext({viewport:{width,height:1000}}); const page=await context.newPage(); lastPage=page; const errors=[]; page.on('pageerror',e=>errors.push(e.message));
  await page.goto(origin+'/login'); await page.getByLabel('Email',{exact:true}).fill('operator.admin@anphu-demo.example'); await page.getByLabel('Mật khẩu',{exact:true}).fill('DemoOperator!2026'); await page.getByRole('button',{name:'Đăng nhập',exact:true}).click(); await page.waitForURL(url=>url.pathname!='/login');
  await page.goto(origin+'/operator/employees'); await page.getByRole('button',{name:'Thêm nhân sự',exact:true}).click();
  const code='BROWSER-'+width+'-'+Date.now();
  await page.getByLabel('Mã nhân sự',{exact:true}).fill(code); await page.getByLabel('Họ tên',{exact:true}).fill('Driver '+width); await page.getByLabel('Điện thoại',{exact:true}).fill('0901234567'); await page.getByLabel('Tài xế',{exact:true}).check();
  await page.getByLabel('Số giấy phép',{exact:true}).fill('TEST-'+width); await page.getByLabel('Hạng giấy phép',{exact:true}).fill('DEMO'); await page.getByLabel('Ngày hết hạn giấy phép',{exact:true}).fill('2099-12-31');
  const employee=await mutation(page,'POST','/operator/employees',()=>page.getByRole('button',{name:'Lưu nhân sự',exact:true}).click(),201);
  await page.getByRole('button',{name:'Chỉnh sửa '+code,exact:true}).click(); await page.getByLabel('Họ tên',{exact:true}).fill('Edited Driver '+width);
  await mutation(page,'PATCH',`/operator/employees/${employee.id}`,()=>page.getByRole('button',{name:'Lưu nhân sự',exact:true}).click()); await screenshot(page,width+'-employees');
  await page.goto(origin+`/operator/trips/${trip.id}`); await lifecycle(page,trip.id,'Bắt đầu đón khách',409);
  await page.getByLabel('Nhân sự',{exact:true}).selectOption(String(employee.id));
  await mutation(page,'PUT',`/operator/trips/${trip.id}/crew`,()=>page.getByRole('button',{name:'Phân công',exact:true}).click()); await screenshot(page,width+'-crew');
  assert.equal(await page.getByText('Chưa sẵn sàng đón khách. Kiểm tra xe và phân công ít nhất một tài xế hợp lệ.',{exact:true}).count(),0);
  await page.goto(origin+`/operator/trips/${overlap.id}`); await page.getByLabel('Nhân sự',{exact:true}).selectOption(String(employee.id));
  await mutation(page,'PUT',`/operator/trips/${overlap.id}/crew`,()=>page.getByRole('button',{name:'Phân công',exact:true}).click(),409);
  await page.goto(origin+`/operator/trips/${trip.id}`); await lifecycle(page,trip.id,'Bắt đầu đón khách');
  await page.goto(origin+`/operator/trips/${trip.id}/passengers`);
  const row=booking=>page.locator('article.card').filter({has:page.getByRole('link',{name:booking.bookingCode,exact:true})});
  await row(unpaid).getByText('Chưa thu tiền',{exact:true}).waitFor(); assert.ok(await row(unpaid).getByRole('button',{name:'Check-in',exact:true}).isDisabled());
  await row(paid).getByRole('button',{name:'Check-in',exact:true}).click(); await row(paid).getByText('Đã check-in',{exact:true}).waitFor(); await row(paid).getByRole('button',{name:'Lên xe',exact:true}).click(); await row(paid).getByText('Đã lên xe',{exact:true}).waitFor();
  // Closing with one unresolved paid passenger is rejected, never silently marking no-show.
  await page.getByRole('button',{name:`Đóng điểm đón ${start.locationName}`,exact:true}).click();
  await mutation(page,'POST',`/operator/trips/${trip.id}/stops/${start.id}/close-pickup`,()=>page.getByRole('button',{name:'Xác nhận đóng điểm đón',exact:true}).click(),409);
  await row(absent).getByRole('button',{name:'Vắng mặt',exact:true}).click();
  await page.getByRole('region',{name:'Xác nhận hành khách'}).getByRole('button',{name:'Xác nhận',exact:true}).click(); await row(absent).locator('strong').filter({hasText:'Vắng mặt'}).waitFor();
  assert.ok(await row(unpaidAbsent).getByRole('button',{name:'Check-in',exact:true}).isDisabled());
  // Click waits for the previous mutation's query refresh to finish and enable actions.
  await row(unpaidAbsent).getByRole('button',{name:'Vắng mặt',exact:true}).click();
  assert.ok(await row(unpaidAbsent).getByRole('button',{name:'Vắng mặt',exact:true}).isEnabled());
  await mutation(page,'POST',`/operator/trips/${trip.id}/booking-items/${(await call('GET',`/operator/trips/${trip.id}/attendance`,token)).data.find(p=>p.bookingId===unpaidAbsent.bookingId).bookingItemId}/no-show`,()=>page.getByRole('region',{name:'Xác nhận hành khách'}).getByRole('button',{name:'Xác nhận',exact:true}).click());
  await row(unpaidAbsent).locator('strong').filter({hasText:'Vắng mặt'}).waitFor();
  assert.equal(await row(unpaidAbsent).getByRole('button',{name:'Ghi nhận thu tiền mô phỏng',exact:true}).count(),0);
  const absentState=(await call('GET',`/operator/trips/${trip.id}/attendance`,token)).data.find(p=>p.bookingId===unpaidAbsent.bookingId);
  assert.equal(absentState.ticketId,null); assert.equal(absentState.paymentStatus,'PENDING'); assert.equal(absentState.paymentBlocked,true);
  const deniedPayment=await call('POST',`/operator/bookings/${unpaidAbsent.bookingId}/payments`,token,{method:'PAY_ON_BOARD'});
  assert.equal(deniedPayment.status,409); assert.equal(deniedPayment.code,'BOOKING_ATTENDANCE_TERMINAL');
  await close(page,trip.id,start); await screenshot(page,width+'-origin-resolved');
  await lifecycle(page,trip.id,'Khởi hành'); await lifecycle(page,trip.id,'Hoàn thành chuyến',409);
  // Existing M16A collection is reused after origin departure at the intermediate pickup.
  await row(unpaid).getByRole('button',{name:'Ghi nhận thu tiền mô phỏng',exact:true}).click();
  assert.doesNotMatch(await page.getByRole('region',{name:'Xác nhận hành khách'}).innerText(),/NaN|undefined/);
  await page.getByRole('region',{name:'Xác nhận hành khách'}).getByRole('button',{name:'Xác nhận',exact:true}).click();
  await row(unpaid).getByRole('button',{name:'Check-in',exact:true}).click(); await row(unpaid).getByText('Đã check-in',{exact:true}).waitFor(); await row(unpaid).getByRole('button',{name:'Lên xe',exact:true}).click(); await row(unpaid).getByText('Đã lên xe',{exact:true}).waitFor();
  await screenshot(page,width+'-intermediate-boarded'); for(const stop of pickup.slice(1)) await close(page,trip.id,stop);
  await lifecycle(page,trip.id,'Hoàn thành chuyến'); await screenshot(page,width+'-completed');
  const attendance=await call('GET',`/operator/trips/${trip.id}/attendance`,token); assert.deepEqual(attendance.data.map(p=>p.boardingStatus).sort(),['BOARDED','BOARDED','NO_SHOW','NO_SHOW']);
  const history=await call('GET',`/operator/trips/${trip.id}/history`,token); assert.equal(history.data.filter(e=>e.action==='BOARD').length,2); assert.equal(history.data.filter(e=>e.action==='NO_SHOW').length,2); assert.equal(history.data.filter(e=>e.action==='NO_SHOW'&&e.entity_type==='BOOKING_ITEM').length,1);
  assert.deepEqual(errors,[]); results.push({width,tripId:trip.id,employee:true,crew:true,overlapRejected:true,missingDriverRejected:true,unpaidBlocked:true,intermediateCollectionAndBoarding:true,noShowExplicit:true,completionGuard:true,overflow:false,pageErrors:errors}); await context.close();
 }
} catch(error) { if(lastPage && !lastPage.isClosed()) { console.log((await lastPage.locator('body').innerText()).slice(0,6000)); await screenshot(lastPage,'failure'); } throw error; } finally { await browser.close(); writeFileSync(new URL('results.json',directory),JSON.stringify(results,null,2)); }
console.log(JSON.stringify(results,null,2));
