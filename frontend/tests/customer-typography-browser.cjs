// Real React + Edge, synthetic API fixtures only. No backend access.
const fs=require('node:fs'),path=require('node:path'),Module=require('node:module');
const filename=path.resolve('frontend/tests/m29-customer-browser.cjs');
const out=path.resolve('.tools/content-polish/visual');fs.mkdirSync(out,{recursive:true});
let source=fs.readFileSync(filename,'utf8').replace("const out = path.resolve(__dirname, '../../.tools/m29-browser/m29-2');",`const out=${JSON.stringify(out)};`)
 .replace("name: 'Giường nằm'","name: 'Giường nằm 34 chỗ'").replace("name: 'Nhà xe thử nghiệm'","name: 'An Phú Express'");
source=source.replace("if (p === '/users/me')",`if(p==='/public/operators/1/profile')return ok({data:{operator,routes:[{...trip.route,pickupLocationId:1,dropoffLocationId:2}],busTypes:[trip.busType],trips:[trip]}});
 if(p==='/public/operators/1/reviews')return ok(paged([]));
 if(p==='/support/dashboard')return ok({data:[]});
 if(p==='/support/tickets')return ok(paged([]));
 if (p === '/users/me')`);
source=source.replace('state.discoveryEmpty ? [] : [operator]',`state.discoveryEmpty ? [] : [operator,{...operator,id:2,name:'Minh Thành Limousine',averageRating:null,reviewCount:0},{...operator,id:3,name:'Tây Nguyên Travel',averageRating:null,reviewCount:0}]`);
source=source.slice(0,source.indexOf(' for (const width of (process.argv'))+source.slice(source.indexOf(' assert.equal(report.consoleErrors.length,0);'));
source=source.replace('async function capture(p, name) {', 'async function capture(p, name) { await p.locator("img").evaluateAll(async images => { images.forEach(img => img.loading="eager"); await Promise.all(images.map(img => img.decode().catch(()=>{}))); });');
const checks=`
 const contrast=[];
 async function checkText(selector) {
  const values=await p.locator(selector).evaluateAll(elements=>elements.filter(e=>e.getBoundingClientRect().height).map(e=>{
   const cs=getComputedStyle(e);let node=e,bg='rgb(255, 255, 255)';while(node){const c=getComputedStyle(node).backgroundColor;if(c!=='rgba(0, 0, 0, 0)'&&c!=='transparent'){bg=c;break;}node=node.parentElement;}
   const luminance=c=>{const n=c.match(/[\\d.]+/g).slice(0,3).map(Number).map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4;});return n[0]*.2126+n[1]*.7152+n[2]*.0722;};
   const a=luminance(cs.color),b=luminance(bg);return {text:e.textContent.trim().slice(0,55),color:cs.color,bg,size:cs.fontSize,ratio:(Math.max(a,b)+.05)/(Math.min(a,b)+.05)};
  }));for(const v of values){assert(v.ratio>=4.5,selector+' contrast '+JSON.stringify(v));contrast.push({selector,...v});}
 }
 for(const width of [1440,1100,834,390,360]) {
  await p.setViewportSize({width,height:1000});await p.goto(origin+'/');await ready(p);
  for(const img of await p.locator('.destination-card img').all()){await img.scrollIntoViewIfNeeded();await img.evaluate(e=>e.decode());}
  assert.equal(await p.locator('.market-operator').count(),3);
  assert.equal(await p.locator('.market-operator .operator-description').count(),0);
  assert.equal(await p.locator('.operator-profile-cta').count(),3);
  const route=p.locator('.route-card-copy');const a=await route.locator('strong').boundingBox(),b=await route.locator('.route-card-cta').boundingBox();assert(b.y>=a.y+a.height);
  const sectionSize=await p.locator('#destinations-heading').evaluate(e=>parseFloat(getComputedStyle(e).fontSize));assert(sectionSize>=26&&sectionSize<=32);
  await checkText('.operator-profile-cta,.market-rating,.route-card-cta,.benefits-grid p,.home-promo-copy > span,.customer-faq summary,.customer-footer-inner');
  await p.evaluate(()=>{document.activeElement?.blur();scrollTo(0,0);});await capture(p,'home-'+width);
  for(const [name,selector] of [['operators','#public-operators'],['routes','.discovery-route'],['benefits','.customer-benefits'],['guide','#booking-guide']])await p.locator(selector).screenshot({path:path.join(out,name+'-'+width+'.png')});
  for(const [name,url] of [['search',search],['trip',tripUrl],['operator','/operators/1'],['account','/profile'],['support','/support']]){
   await p.goto(origin+url);await ready(p);await capture(p,name+'-'+width);
   if(name==='search'){await checkText('.trip-price strong,.trip-times strong,.trip-route,.trip-bottom > .muted,.trip-bottom .button');}
   if(name==='trip'){assert.equal(await p.locator('#trip-seats .vehicle-media').count(),0);await checkText('.selection-summary .fine-print');}
  }
 }
 // Stress existing presentation with deliberately long fixture labels, not production data.
 await p.goto(origin+search);await ready(p);await p.evaluate(()=>{for(const e of document.querySelectorAll('.trip-meta strong'))e.textContent='Nhà xe thử nghiệm tên dài kết nối Thành phố Hồ Chí Minh và các tỉnh Tây Nguyên';for(const e of document.querySelectorAll('.trip-times > div > span'))e.textContent='Bến xe thử nghiệm với tên điểm đón rất dài để kiểm tra xuống dòng';});await capture(p,'search-long-360');
 await p.goto(origin+'/');await ready(p);await p.evaluate(()=>{document.querySelector('.route-card-copy strong').textContent='Tuyến thử nghiệm tên dài: Thành phố Hồ Chí Minh – Buôn Ma Thuột – các điểm dừng theo hành trình';});await capture(p,'route-long-360');
 state.discoveryEmpty=true;await p.goto(origin+'/');await ready(p);await capture(p,'home-empty-360');state.discoveryEmpty=false;
 state.empty=true;await p.goto(origin+search);await ready(p);await capture(p,'search-empty-360');state.empty=false;
 await p.goto(origin+'/login');await ready(p);assert.equal(await p.locator('h1').count(),1);await capture(p,'login-360');
 await p.goto(origin+'/register');await ready(p);assert.equal(await p.locator('h1').count(),1);await capture(p,'register-360');
 fs.writeFileSync(path.join(out,'contrast.json'),JSON.stringify(contrast,null,2));
 check('Typography hierarchy, separate route CTA, three honest operator cards, account/support/auth, long labels, empty states and 5 viewports');
 check('Representative text and primary CTA contrast >=4.5:1 on computed solid backgrounds');
`;
source=source.replace(' assert.equal(report.consoleErrors.length,0);',checks+'\n assert.equal(report.consoleErrors.length,0);');
const mod=new Module(filename,module);mod.filename=filename;mod.paths=Module._nodeModulePaths(path.dirname(filename));mod._compile(source,filename);
