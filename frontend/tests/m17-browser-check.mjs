// Real backend acceptance. Only newly created test reservations get a past deadline.
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
const require=createRequire(import.meta.url);
const { chromium }=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const origin=process.env.BUSGO_BROWSER_ORIGIN || 'http://127.0.0.1:5178';
const directory=new URL(process.env.BUSGO_BROWSER_OUTPUT || '../../.tools/m17-browser/',import.meta.url);
mkdirSync(directory,{recursive:true});
async function call(method,path,token,body) {
  const response=await fetch(origin+'/api/v1'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{})});
  return {status:response.status,...await response.json()};
}
async function login(email,password) {
  const result=await call('POST','/auth/login',null,{email,password}); assert.equal(result.status,200,JSON.stringify(result)); return result.data.accessToken;
}
const adminEmail='operator.admin@anphu-demo.example',adminPassword='DemoOperator!2026';
const admin=await login(adminEmail,adminPassword);
const listed=(await call('GET','/operator/trips?size=100',admin)).data;
const template=(await call('GET',`/operator/trips/${listed[0].id}`,admin)).data;
const fixtureStart=Math.max(Date.now(),...listed.map(t=>Date.parse(t.estimatedArrivalTime)))+7*86400000;
const browser=await chromium.launch({headless:true,channel:'msedge'}); const results=[]; let lastPage;
async function uiLogin(page,email,password) {
  await page.goto(origin+'/login'); await page.getByLabel('Email',{exact:true}).fill(email); await page.getByLabel('Mật khẩu',{exact:true}).fill(password);
  await page.getByRole('button',{name:'Đăng nhập',exact:true}).click(); await page.waitForURL(url=>url.pathname!='/login');
}
async function screenshot(page,name) {
  await page.evaluate(()=>window.scrollTo(0,0));
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'No horizontal overflow');
  await page.screenshot({path:new URL(name+'.png',directory).pathname.replace(/^\/([A-Z]:)/,'$1'),fullPage:true});
}
async function cancelUi(page,b,operator,paid) {
  await page.goto(origin+`${operator?'/operator/bookings/':'/my-bookings/'}${b.bookingId}`);
  const action=operator?'Hủy đặt vé':paid?'Huỷ và hoàn tiền mô phỏng':'Huỷ đặt chỗ';
  await page.getByRole('button',{name:action,exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'Xác nhận hủy đặt vé'}); await dialog.waitFor();
  assert.ok((await dialog.innerText()).includes(b.bookingCode));
  await screenshot(page,`${page.viewportSize().width}-${operator?'phone':'web'}-${paid?'paid':'unpaid'}-review`);
  const response=page.waitForResponse(r=>r.url().endsWith(`/bookings/${b.bookingId}/cancel`) && r.request().method()==='POST');
  await dialog.getByRole('button',{name:'Xác nhận hủy',exact:true}).click();
  assert.equal((await response).status(),200); await dialog.waitFor({state:'hidden'});
  await page.locator('.cancellation-section').getByRole('status').waitFor();
  await screenshot(page,`${page.viewportSize().width}-${operator?'phone':'web'}-${paid?'paid':'unpaid'}-cancelled`);
  if(paid) await page.locator('.cancellation-section').getByText(/Hoàn tiền mô phỏng/).waitFor();
}
async function assertAvailable(trip,start,end,seatId) {
  const map=(await call('GET',`/trips/${trip.id}/seats?pickupLocationId=${start.locationId}&dropoffLocationId=${end.locationId}`)).data;
  assert.equal(map.seats.find(s=>s.tripSeatId===seatId).available,true);
}
function timeoutBooking(id) {
  assert.match(String(id),/^\d+$/);
  execFileSync(process.env.MYSQL_CLIENT || 'C:/Program Files/MySQL/MySQL Server 9.2/bin/mysql.exe',
    ['--no-defaults','-h','127.0.0.1','-P',process.env.M17_MYSQL_PORT || '13317','-u','root',process.env.M17_BROWSER_SCHEMA || 'busgo_m17_browser','-e',`UPDATE bookings SET payment_due_at=created_at-INTERVAL 1 SECOND WHERE id=${id} AND status='PENDING' AND payment_method<>'PAY_ON_BOARD';`],{env:{...process.env,MYSQL_PWD:''}});
}
try {
 for(const [index,width] of [390,820,1440].entries()) {
  const created=await call('POST','/operator/trips',admin,{operatorRouteId:template.route.operatorRouteId,busId:template.bus.id,departureTime:new Date(fixtureStart+index*3*86400000).toISOString()}); assert.equal(created.status,201,JSON.stringify(created));
  const trip=(await call('GET',`/operator/trips/${created.data.id}`,admin)).data;
  const start=trip.stops.find(s=>s.allowPickup),end=trip.stops.at(-1);
  const seats=(await call('GET',`/trips/${trip.id}/seats?pickupLocationId=${start.locationId}&dropoffLocationId=${end.locationId}`)).data.seats.filter(s=>s.available);
  const context=await browser.newContext({viewport:{width,height:1000}});const page=await context.newPage();lastPage=page;const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const email=`m17-${width}-${Date.now()}@example.test`,password='BrowserCustomer!2026';
  assert.equal((await call('POST','/auth/register',null,{fullName:'M17 Customer',email,phone:'090'+String(Date.now()).slice(-7),password})).status,201);
  const customer=await login(email,password); await uiLogin(page,email,password);
  for(const [n,paid] of [false,true].entries()) {
    const hold=await call('POST','/seat-holds',customer,{tripId:trip.id,pickupLocationId:start.locationId,dropoffLocationId:end.locationId,tripSeatIds:[seats[n].tripSeatId]});assert.equal(hold.status,201,JSON.stringify(hold));
    const made=await call('POST','/bookings',customer,{holdToken:hold.data.holdToken,contactName:'M17 Customer',contactPhone:'0901234567',contactEmail:email});assert.equal(made.status,201);const b=made.data;
    if(paid) {
      await page.goto(origin+`/payment?bookingId=${b.bookingId}`);await page.getByRole('button',{name:'Xác nhận thanh toán giả lập',exact:true}).click();
      await page.waitForURL('**/booking-success?bookingId=*');await page.locator('.ticket-stub svg').waitFor();
    }
    await cancelUi(page,b,false,paid); await assertAvailable(trip,start,end,seats[n].tripSeatId);
    const recovery=(await call('GET',`/bookings/${b.bookingId}/recovery`,customer)).data;
    assert.equal(recovery.status,'CANCELLED');assert.equal(recovery.refunds.length,paid?1:0);assert.equal(recovery.tickets.length,paid?1:0);
    const detail=(await call('GET',`/operator/bookings/${b.bookingId}`,admin)).data;assert.equal(detail.payments.length,paid?1:0);
    if(paid) {await page.goto(origin+`/booking-success?bookingId=${b.bookingId}`);await page.getByText('Đã vô hiệu (VOID) · Không dùng để lên xe',{exact:true}).waitFor();assert.equal(await page.locator('.ticket-stub svg').count(),0);await screenshot(page,`${width}-void-web-ticket`);}
  }
  await uiLogin(page,adminEmail,adminPassword);
  for(const [n,method] of ['PAY_ON_BOARD','QR_TRANSFER'].entries()) {
    const made=await call('POST','/operator/bookings',admin,{tripId:trip.id,pickupLocationId:start.locationId,dropoffLocationId:end.locationId,tripSeatIds:[seats[n].tripSeatId],contactName:'M17 Caller',contactPhone:'0901234567',paymentMethod:method});assert.equal(made.status,201);const b=made.data;let publicToken;
    if(method==='QR_TRANSFER') {
      const link=await call('POST',`/operator/bookings/${b.bookingId}/payment-link`,admin);publicToken=link.data.path.slice('/pay/'.length);
      const anonymous=await browser.newPage({viewport:{width,height:1000}});await anonymous.goto(origin+link.data.path);await anonymous.getByRole('button',{name:'Xác nhận mô phỏng thanh toán',exact:true}).click();await anonymous.getByText('Đã thanh toán. Liên hệ nhà xe để nhận vé điện tử.',{exact:true}).waitFor();await anonymous.close();
    }
    await cancelUi(page,b,true,method==='QR_TRANSFER');await assertAvailable(trip,start,end,seats[n].tripSeatId);
    const detail=(await call('GET',`/operator/bookings/${b.bookingId}`,admin)).data;
    assert.equal(detail.payments.length,method==='QR_TRANSFER'?1:0);assert.equal(await page.locator('.dossier-seat svg').count(),0);
    if(publicToken) {
      assert.equal((await call('POST',`/public/payments/${publicToken}/mock-confirm`)).status,409);
      const anonymous=await browser.newPage({viewport:{width,height:1000}});await anonymous.goto(origin+'/pay/'+publicToken);await anonymous.getByText('Đặt vé đã hủy, không còn thanh toán được.',{exact:true}).waitFor();assert.equal(await anonymous.getByRole('button').count(),0);await screenshot(anonymous,`${width}-cancelled-public-link`);await anonymous.close();
    }
  }
  // Live scheduled expiry after moving only this newly created test booking's deadline.
  const timeout=await call('POST','/operator/bookings',admin,{tripId:trip.id,pickupLocationId:start.locationId,dropoffLocationId:end.locationId,tripSeatIds:[seats[0].tripSeatId],contactName:'M17 Timeout',contactPhone:'0901234567',paymentMethod:'QR_TRANSFER'});assert.equal(timeout.status,201);
  const timeoutId=timeout.data.bookingId;const link=await call('POST',`/operator/bookings/${timeoutId}/payment-link`,admin);timeoutBooking(timeoutId);
  let recovery;for(let n=0;n<30;n++){recovery=(await call('GET',`/operator/bookings/${timeoutId}/recovery`,admin)).data;if(recovery.status==='CANCELLED')break;await new Promise(resolve=>setTimeout(resolve,500));}
  assert.equal(recovery.reasonCode,'PAYMENT_TIMEOUT');assert.equal(recovery.refunds.length,0);assert.equal(recovery.tickets.length,0);await assertAvailable(trip,start,end,seats[0].tripSeatId);
  assert.equal((await call('POST',`/public/payments/${link.data.path.slice('/pay/'.length)}/mock-confirm`)).status,409);
  await page.goto(origin+`/operator/bookings/${timeoutId}`);await page.getByText('Quá hạn thanh toán',{exact:false}).first().waitFor();await screenshot(page,`${width}-timeout`);
  // A paid passenger checked in through M16B cannot cancel.
  const attended=await call('POST','/operator/bookings',admin,{tripId:trip.id,pickupLocationId:start.locationId,dropoffLocationId:end.locationId,tripSeatIds:[seats[0].tripSeatId],contactName:'M17 Attendance',contactPhone:'0901234567',paymentMethod:'PAY_ON_BOARD'});assert.equal(attended.status,201);
  assert.equal((await call('POST',`/operator/bookings/${attended.data.bookingId}/payments`,admin,{method:'PAY_ON_BOARD'})).status,200);
  const employee=await call('POST','/operator/employees',admin,{employeeCode:`M17-${width}-${Date.now()}`,fullName:'M17 Driver',phone:'0901234567',status:'ACTIVE',capabilities:['DRIVER'],licenceNumber:'DEMO',licenceClass:'DEMO',licenceExpiryDate:'2099-12-31'});assert.equal(employee.status,201);
  assert.equal((await call('PUT',`/operator/trips/${trip.id}/crew`,admin,{assignments:[{employeeId:employee.data.id,duty:'DRIVER'}]})).status,200);
  assert.equal((await call('PATCH',`/operator/trips/${trip.id}/status`,admin,{status:'BOARDING'})).status,200);
  const detail=(await call('GET',`/operator/bookings/${attended.data.bookingId}`,admin)).data;const ticket=detail.items[0].ticket.id;
  assert.equal((await call('POST',`/operator/trips/${trip.id}/tickets/${ticket}/check-in`,admin,{stopId:start.id})).status,200);
  await page.goto(origin+`/operator/bookings/${attended.data.bookingId}`);await page.locator('.cancellation-section').getByText(/Có khách đã điểm danh/).waitFor();assert.equal(await page.locator('.cancellation-section').getByRole('button').count(),0);await screenshot(page,`${width}-attendance-boundary`);
  assert.equal((await call('POST',`/operator/bookings/${attended.data.bookingId}/cancel`,admin,{})).status,409);
  assert.deepEqual(errors,[]);results.push({width,webUnpaid:true,webPaidRefundAndVoid:true,phonePayOnBoard:true,phoneQrPublicPaid:true,expiry:true,attendanceBoundary:true,overflow:false,pageErrors:errors});await context.close();
 }
} catch(error) {if(lastPage&&!lastPage.isClosed())await screenshot(lastPage,'failure');throw error;}
finally {await browser.close();writeFileSync(new URL('results.json',directory),JSON.stringify(results,null,2));}
console.log(JSON.stringify(results,null,2));
