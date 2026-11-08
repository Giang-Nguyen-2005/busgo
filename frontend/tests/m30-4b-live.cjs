// Live acceptance only: no context.route / request interception. Dedicated test schema only.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
function loadPlaywright(env=process.env,load=require) {
 const moduleName=env.PLAYWRIGHT_MODULE||'playwright';
 try {
  const playwright=load(moduleName);
  if(typeof playwright.chromium?.launch!=='function')throw new Error('Missing chromium.launch');
  return playwright;
 }catch{
  throw new Error('Cannot load Playwright. Install playwright in the frontend test environment or set PLAYWRIGHT_MODULE to its module path; the module must export chromium.launch.');
 }
}
function verifyLiveEnvironment(args=process.argv.slice(2),env=process.env) {
 if(!args.includes('--allow-live-mutations'))throw new Error('Live mutations are disabled. --allow-live-mutations explicitly opts in to creating test accounts, campaigns, bookings and mock payments; it does not verify the target database.');
 const target=new URL(env.BUSGO_TEST_ORIGIN||'http://127.0.0.1:5190');
 if(target.protocol!=='http:'||!['127.0.0.1','localhost','[::1]'].includes(target.hostname)||target.username||target.password||target.pathname!=='/'||target.search||target.hash)
  throw new Error('Live acceptance requires a local HTTP origin without credentials, path, query or fragment.');
 // The existing health endpoint reports UP without checking datasource identity.
 // Neither a loopback URL, an environment variable nor a fixture account proves
 // which database the backend/proxy uses. There is currently no trusted verifier
 // in this repository, so opt-in alone must never unlock this mutating harness.
 throw new Error('Cannot independently verify the backend/proxy target database as an isolated local test environment. Live acceptance is blocked before browser launch or HTTP requests. A trusted environment verifier is required; health=UP and self-declared environment variables are insufficient. Use the non-mutating browser fixture harness instead.');
}
const origin=process.env.BUSGO_TEST_ORIGIN||'http://127.0.0.1:5190';
const phase=process.argv.includes('--baseline')?'m304a-baseline':'m304b-live';
const out=path.resolve(origin==='http://127.0.0.1:5196'?'.tools/m30-6/customer':'.tools/m30-4b/'+phase);
const report={mode:'Live acceptance; no API interception; target requires independent verification',checks:[],pageErrors:[],captures:[]};
const stamp=Date.now(),code='LIVE'+stamp;
async function api(p,method,url,body,token){const r=await p.request.fetch(origin+'/api/v1'+url,{method,data:body,headers:token?{Authorization:'Bearer '+token}:{}});const data=await r.json();assert(r.ok(),method+' '+url+' '+r.status()+' '+JSON.stringify(data));return data;}
async function capture(p,name){const metrics=await p.evaluate(()=>({w:innerWidth,d:document.documentElement.scrollWidth}));assert(metrics.d<=metrics.w,name+' overflow');await p.screenshot({path:path.join(out,name+'.png'),fullPage:true});report.captures.push(name);}
async function main(){
 const isolated=require('./helpers/m306-environment.cjs');
 if(origin===isolated.origin && process.argv.includes('--allow-live-mutations')) await isolated.verifyM306(origin);
 else verifyLiveEnvironment();
 const {chromium}=loadPlaywright();fs.mkdirSync(out,{recursive:true});const browser=await chromium.launch({channel:'msedge',headless:true});const context=await browser.newContext({viewport:{width:1440,height:900},locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh'});const p=await context.newPage();p.setDefaultTimeout(20000);p.on('pageerror',e=>report.pageErrors.push(e.message));try{
 const login=(await api(p,'POST','/auth/login',{email:'operator.admin@anphu-demo.example',password:'DemoOperator!2026'})).data;
 const owned=(await api(p,'GET','/operator/trips',undefined,login.accessToken)).data;
 const ids=new Set(owned.map(t=>t.id??t.tripId));const trips=(await api(p,'GET','/public/discovery/trips')).data;
 const trip=trips.find(t=>ids.has(t.tripId)&&t.availableSeats>=1);assert(trip,'Need seeded public trip belonging to test operator');
 const routes=(await api(p,'GET','/operator/routes',undefined,login.accessToken)).data;
 const route=routes.find(r=>r.route.id===trip.route.id);assert(route);
 const campaign=(await api(p,'POST','/operator/vouchers',{code,name:'Ưu đãi live test '+stamp,description:'Chỉ dữ liệu nghiệm thu trên schema test độc lập',discountType:'PERCENT',discountValue:10,maxDiscount:100000,minimumSpend:0,startsAt:new Date(Date.now()-60000).toISOString(),endsAt:new Date(Date.now()+86400000).toISOString(),totalLimit:100,customerLimit:5,operatorRouteId:route.id},login.accessToken)).data;
 assert.equal(campaign.customer_visible,false);
 await api(p,'POST','/operator/vouchers/'+campaign.id+'/activate',{},login.accessToken);
 if(phase!=='m304a-baseline')assert(!(await api(p,'GET','/public/promotions?operatorId='+trip.operator.id)).data.some(v=>v.code===code));
 await api(p,'PATCH','/operator/vouchers/'+campaign.id+'/visibility',{customerVisible:true},login.accessToken);
 report.checks.push('Live API create, activate and opt-in voucher using operator admin');
 const tripUrl='/trips/'+trip.tripId+'?pickupLocationId='+trip.pickup.locationId+'&dropoffLocationId='+trip.dropoff.locationId;
 const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date(trip.pickup.departureTime));
 const search='/search?'+new URLSearchParams({pickupLocationId:String(trip.pickup.locationId),pickupLabel:trip.pickup.name,dropoffLocationId:String(trip.dropoff.locationId),dropoffLabel:trip.dropoff.name,departureDate:date,operatorId:String(trip.operator.id)});
 await p.goto(origin+'/');await p.getByRole('heading',{name:/Đi xa một chút/}).waitFor();
 if(phase!=='m304a-baseline'){
  const card=p.locator('.public-promotion').filter({hasText:code});await card.waitFor();
  for(const width of [1440,834,390,360]){await p.setViewportSize({width,height:900});await capture(p,'home-promotions-'+width);}
  await p.setViewportSize({width:1440,height:900});
  await card.getByRole('link',{name:'Tìm chuyến phù hợp'}).click();await p.waitForURL('**/search?**');await p.getByLabel('Ngày đi',{exact:true}).fill(date);await p.getByRole('button',{name:'Tìm chuyến',exact:true}).click();
 }else{await p.goto(origin+search);}
 await p.locator('.trip-card').first().waitFor();const selected=p.locator('.trip-card').filter({has:p.locator('a[href*="/trips/'+trip.tripId+'?"]')}).first();if(phase!=='m304a-baseline')await selected.getByRole('link',{name:'Có ưu đãi — xem điều kiện',exact:true}).waitFor();await selected.getByRole('link',{name:/Chọn chỗ/}).click();await p.waitForURL('**/trips/'+trip.tripId+'?**');
 const seat=p.locator('.seat:not(:disabled)').first();await seat.click();
 if(phase!=='m304a-baseline'){
  await p.locator('#trip-promotions summary').click();await p.locator('#trip-promotions').getByText(code,{exact:true}).waitFor();assert.equal(await p.locator('.seat.selected').count(),1);
 }
 await p.getByRole('button',{name:'Tiếp tục',exact:true}).click();await p.waitForURL('**/login?**');
 await p.getByRole('link',{name:'Đăng ký ngay',exact:true}).click();await p.waitForURL('**/register?**');
 const email='live.m304b.'+stamp+'@example.test',password='LiveAcceptance!2026';
 await p.getByLabel('Họ và tên',{exact:true}).fill('Khách nghiệm thu '+stamp);await p.getByLabel('Email',{exact:true}).fill(email);await p.getByLabel('Số điện thoại',{exact:true}).fill('0901234567');await p.getByLabel('Mật khẩu',{exact:true}).fill(password);await p.getByLabel('Nhập lại mật khẩu',{exact:true}).fill(password);await p.getByRole('button',{name:'Đăng ký',exact:true}).click();await p.waitForURL('**/login?**');
 await p.getByLabel('Email',{exact:true}).fill(email);await p.getByLabel('Mật khẩu',{exact:true}).fill(password);await p.getByRole('button',{name:'Đăng nhập',exact:true}).click();await p.waitForURL('**/trips/'+trip.tripId+'?**');await p.getByText(/Đã khôi phục ghế/).waitFor();assert.equal(await p.locator('.seat.selected').count(),1);
 report.checks.push('Live search card -> guest seats -> register -> login -> exact trip/seat restored');
 await p.getByRole('button',{name:'Tiếp tục',exact:true}).click();await p.waitForURL('**/booking');await p.locator('.checkout-voucher-options summary').click();
 await p.getByRole('button',{name:'Chọn mã '+code,exact:true}).click();await p.getByText(new RegExp('Ưu đãi '+code+':')).waitFor();
 const tokens=await p.evaluate(()=>JSON.parse(sessionStorage.getItem('busgo.auth')));const saved=await p.evaluate(()=>JSON.parse(sessionStorage.getItem('busgo.hold')));
 const quote=(await api(p,'POST','/bookings/voucher-quote',{holdToken:saved.holdToken,voucherCode:code},tokens.accessToken)).data;
 assert.equal(quote.grossAmount-quote.discountAmount,quote.totalAmount);
 await capture(p,'checkout-1440');await p.setViewportSize({width:390,height:844});await capture(p,'checkout-390');
 await p.getByRole('button',{name:'Tiếp tục đến thanh toán'}).click();await p.waitForURL('**/payment?bookingId=*');const bookingId=new URL(p.url()).searchParams.get('bookingId');
 const booking=(await api(p,'GET','/bookings/'+bookingId,undefined,tokens.accessToken)).data;assert.equal(booking.price.voucherCode,code);assert.equal(booking.totalAmount,quote.totalAmount);
 report.checks.push('Live hold -> voucher-options -> select -> server quote -> booking committed exact gross/discount/net');
 await p.getByRole('button',{name:'Xác nhận thanh toán mô phỏng',exact:true}).click();await p.waitForURL('**/booking-success?bookingId='+bookingId);await p.getByRole('heading',{name:'Đặt vé thành công!',exact:true}).waitFor();
 const after=(await api(p,'GET','/operator/vouchers/'+campaign.id,undefined,login.accessToken)).data;assert.equal(after.redeemed,1);assert.equal(after.reserved,0);
 report.checks.push('Live mock payment redeems exactly one voucher use');
 if(phase!=='m304a-baseline'){
  await api(p,'PATCH','/operator/vouchers/'+campaign.id+'/visibility',{customerVisible:false},login.accessToken);
  assert(!(await api(p,'GET','/public/promotions?operatorId='+trip.operator.id)).data.some(v=>v.code===code));
  await api(p,'PATCH','/operator/vouchers/'+campaign.id+'/visibility',{customerVisible:true},login.accessToken);
  await p.goto(origin+'/');await p.locator('.public-promotion').filter({hasText:code}).waitFor();
  await api(p,'POST','/operator/vouchers/'+campaign.id+'/pause',{},login.accessToken);
  await p.getByRole('button',{name:'Cập nhật ưu đãi',exact:true}).click();await p.locator('.public-promotion').filter({hasText:code}).waitFor({state:'hidden'});
  const committed=(await api(p,'GET','/bookings/'+bookingId,undefined,tokens.accessToken)).data;assert.equal(committed.totalAmount,quote.totalAmount);
  report.checks.push('Live private default, public search badge, visibility off and pause refresh hide campaign without repricing committed booking');
 }
 report.bookingId=bookingId;report.tripId=trip.tripId;report.voucherId=campaign.id;report.code=code;report.price=booking.price;assert.equal(report.pageErrors.length,0);report.status='passed';
}catch(e){report.status='failed';report.failure=e.stack;await p.screenshot({path:path.join(out,'failure.png'),fullPage:true});fs.writeFileSync(path.join(out,'failure.txt'),await p.locator('body').innerText());console.error(e);process.exitCode=1;}finally{fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(report,null,2));await browser.close();}}
module.exports={loadPlaywright,verifyLiveEnvironment};
if(require.main===module)main().catch(error=>{console.error(error.message);process.exitCode=1;});
