const fs=require('fs'),path=require('path'),assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const {dataFor}=require('./fixtures/m29-api.cjs');
const out=path.resolve(__dirname,'../../.tools/m29-browser'),origin=process.env.BUSGO_ORIGIN||'http://127.0.0.1:5188';
fs.mkdirSync(out,{recursive:true});
const cases={CUSTOMER:[['customer','/my-bookings'],['customer-seats','/trips/7?pickupLocationId=1&dropoffLocationId=2']],OPERATOR_ADMIN:[['operator','/operator'],['operator-bookings','/operator/bookings'],['operator-finance','/operator/finance']],OPERATOR_STAFF:[['staff','/operator'],['staff-bookings','/operator/bookings']],SYSTEM_ADMIN:[['admin','/admin'],['admin-disputes','/admin/disputes']]};
const result={mode:'Real React app and production router; synthetic API responses; no backend calls',captures:[],interactions:[],errors:[],unknownRequests:[]};
async function settle(p){await p.waitForLoadState('networkidle');await p.waitForTimeout(250);}
(async()=>{const browser=await chromium.launch({channel:'msedge',headless:true});try{
for(const [role,routes]of Object.entries(cases)){
 const context=await browser.newContext({viewport:{width:1440,height:900},locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh'});
 await context.addInitScript(()=>{if(!sessionStorage.getItem('m29-initialized')){sessionStorage.setItem('busgo.auth',JSON.stringify({accessToken:'fixture-only',refreshToken:'fixture-only',expiresIn:3600}));sessionStorage.setItem('m29-initialized','1')}});
 await context.route('**/api/v1/**',async route=>{const p=new URL(route.request().url()).pathname.replace('/api/v1','');const data=dataFor(p,role);if(data===undefined)result.unknownRequests.push({role,path:p});await route.fulfill({status:data===undefined?404:200,contentType:'application/json',body:JSON.stringify(data??{message:'Missing test fixture'})});});
 const p=await context.newPage();p.on('pageerror',e=>result.errors.push({role,type:'page',message:e.message}));p.on('console',m=>{if(m.type()==='error')result.errors.push({role,type:'console',message:m.text()})});
 for(const [id,url]of routes){for(const width of [1440,834,390,360]){
  await p.setViewportSize({width,height:width===1440?900:844});await p.goto(origin+url);await settle(p);
  const metrics=await p.evaluate(()=>({width:innerWidth,documentWidth:document.documentElement.scrollWidth,h1:[...document.querySelectorAll('h1')].map(e=>e.textContent),body:document.body.innerText.slice(0,120)}));
  await p.screenshot({path:path.join(out,`${id}-${width}.png`),fullPage:true});result.captures.push({role,id,url,...metrics});console.log(id,width,metrics.documentWidth,metrics.h1);
 }}
 // Each shell: mobile modal semantics, focus containment, Escape/focus return, navigation dismissal.
 await p.goto(origin+routes[0][1]);await settle(p);const trigger=p.getByRole('button',{name:'Mở điều hướng',exact:true});await trigger.focus();await p.keyboard.press('Enter');const dialog=p.getByRole('dialog');await dialog.waitFor();await p.waitForTimeout(250);
 assert.equal(await p.evaluate(()=>document.body.style.overflow),'hidden');assert.equal(await dialog.evaluate(e=>e.contains(document.activeElement)),true);
 const links=await dialog.locator('nav a').evaluateAll(es=>es.map(e=>e.getAttribute('href')));
 if(role==='OPERATOR_STAFF')for(const denied of ['/operator/finance','/operator/staff','/operator/buses','/operator/reports','/operator/customers','/operator/routes','/operator/maintenance'])assert(!links.includes(denied),denied);
 for(let i=0;i<25;i++){await p.keyboard.press('Tab');assert.equal(await dialog.evaluate(e=>e.contains(document.activeElement)),true,'focus containment');}
 await p.screenshot({path:path.join(out,`${role.toLowerCase()}-drawer-focus-360.png`),fullPage:true});await p.keyboard.press('Escape');assert.equal(await dialog.isVisible(),false);assert.equal(await trigger.evaluate(e=>e===document.activeElement),true);assert.notEqual(await p.evaluate(()=>document.body.style.overflow),'hidden');
 await trigger.click();await dialog.locator('nav a:not([aria-current=page])').first().click();await settle(p);assert.equal(await dialog.isVisible(),false);
 await p.goBack();await settle(p);assert.equal(new URL(p.url()).pathname,new URL(origin+routes[0][1]).pathname);await p.goForward();await settle(p);
 await trigger.click();await p.getByRole('button',{name:'Đóng điều hướng',exact:true}).click();assert.equal(await trigger.evaluate(e=>e===document.activeElement),true);
 await trigger.click();await p.mouse.click(355,500);assert.equal(await dialog.isVisible(),false);
 await trigger.click();await p.setViewportSize({width:1440,height:900});assert.equal(await dialog.isVisible(),false);assert.notEqual(await p.evaluate(()=>document.body.style.overflow),'hidden');
 await p.setViewportSize({width:390,height:844});await p.emulateMedia({reducedMotion:'reduce'});await trigger.click();assert.equal(await dialog.evaluate(e=>getComputedStyle(e).animationName),'none');await p.keyboard.press('Escape');
 await p.goto(origin+routes[0][1]);await settle(p);assert.equal(await p.locator('.app-page-content').evaluate(e=>e.getAnimations().length),0);
 // Notification and account/logout access remain available.
 if(role==='CUSTOMER'||role==='OPERATOR_ADMIN'){await p.getByRole('button',{name:/^Thông báo,/}).click();await p.getByRole('button',{name:'Đóng thông báo'}).waitFor();await p.screenshot({path:path.join(out,`${role.toLowerCase()}-notifications-390.png`)});await p.getByRole('button',{name:'Đóng thông báo'}).click();}
 if(role==='CUSTOMER'){await trigger.click();await dialog.getByRole('button',{name:'Đăng xuất',exact:true}).click();}
 else {await p.locator('.ws-account summary').click();await p.screenshot({path:path.join(out,`${role.toLowerCase()}-account-390.png`)});await p.keyboard.press('Escape');assert.equal(await p.locator('.ws-account').getAttribute('open'),null);await p.locator('.ws-account summary').click();await p.locator('.ws-account').getByRole('button',{name:'Đăng xuất'}).click();}
 await p.waitForTimeout(250);assert.equal(await p.evaluate(()=>sessionStorage.getItem('busgo.auth')),null);
 result.interactions.push({role,checks:['keyboard open','focus containment (25 tabs)','Escape','focus return','scroll lock/unlock','navigation dismissal','back/forward','close button','backdrop','desktop resize','reduced motion','account/logout','notification access where authorized'],pass:true});
 await context.close();
}
}finally{fs.writeFileSync(path.join(out,'acceptance.json'),JSON.stringify(result,null,2));await browser.close();}
assert.equal(result.unknownRequests.length,0,JSON.stringify(result.unknownRequests));assert.equal(result.errors.length,0,JSON.stringify(result.errors));assert(result.captures.every(c=>c.documentWidth<=c.width),'document overflow');console.log('PASS',result.captures.length,'viewports; four role interaction suites');})().catch(e=>{console.error(e);process.exit(1)});
