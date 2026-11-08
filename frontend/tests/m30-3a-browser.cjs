// Real React + Edge, synthetic API and image fixtures only. No backend mutations.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/Giang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const origin=process.env.BUSGO_TEST_ORIGIN || 'http://127.0.0.1:5190';
const out=path.resolve('.tools/m30-3a'); fs.mkdirSync(out,{recursive:true});
const operator={id:87,name:'Nhà xe fixture',description:'Giới thiệu do API cung cấp.',logoUrl:'/fixture-logo.svg',averageRating:null,reviewCount:0};
const routes=[{id:91,name:'TP. Hồ Chí Minh → Đà Lạt',pickupLocationId:739,dropoffLocationId:482}];
const svg='<svg xmlns="http://www.w3.org/2000/svg" width="240" height="80"><rect width="240" height="80" fill="#26734d"/></svg>';
(async()=>{const browser=await chromium.launch({channel:'msedge',headless:true});const page=await browser.newPage();const errors=[];page.on('pageerror',e=>{errors.push(e.message);console.error('PAGE ERROR',e.message);});
try {
 await page.route('**/api/v1/**',route=>{const p=new URL(route.request().url()).pathname.replace('/api/v1','');let data=[];
 if(p==='/public/discovery/operators')data=[operator];
 if(p==='/public/discovery/routes')data=routes;
 if(p==='/locations')data=[{id:739,name:'Bến xe TP. Hồ Chí Minh'},{id:482,name:'Bến xe Đà Lạt'}];
 if(p==='/public/operators/87/profile')data={operator,routes,busTypes:[],trips:[]};
 const paged=/operators$|recent$|reviews$|promotions$/.test(p);
 return route.fulfill({contentType:'application/json',body:JSON.stringify({data,...(paged?{pagination:{page:0,size:10,totalElements:data.length,totalPages:1}}:{})})});});
 await page.route('**/fixture-logo.svg',r=>r.fulfill({contentType:'image/svg+xml',body:svg}));
 for(const width of [1440,834,390]){
  await page.setViewportSize({width,height:1000});await page.goto(origin);await page.locator('.market-operator img').scrollIntoViewIfNeeded();
  await page.waitForFunction(()=>document.querySelector('.market-operator img')?.naturalWidth>0);
  assert.equal(await page.locator('.discovery-route').count(),1);assert.equal(await page.locator('.discovery-route .destination-image img').count(),1);await page.locator('.discovery-route .destination-image img').scrollIntoViewIfNeeded();await page.waitForFunction(()=>document.querySelector('.discovery-route .destination-image img')?.naturalWidth>0);
  await page.locator('.customer-faq summary').first().click();
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  const button=page.getByRole('button',{name:'Tìm chuyến',exact:true});await button.scrollIntoViewIfNeeded();await button.click({trial:true});
  await page.evaluate(()=>{document.activeElement?.blur();window.scrollTo(0,0);});
  await page.screenshot({path:path.join(out,`home-${width}.png`),fullPage:true});
  await page.locator('.market-operator').click();await page.locator('.market-profile-heading img').waitFor();await page.getByText(operator.description,{exact:true}).waitFor();
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 }
 operator.logoUrl='/missing-logo.png';operator.description='   ';await page.goto(origin);await page.locator('.market-operator').scrollIntoViewIfNeeded();assert.equal(await page.locator('.market-operator .operator-description').count(),0);await page.waitForFunction(()=>!document.querySelector('.operator-logo img'));
 await page.locator('.market-operator').click();await page.getByText('Thông tin giới thiệu chưa được cung cấp.',{exact:true}).waitFor();await page.waitForFunction(()=>!document.querySelector('.operator-logo img'));
 // Render production media components in a test-only page to exercise errors and URL changes.
 await page.route('**/asset-test',r=>r.fulfill({contentType:'text/html',body:'<div id="root"></div><script type="module" src="/asset-test-module"></script>'}));
 await page.route('**/asset-test-module',r=>r.fulfill({contentType:'text/javascript',body:`import React from '/node_modules/.vite/deps/react.js';import ReactDOM from '/node_modules/.vite/deps/react-dom_client.js';const{createRoot}=ReactDOM;import RefreshRuntime from '/@react-refresh';RefreshRuntime.injectIntoGlobalHook(window);window.$RefreshReg$=()=>{};window.$RefreshSig$=()=>t=>t;window.__vite_plugin_react_preamble_installed__=true;const{AssetImage,DestinationImage,OperatorLogo}=await import('/src/features/customer/CustomerMedia.tsx');const root=createRoot(document.getElementById('root'));window.draw=(src,fallbackSrc='/missing-fallback.png')=>root.render(React.createElement('div',{},React.createElement(OperatorLogo,{name:'Test',logoUrl:src}),React.createElement(DestinationImage,{asset:{imageUrl:src}}),React.createElement(AssetImage,{src,fallbackSrc,alt:'hero test',width:1920,height:960})));window.draw('/missing-image.png');`}));
 await page.goto(origin+'/asset-test');await page.waitForFunction(()=>window.draw && !document.querySelector('img'));
 await page.evaluate(()=>window.draw('/fixture-logo.svg'));await page.waitForFunction(()=>document.querySelectorAll('img').length===3 && [...document.images].every(i=>i.naturalWidth>0));
 await page.evaluate(()=>window.draw('/missing-again.png'));await page.waitForFunction(()=>!document.querySelector('img'));
 await page.evaluate(()=>window.draw('/missing-primary.png','/fixture-logo.svg'));await page.waitForFunction(()=>document.images.length===1 && document.images[0].naturalWidth>0);
 assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'report.json'),JSON.stringify({mode:'Edge + real React; synthetic fixtures only',widths:[1440,834,390],checks:['API-only route cards','logo success/error/reset on home/profile and media harness','blank description','missing destination and final image fallback','no horizontal overflow','search button actionable','FAQ keyboard-native details'],pageErrors:errors},null,2));console.log('PASS M30.3A browser: 1440, 834, 390; image error/reset/final fallback; no overflow; search actionable');
}finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
