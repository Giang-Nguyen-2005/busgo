// Real application, synthetic API only. Also run against vite preview to check production gating.
const fs = require('node:fs'), path = require('node:path'), Module = require('node:module');
const filename = path.resolve('frontend/tests/m29-customer-browser.cjs');
const production = process.env.BUSGO_PRODUCTION_REVIEW === '1';
const out = path.resolve('.tools/customer-complete/' + (production ? 'production' : 'completeness'));
fs.mkdirSync(out, {recursive:true});
let source = fs.readFileSync(filename,'utf8').replace("const out = path.resolve(__dirname, '../../.tools/m29-browser/m29-2');", `const out = ${JSON.stringify(out)};`);
source = source.slice(0, source.indexOf(' for (const width of (process.argv')) + source.slice(source.indexOf(' assert.equal(report.consoleErrors.length,0);'));
const checks = `
 for (const width of [1440,1100,834,390,360]) {
  await p.setViewportSize({width,height:1000}); await p.goto(origin+'/'); await ready(p);
  assert.equal(await p.locator('.destination-card').count(),6);
  assert.equal(await p.locator('.destination-card a').count(),1);
  const href=await p.locator('.destination-card a').getAttribute('href');assert(href.includes('pickupLocationId=1'));assert(href.includes('dropoffLocationId=2'));
  assert.equal(await p.locator('.home-promo-card').count(),${production ? 2 : 3});
  assert.equal(await p.locator('img[src*="promo-featured"]').count(),${production ? 0 : 1});
  await p.locator('.home-promo-card').last().click();assert.equal(new URL(p.url()).hash,'${production ? '#public-operators' : '#public-promotions'}');
  const images=p.locator('.destination-card img');for(const img of await images.all()){await img.scrollIntoViewIfNeeded();await img.evaluate(e=>e.decode());}
  const dimensions=await images.first().boundingBox();assert(dimensions.width>200);assert(dimensions.height>100);
  await p.locator('.customer-footer a[href="/#booking-guide"]').click();await p.locator('#booking-guide').waitFor();
  await p.locator('.customer-footer a[href="/#customer-faq"]').click();
  const faq=p.locator('.customer-faq summary').first();await faq.focus();await p.keyboard.press('Enter');assert(await p.locator('.customer-faq details').first().getAttribute('open')!==null);
  await p.evaluate(()=>scrollTo(0,0));await capture(p,'home-'+width);
  assert.equal(await p.title(),'Tìm chuyến xe liên tỉnh | BusGo');
 }
 await p.goto(origin+search);await ready(p);assert.equal(await p.title(),'Kết quả tìm chuyến | BusGo');
 const logo=p.locator('.trip-meta .operator-logo').first();const box=await logo.boundingBox();assert.equal(box.width,32);assert.equal(box.height,32);
 check('Six destination artworks, exact API route links, no invented links, DEV/production banner gate, anchors, keyboard FAQ, titles and 5 widths');
`;
source=source.replace(' assert.equal(report.consoleErrors.length,0);',checks+'\n assert.equal(report.consoleErrors.length,0);');
const mod=new Module(filename,module);mod.filename=filename;mod.paths=Module._nodeModulePaths(path.dirname(filename));mod._compile(source,filename);
