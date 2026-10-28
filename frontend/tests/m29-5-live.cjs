// Requires the dedicated M29.5 local server; never use against a shared database.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/Giang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const origin='http://127.0.0.1:5195',base='http://127.0.0.1:8099/api/v1';
const out=path.resolve('.tools/m29-browser/m29-5');
const result={mode:'LIVE React/Vite -> Spring Boot -> dedicated MySQL 3329; no API interception',checks:[],pages:[],errors:[],api:[]};
async function api(url,body,token,expected=200){const r=await fetch(base+url,{method:body?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body?JSON.stringify(body):undefined});result.api.push({url,status:r.status});assert.equal(r.status,expected,url);const j=await r.json();return j.data??j;}
async function ready(p){await p.waitForLoadState('networkidle');}
async function login(p,email,password){await p.goto(origin+'/login');await p.getByLabel('Email',{exact:true}).fill(email);await p.getByLabel('Mật khẩu',{exact:true}).fill(password);await p.getByRole('button',{name:'Đăng nhập',exact:true}).click();await p.waitForURL(u=>u.pathname!='/login');await ready(p);}
async function shot(p,name,width){await p.setViewportSize({width,height:900});await ready(p);assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),name+' overflow');await p.screenshot({path:path.join(out,name+'-'+width+'.png'),fullPage:true});result.pages.push({name,width,url:p.url()});}
(async()=>{
const browser=await chromium.launch({channel:'msedge',headless:true});const context=await browser.newContext({viewport:{width:1440,height:900},locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh'});const p=await context.newPage();p.on('pageerror',e=>result.errors.push(e.message));
try{
 const admin=await api('/auth/login',{email:'operator.admin@anphu-demo.example',password:'DemoOperator!2026'});
 const trips=await api('/operator/trips?size=100',null,admin.accessToken);const trip=trips.find(t=>t.status==='SCHEDULED'&&new Date(t.departureTime)>new Date());assert(trip,'future demo trip');
 const detail=await api('/operator/trips/'+trip.id,null,admin.accessToken);const pickup=detail.stops.find(s=>s.allowPickup),dropoff=detail.stops.filter(s=>s.allowDropoff).at(-1);
 const email='customer.m29.'+Date.now()+'@example.test',password='M29Customer!2026';
 await p.goto(origin+'/register');await p.getByLabel('Họ và tên',{exact:true}).fill('M29 Live Customer');await p.getByLabel('Email',{exact:true}).fill(email);await p.getByLabel('Số điện thoại',{exact:true}).fill('0900001129');await p.getByLabel('Mật khẩu',{exact:true}).fill(password);await p.getByLabel('Nhập lại mật khẩu',{exact:true}).fill(password);await p.getByRole('button',{name:'Đăng ký',exact:true}).click();await p.waitForURL('**/login?**');
 await login(p,email,password);result.checks.push('Live UI registration and login');
 await p.goto(origin+'/');await ready(p);await shot(p,'live-home',1440);
 const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(trip.departureTime));
 await p.getByRole('combobox',{name:'Điểm đón',exact:true}).fill(pickup.locationName);await p.getByRole('option').filter({hasText:pickup.locationName}).first().click();
 await p.getByRole('combobox',{name:'Điểm trả',exact:true}).fill(dropoff.locationName);await p.getByRole('option').filter({hasText:dropoff.locationName}).first().click();
 await p.getByLabel('Ngày đi',{exact:true}).fill(date);await p.getByRole('button',{name:'Tìm chuyến',exact:true}).click();await p.locator('.trip-card').first().waitFor();await shot(p,'live-search',390);result.checks.push('Live homepage autocomplete and search form -> search results');
 await p.goto(origin+`/trips/${trip.id}?pickupLocationId=${pickup.locationId}&dropoffLocationId=${dropoff.locationId}`);await ready(p);await p.locator('.seat:not(:disabled)').first().click();await p.getByRole('button',{name:'Tiếp tục',exact:true}).click();await p.waitForURL('**/booking');await ready(p);
 await p.getByLabel('Họ và tên',{exact:true}).fill('M29 Live Customer');await p.getByLabel('Số điện thoại',{exact:true}).fill('0900001129');await p.getByLabel('Email',{exact:true}).fill(email);await shot(p,'live-checkout',390);
 await p.getByRole('button',{name:'Tiếp tục đến thanh toán'}).click();await p.waitForURL('**/payment?bookingId=*');const bookingId=Number(new URL(p.url()).searchParams.get('bookingId'));await shot(p,'live-payment',390);
 await p.getByRole('button',{name:'Xác nhận thanh toán mô phỏng'}).click();await p.waitForURL('**/booking-success?bookingId=*');await p.locator('.ticket-stub svg').first().waitFor();await shot(p,'live-ticket',390);result.checks.push('Live seat selection -> hold -> booking -> mock payment -> valid QR ticket');
 await p.goto(origin+'/my-bookings');await ready(p);await shot(p,'live-my-bookings',1440);
 const customer=await api('/auth/login',{email,password});await api('/admin/dashboard',null,customer.accessToken,403);await api('/operator/trips',null,customer.accessToken,403);await api('/bookings',null,null,401);
 const ticket=await api('/support/tickets',{bookingId,category:'BOOKING_HELP',subject:'M29 live support',description:'M29 test case <b>literal</b>',requestKey:crypto.randomUUID()},customer.accessToken);
 await p.goto(origin+'/support/tickets/'+ticket.id);await ready(p);await p.getByText('M29 test case <b>literal</b>',{exact:true}).waitFor();assert.equal(await p.locator('.support-workspace b').count(),0);await shot(p,'live-support',390);result.checks.push('Live API creates owned support case; browser renders escaped message');
 result.bookingId=bookingId;result.tripId=trip.id;
 await context.close();
 for(const [role,email,password,routes] of [
 ['OPERATOR_ADMIN','operator.admin@anphu-demo.example','DemoOperator!2026',['/operator','/operator/dispatch?trip='+trip.id,'/operator/bookings','/operator/finance','/operator/support','/operator/buses','/operator/routes','/operator/staff','/operator/reports']],
 ['OPERATOR_STAFF','operator.staff@anphu-demo.example','DemoStaff!2026',['/operator','/operator/dispatch?trip='+trip.id,'/operator/bookings','/operator/support']],
 ['SYSTEM_ADMIN','system.m29@example.test','M29Acceptance!2026',['/admin','/admin/operator-applications','/admin/operators','/admin/finance','/admin/disputes','/admin/audit-events']]]){
  const c=await browser.newContext({viewport:{width:1440,height:900},locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh'}),page=await c.newPage();page.on('pageerror',e=>result.errors.push(e.message));await login(page,email,password);
  for(const url of routes){await page.setViewportSize({width:1440,height:900});await page.goto(origin+url);await ready(page);assert.equal(await page.getByRole('alert').count(),0,role+url+' alert: '+await page.locator('body').innerText());
   if(url.includes('dispatch')){await page.locator('.db-context-controls select').nth(0).selectOption('journey');await page.locator('.db-context-controls select').nth(1).selectOption(String(detail.stops[0].id));await page.locator('.db-context-controls select').nth(2).selectOption(String(detail.stops.at(-1).id));await page.locator('.db-seat').filter({hasText:'Đã đặt'}).first().click();await page.locator('.db-inspector').waitFor();}
   for(const width of [1440,390]){await page.setViewportSize({width,height:900});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),role+url+' overflow');}result.checks.push(role+' live '+url);if(url.includes('dispatch')||url.endsWith('finance')||url==='/admin')await shot(page,'live-'+role+'-'+url.split('?')[0].replaceAll('/','_'),390);}
  if(role==='OPERATOR_STAFF'){const auth=await api('/auth/login',{email,password});await api('/operator/finance/overview',null,auth.accessToken,403);await api('/operator/staff',null,auth.accessToken,403);await page.goto(origin+'/operator/finance');await ready(page);assert.equal(await page.locator('.finance-page').count(),0);result.checks.push('Staff forbidden finance/staff APIs and finance URL');}
  await c.close();
 }
 assert.deepEqual(result.errors,[]);result.status='PASS';
}catch(e){result.status='FAIL';result.failure=e.stack;for(const c of browser.contexts())for(const page of c.pages()){fs.writeFileSync(path.join(out,'live-failure.txt'),await page.locator('body').innerText());await page.screenshot({path:path.join(out,'live-failure.png'),fullPage:true});}process.exitCode=1;console.error(e);}finally{fs.writeFileSync(path.join(out,'live-results.json'),JSON.stringify(result,null,2));await browser.close();}console.log(JSON.stringify(result,null,2));
})();


