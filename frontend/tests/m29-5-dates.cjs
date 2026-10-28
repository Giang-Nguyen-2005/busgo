// Focused fixture evidence for the confirmed raw-ISO presentation defects.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/Giang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const {dataFor}=require('./fixtures/m29-api.cjs');
const out=path.resolve('.tools/m29-browser/m29-5');
(async()=>{const b=await chromium.launch({channel:'msedge',headless:true});try{
 const c=await b.newContext({viewport:{width:390,height:900},timezoneId:'America/Los_Angeles'});
 await c.addInitScript(()=>sessionStorage.setItem('busgo.auth',JSON.stringify({accessToken:'fixture',refreshToken:'fixture',expiresIn:3600})));
 const stamp='2026-10-23T20:00:00Z';const application={id:1,name:'M29 timestamp fixture',status:'UNDER_REVIEW',submitted_at:stamp,invitations:[{id:1,intended_email:'test@example.invalid',expires_at:stamp}],history:[{id:1,event_type:'SUBMITTED',created_at:stamp}]};
 await c.route('**/api/v1/**',async r=>{const p=new URL(r.request().url()).pathname.replace('/api/v1','');let data=dataFor(p,'SYSTEM_ADMIN');if(p==='/admin/operator-applications')data={data:[application],pagination:{page:0,size:20,totalPages:1,totalElements:1}};if(p==='/admin/operator-applications/1')data={data:application};await r.fulfill({json:data||{data:[]}});});
 const p=await c.newPage();const errors=[];p.on('pageerror',e=>errors.push(e.message));
 for(const url of ['/admin/operator-applications','/admin/operator-applications/1']){await p.goto('http://127.0.0.1:5195'+url);await p.waitForLoadState('networkidle');const text=await p.locator('body').innerText();assert(text.includes('24/10/2026 03:00 (Việt Nam)'));assert(!text.includes(stamp));assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));}
 await p.screenshot({path:path.join(out,'fixed-onboarding-dates-390.png'),fullPage:true});assert.deepEqual(errors,[]);
 fs.writeFileSync(path.join(out,'date-results.json'),JSON.stringify({status:'PASS',mode:'API fixtures',browserTimezone:'America/Los_Angeles',expected:'24/10/2026 03:00 (Việt Nam)',checks:['application list','facts','invitation expiry','history','390px overflow']},null,2));
 }finally{await b.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
