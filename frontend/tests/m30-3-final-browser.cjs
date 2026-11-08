// Real Edge + production React/components and local assets. All APIs are synthetic fixtures.
const fs=require('node:fs'),path=require('node:path'),Module=require('node:module');
const filename=path.resolve('frontend/tests/m29-customer-browser.cjs');
const out=path.resolve(process.env.BUSGO_MEDIA_OUT || '.tools/m30-3-final/browser');fs.mkdirSync(out,{recursive:true});
let source=fs.readFileSync(filename,'utf8')
 .replace("const out = path.resolve(__dirname, '../../.tools/m29-browser/m29-2');",`const out=${JSON.stringify(out)};`)
 .replace("name: 'Giường nằm'", `name: '${process.env.BUSGO_MEDIA_TYPE || 'Giường nằm 34 chỗ'}'`)
 .replace("name: 'Nhà xe thử nghiệm'", "name: 'An Phú Express'");
if (process.env.BUSGO_MEDIA_TYPE === 'Limousine 22 chỗ') {
 // Match the read-only catalogue review: 11 rows, columns 1/3, one floor.
 source=source.replace(/^const seats = .*;\r?$/m, "const seats = Array.from({length:22},(_,i)=>({tripSeatId:i+1,seatCode:'A'+String(i+1).padStart(2,'0'),row:Math.floor(i/2)+1,column:i%2?3:1,floor:1,seatType:'STANDARD',available:true}));")
  .replace('availableSeats: 15','availableSeats: 22')
  .replace('availableSeatCount: 15 - state.unavailable.length','availableSeatCount: 22 - state.unavailable.length');
}
source=source.replace(" if (p === '/public/promotions'",` if(p==='/public/operators/1/reviews')return ok(paged([])); if(p==='/public/operators/1/profile')return ok({data:{operator,routes:[{...trip.route,pickupLocationId:1,dropoffLocationId:2}],busTypes:[trip.busType],trips:[trip]}});\n if (p === '/public/promotions'`);
// This suite adds media acceptance to the existing customer journey regression.
const checks=`
 state.discoveryEmpty=false;state.discoveryError=false;state.error=false;state.empty=false;state.expired=false;state.unavailable=[];
 const assetFailures=[];p.on('response',r=>{if(r.url().includes('/images/busgo/')&&r.status()>=400)assetFailures.push(r.url());});
 for(const width of [1440,1100,834,390,360]){
  await p.setViewportSize({width,height:1000});await p.goto(origin+'/');await ready(p);
  await p.locator('.destination-image img').first().scrollIntoViewIfNeeded();await p.waitForFunction(()=>document.querySelector('.destination-image img')?.naturalWidth>0);
  assert.equal(await p.locator('.home-promo-card').count(),3);assert.equal(await p.locator('img[src*="promo-featured"]').count(),1);
  await p.getByText('Bản xem trước · Chưa có hiệu lực').waitFor();
  await p.locator('.home-promo-card').first().click();assert.equal(new URL(p.url()).hash,'#public-promotions');await p.getByText(/Hiện chưa có chương trình phù hợp để giới thiệu/).waitFor();
  await p.locator('.home-promo-card').nth(1).click();assert.equal(new URL(p.url()).hash,'#public-operators');
  await p.evaluate(()=>scrollTo(0,0));await capture(p,'assets-home-'+width);
  await p.locator('.home-promo-cards').scrollIntoViewIfNeeded();await p.screenshot({path:path.join(out,'promotions-'+width+'.png')});
  await p.goto(origin+search);await ready(p);await p.locator('.vehicle-image-button').first().scrollIntoViewIfNeeded();
  await p.waitForFunction(()=>document.querySelector('.vehicle-image-button img')?.naturalWidth>0);await capture(p,'interior-search-'+width);
  if(trip.busType.name==='Limousine 22 chỗ')assert((await p.locator('.vehicle-image-button img').first().getAttribute('src')).endsWith('/interior-limousine-seat-vip.webp'));
  await p.locator('.vehicle-image-button').first().click();await p.locator('dialog[open]').waitFor();await p.keyboard.press('Escape');assert.equal(await p.locator('dialog[open]').count(),0);
  await p.goto(origin+tripUrl);await ready(p);await p.getByRole('button',{name:/^Chỗ A01,/}).click();
  const selected=await p.locator('.seat.selected').count();assert(selected>0);
  const before=state.calls.filter(c=>c.method==='POST').length;
  const trigger=p.getByRole('button',{name:'Xem ảnh minh họa '+trip.busType.name});
  assert.equal(await p.locator('#trip-seats .vehicle-media').count(),0);
  assert.equal(await p.locator('.trip-heading .vehicle-media-trigger').count(),1);
  assert.equal(await p.locator('.vehicle-image-button:visible').count(),0);
  assert((await trigger.boundingBox()).height<=60);
  await trigger.click();
  assert.equal(await p.locator('dialog[open]').count(),1);assert.equal(await p.getByRole('button',{name:'Đóng ảnh'}).evaluate(e=>e===document.activeElement),true);
  if(width===390)await p.screenshot({path:path.join(out,'interior-modal-390.png')});await p.keyboard.press('Tab');assert.equal(await p.getByRole('button',{name:'Đóng ảnh'}).evaluate(e=>e===document.activeElement),true);
  await p.keyboard.press('Escape');assert.equal(await p.locator('.seat.selected').count(),selected);assert(await trigger.evaluate(e=>e===document.activeElement));
  await trigger.click();await p.getByRole('button',{name:'Đóng ảnh'}).click();assert.equal(await p.locator('.seat.selected').count(),selected);assert.equal(state.calls.filter(c=>c.method==='POST').length,before);
  await p.evaluate(()=>{document.activeElement?.blur();scrollTo(0,0);});await capture(p,'interior-trip-'+width);
 }
 await p.goto(origin+'/operators/1');await ready(p);await p.waitForFunction(()=>document.querySelector('.operator-logo img')?.naturalWidth>0);assert.equal(await p.locator('.vehicle-image-button').count(),2);await capture(p,'operator-profile-360');
 assert.deepEqual(assetFailures,[]);
 check('M30.3 final: five widths, local WebP images, square banners/anchors, empty promotions, no asset 404; modal focus/Escape/button preserve selected seats without POST');
 // Media harness: force real load failures, then change the bus type on the same React root.
 await p.goto(origin+'/tests/fixtures/customer-media.html');await p.waitForFunction(()=>document.querySelector('.vehicle-image-button img')?.naturalWidth>0);
 await p.evaluate(()=>window.draw('Limousine 22 chỗ'));await p.waitForFunction(()=>document.querySelector('.vehicle-image-button img')?.src.endsWith('/interior-limousine-seat-vip.webp') && document.querySelector('.vehicle-image-button img').naturalWidth>0);await p.evaluate(()=>window.draw('Limousine'));await p.getByText('Chưa có ảnh nội thất phù hợp',{exact:true}).waitFor();assert.equal(await p.locator('img').count(),0);
 await p.evaluate(()=>window.draw('Giường nằm 34 chỗ'));await p.waitForFunction(()=>document.querySelector('.vehicle-image-button img'));await p.evaluate(()=>document.querySelector('.vehicle-image-button img').dispatchEvent(new Event('error')));await p.getByText('Chưa có ảnh nội thất phù hợp',{exact:true}).waitFor();assert.equal(await p.locator('img').count(),0);
 await p.evaluate(()=>window.draw('Ghế ngồi 40 chỗ'));await p.getByText('Chưa có ảnh nội thất phù hợp',{exact:true}).waitFor();await p.evaluate(()=>window.draw('Giường nằm 34 chỗ'));await p.waitForFunction(()=>document.querySelector('img')?.naturalWidth>0);
 await p.evaluate(()=>window.logo('An Phú Express','/images/busgo/operator-an-phu-express-logo.webp'));await p.waitForFunction(()=>document.querySelector('img')?.naturalWidth>0);
 await p.evaluate(()=>window.logo('Unknown','/intentional-missing-image.webp'));await p.waitForFunction(()=>!document.querySelector('img'));
 await p.evaluate(()=>window.destination('/intentional-missing-image.webp'));await p.waitForFunction(()=>!document.querySelector('img'));
 await p.evaluate(()=>window.logo('Minh Thành Limousine',null));await p.waitForFunction(()=>document.querySelector('img')?.naturalWidth>0);
 await p.evaluate(()=>window.compact('Unknown'));await p.getByText('Chưa có ảnh minh họa',{exact:true}).waitFor();assert.equal(await p.locator('.vehicle-image-placeholder').count(),0);
 await p.evaluate(()=>window.compact('Giường nằm 34 chỗ'));await p.locator('.vehicle-media-trigger').click();await p.evaluate(()=>document.querySelector('dialog[open] img').dispatchEvent(new Event('error')));await p.getByText('Chưa có ảnh minh họa',{exact:true}).waitFor();assert.equal(await p.locator('.vehicle-image-placeholder').count(),0);
 check('M30.3 final: changing vehicle type resets images, failed interior/logo/destination falls back, changing logo restores image');
`;
if(process.argv.includes('--media-only'))source=source.slice(0,source.indexOf(' for (const width of (process.argv'))+source.slice(source.indexOf(' assert.equal(report.consoleErrors.length,0);'));
source=source.replace(' assert.equal(report.consoleErrors.length,0);',checks+'\n assert.equal(report.consoleErrors.length,0);');
const mod=new Module(filename,module);mod.filename=filename;mod.paths=Module._nodeModulePaths(path.dirname(filename));mod._compile(source,filename);
