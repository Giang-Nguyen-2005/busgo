// Real-browser acceptance against a dedicated local dev,demo M27A schema.
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const origin=process.env.BUSGO_BROWSER_ORIGIN || 'http://127.0.0.1:5187';
const out=process.env.BUSGO_BROWSER_OUTPUT || '.tools/m27a-browser';mkdirSync(out,{recursive:true});
const evidence=[];const stamp=Date.now();
async function api(path,token,body,method=body===undefined?'GET':'POST') {
  const r=await fetch(origin+'/api/v1'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});
  const j=await r.json();assert.ok(r.ok,`${path}: ${r.status} ${JSON.stringify(j)}`);return j.data;
}
async function login(email,password) {return (await api('/auth/login',null,{email,password})).accessToken;}
async function uiLogin(page,email,password) {
  await page.goto(origin+'/login');await page.getByLabel('Email',{exact:true}).fill(email);await page.getByLabel('Mật khẩu',{exact:true}).fill(password);
  await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await page.waitForURL(u=>u.pathname!='/login');
}
const admin=await login('operator.admin@anphu-demo.example','DemoOperator!2026');
const system=await login('system.m27a@example.test','M27Acceptance!2026');
const own=await api('/operator/finance/overview?from=2026-10-01&to=2026-10-31',admin);
const operatorId=own.operatorId;
const browser=await chromium.launch({channel:'msedge',headless:true});
const errors=[];
try {
  const systemContext=await browser.newContext({viewport:{width:1440,height:1000},timezoneId:'America/New_York'});
  const page=await systemContext.newPage();page.on('pageerror',e=>errors.push(e.message));
  await uiLogin(page,'system.m27a@example.test','M27Acceptance!2026');await page.goto(origin+'/admin/finance');
  await page.getByRole('heading',{name:'Tài chính / Doanh thu & Hoa hồng'}).waitFor();
  await page.getByLabel('Nhà xe',{exact:true}).selectOption(String(operatorId));
  await page.getByRole('button',{name:'Chính sách hoa hồng',exact:true}).click();
  // Future minute in Vietnam: enough time to submit and explicitly activate, no retroactive SQL changes.
  const effective=new Date(Math.ceil((Date.now()+45000)/60000)*60000);
  const vietnamLocal=new Date(effective.getTime()+7*3600000).toISOString().slice(0,16);
  await page.getByLabel('Tỷ lệ (%)',{exact:true}).fill('5');
  await page.getByLabel('Hiệu lực từ (giờ Việt Nam)',{exact:true}).fill(vietnamLocal);
  await page.getByLabel('Lý do',{exact:true}).fill('M27A browser acceptance '+stamp);
  const created=page.waitForResponse(r=>r.url().endsWith('/finance/policies')&&r.request().method()==='POST');
  await page.getByRole('button',{name:'Lưu bản nháp',exact:true}).click();const policyResponse=await created;assert.equal(policyResponse.status(),200);
  const policy=(await policyResponse.json()).data;
  const policyCard=page.locator('article').filter({has:page.getByRole('heading',{name:new RegExp('Chính sách #'+policy.id+' ·')})});
  await policyCard.getByRole('button',{name:'Kích hoạt',exact:true}).click();
  await page.getByRole('button',{name:'Xác nhận kích hoạt',exact:true}).click();
  await policyCard.getByText(/Đã kích hoạt/).waitFor();evidence.push('SYSTEM_ADMIN created and activated policy through UI');
  const operators=await api('/admin/finance/operators',system);assert.ok(operators.data.length>=2);
  const email=`m27a-${stamp}@example.test`,password='M27Customer!2026';
  await api('/auth/register',null,{fullName:'M27A acceptance customer',email,phone:'090'+String(stamp).slice(-7),password});
  const customer=await login(email,password);
  const trips=await api('/operator/trips?size=100',admin);const template=await api('/operator/trips/'+trips[0].id,admin);
  const depart=Math.max(Date.now(),...trips.map(t=>Date.parse(t.estimatedArrivalTime)))+7*86400000;
  const made=await api('/operator/trips',admin,{operatorRouteId:template.route.operatorRouteId,busId:template.bus.id,departureTime:new Date(depart).toISOString()});
  const trip=await api('/operator/trips/'+made.id,admin),pickup=trip.stops.find(s=>s.allowPickup),dropoff=trip.stops.filter(s=>s.allowDropoff).at(-1);
  const seats=(await api(`/trips/${trip.id}/seats?pickupLocationId=${pickup.locationId}&dropoffLocationId=${dropoff.locationId}`)).seats;
  const voucher=await api('/operator/vouchers',admin,{code:'M27-'+stamp,name:'Acceptance discount',discountType:'PERCENT',discountValue:20,minimumSpend:0,startsAt:new Date(Date.now()-86400000).toISOString(),endsAt:new Date(Date.now()+86400000*20).toISOString()});
  await api('/operator/vouchers/'+voucher.id+'/activate',admin,{});
  const hold=await api('/seat-holds',customer,{tripId:trip.id,pickupLocationId:pickup.locationId,dropoffLocationId:dropoff.locationId,tripSeatIds:[seats[0].tripSeatId]});
  const booking=await api('/bookings',customer,{holdToken:hold.holdToken,contactName:'M27A acceptance customer',contactPhone:'0901234567',contactEmail:email,voucherCode:'M27-'+stamp});
  while(Date.now()<effective.getTime()+1000) await new Promise(r=>setTimeout(r,Math.min(1000,effective.getTime()+1000-Date.now())));
  const customerContext=await browser.newContext();const cp=await customerContext.newPage();await uiLogin(cp,email,password);
  await cp.goto(origin+'/payment?bookingId='+booking.bookingId);
  const paid=cp.waitForResponse(r=>r.url().endsWith('/payments/mock-confirm')&&r.request().method()==='POST');
  await cp.getByRole('button',{name:'Xác nhận thanh toán mô phỏng',exact:true}).click();assert.equal((await paid).status(),200);
  const day=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date());
  const filter=`?from=${day}&to=${day}&operatorId=${operatorId}`;
  const ledger=await api('/admin/finance/ledger'+filter+'&bookingId='+booking.bookingId,system);
  assert.equal(ledger.data.length,1);const collection=ledger.data[0];assert.equal(collection.channel,'PLATFORM_MEDIATED_SIMULATED');
  assert.equal(collection.amount,booking.totalAmount);assert.equal(collection.commission,Math.round(collection.amount*5)/100);assert.ok(collection.discountContext>0);
  evidence.push('Discounted WEB booking paid via browser; net basis and 5% snapshot verified');
  await api('/bookings/'+booking.bookingId+'/cancel',customer,{});
  const after=await api('/admin/finance/ledger'+filter+'&bookingId='+booking.bookingId,system);
  assert.equal(after.data.length,2);assert.equal(after.data.reduce((a,e)=>a+e.commission,0),0);evidence.push('Full refund retained original collection and reversed commission exactly');
  const phone=await api('/operator/bookings',admin,{tripId:trip.id,pickupLocationId:pickup.locationId,dropoffLocationId:dropoff.locationId,tripSeatIds:[seats[1].tripSeatId],contactName:'Phone acceptance',contactPhone:'0901234567',paymentMethod:'PAY_ON_BOARD'});
  await api('/operator/bookings/'+phone.bookingId+'/payments',admin,{method:'PAY_ON_BOARD'});
  const phoneEvents=await api('/admin/finance/ledger'+filter+'&bookingId='+phone.bookingId,system);
  assert.equal(phoneEvents.data[0].operatorPayable,0);assert.equal(phoneEvents.data[0].platformReceivable,phoneEvents.data[0].commission);
  evidence.push('PHONE recorded collection has separate platform receivable');
  await page.getByRole('button',{name:'Đối soát',exact:true}).click();
  const runResponse=page.waitForResponse(r=>r.url().endsWith('/finance/reconciliations')&&r.request().method()==='POST');
  await page.getByRole('button',{name:'Kiểm tra kỳ đã chọn',exact:true}).click();const clean=(await (await runResponse).json()).data;assert.equal(clean.status,'CLEAN');
  const runCard=page.locator('article').filter({has:page.getByRole('heading',{name:new RegExp('Đối soát #'+clean.id+' ·')})});
  await runCard.getByRole('button',{name:'Phát hành bảng kê nội bộ',exact:true}).click();
  await page.getByRole('button',{name:'Bảng kê',exact:true}).click();await page.locator('summary').filter({hasText:'Bảng kê nội bộ'}).first().click();
  const statements=await api('/admin/finance/statements?operatorId='+operatorId,system);
  assert.equal(statements.data[0].snapshot.totals.netCommission,phoneEvents.data[0].commission);
  assert.equal(statements.data[0].snapshot.reconciliationStatus,'CLEAN');evidence.push('UI reconciliation and immutable internal statement match event totals');
  // A different operator has no policy: collection remains visible and reconciliation is exceptional.
  const second=await login('operator.admin@minhthanh-demo.example','DemoSecondary!2026');
  const otherOverview=await api('/operator/finance/overview?from='+day+'&to='+day,second);
  const otherTrips=await api('/operator/trips?size=100',second);const otherTrip=await api('/operator/trips/'+otherTrips.find(t=>t.status==='SCHEDULED').id,second);
  const op=otherTrip.stops.find(s=>s.allowPickup),od=otherTrip.stops.filter(s=>s.allowDropoff).at(-1);
  const os=(await api(`/trips/${otherTrip.id}/seats?pickupLocationId=${op.locationId}&dropoffLocationId=${od.locationId}`)).seats.find(s=>s.available);
  const unpriced=await api('/operator/bookings',second,{tripId:otherTrip.id,pickupLocationId:op.locationId,dropoffLocationId:od.locationId,tripSeatIds:[os.tripSeatId],contactName:'Unpriced acceptance',contactPhone:'0901234567',paymentMethod:'PAY_ON_BOARD'});
  await api('/operator/bookings/'+unpriced.bookingId+'/payments',second,{method:'PAY_ON_BOARD'});
  const exceptional=await api('/admin/finance/reconciliations',system,{operatorId:otherOverview.operatorId,from:day,to:day,projectMissing:false});assert.equal(exceptional.status,'EXCEPTIONS');
  const exceptionList=await api('/admin/finance/reconciliations/'+exceptional.id+'/exceptions',system);assert.ok(exceptionList.data.some(e=>e.reason==='COMMISSION_UNPRICED'));
  const forged=await fetch(origin+'/api/v1/operator/finance/overview'+filter,{headers:{Authorization:'Bearer '+second}});assert.equal(forged.status,403);
  evidence.push('Unpriced source appears as reconciliation exception; foreign operator request denied');
  for(const width of [1440,390]) {
    await page.setViewportSize({width,height:1000});
    for(const tab of ['Giao dịch','Chính sách hoa hồng','Đối soát','Bảng kê','So sánh nhà xe']) {
      await page.getByRole('button',{name:tab,exact:true}).click();await page.waitForLoadState('networkidle');
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,`admin overflow ${width} ${tab}`);
    }
    await page.screenshot({path:`${out}/platform-${width}.png`,fullPage:true});
    const ctx=await browser.newContext({viewport:{width,height:1000}});const opPage=await ctx.newPage();await uiLogin(opPage,'operator.admin@anphu-demo.example','DemoOperator!2026');
    await opPage.goto(origin+'/operator/finance');await opPage.getByRole('button',{name:'Chính sách hoa hồng',exact:true}).click();
    await opPage.getByText('M27A browser acceptance '+stamp,{exact:true}).waitFor();assert.equal(await opPage.getByRole('button',{name:'Lưu bản nháp'}).count(),0);
    for(const tab of ['Giao dịch','Đối soát','Bảng kê']) {await opPage.getByRole('button',{name:tab,exact:true}).click();await opPage.waitForLoadState('networkidle');assert.equal(await opPage.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,`operator overflow ${width} ${tab}`);}
    await opPage.screenshot({path:`${out}/operator-${width}.png`,fullPage:true});await ctx.close();
  }
  evidence.push('Platform and operator tabs work at 1440px and 390px without document overflow; operator policy is read-only');
  const staffContext=await browser.newContext();const sp=await staffContext.newPage();await uiLogin(sp,'operator.staff@anphu-demo.example','DemoStaff!2026');await sp.goto(origin+'/operator/finance');await sp.waitForLoadState('networkidle');
  assert.equal(await sp.getByRole('heading',{name:'Tài chính / Doanh thu & Hoa hồng'}).count(),0);evidence.push('Staff direct finance URL denied');
  assert.deepEqual(errors,[]);writeFileSync(out+'/results.json',JSON.stringify({passed:true,evidence,policyId:policy.id,bookingId:booking.bookingId,runId:clean.id},null,2));
  console.log(JSON.stringify({passed:true,evidence},null,2));
} finally {await browser.close();}
