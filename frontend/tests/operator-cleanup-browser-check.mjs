import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync } from 'node:fs';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root=new URL('../../.tools/operator-cleanup-render/',import.meta.url);mkdirSync(root,{recursive:true});
const browser=await chromium.launch({headless:true,channel:'msedge'});
const results=[];
try {
 for(const width of [390,820,1440]) {
  const page=await browser.newPage({viewport:{width,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.clock.setFixedTime(new Date('2027-01-15T18:00:00+07:00'));
  await page.goto('http://127.0.0.1:5173/tests/fixtures/operator-cleanup-browser.html');
  const control=name=>page.locator('[data-fixture-controls]').getByRole('button',{name,exact:true});
  const shot=async name=>{await page.waitForTimeout(400);await page.locator('[data-fixture-controls]').evaluate(e=>e.open=false);await page.evaluate(()=>scrollTo({top:0,behavior:'instant'}));await page.mouse.move(0,0);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,name+' page overflow');await page.screenshot({path:new URL(`${width}-${name}.png`,root).pathname.replace(/^\/([A-Z]:)/,'$1'),fullPage:true});await page.locator('[data-fixture-controls]').evaluate(e=>e.open=true);};
  await control('Operator dashboard').click();await page.locator('.dispatch-trip-metrics strong').first().waitFor();await page.waitForFunction(()=>document.querySelectorAll('.dispatch-trip-metrics strong').length===3);
  assert.deepEqual(await page.locator('.dispatch-totals dd').allTextContents(),['3','0','1','1']);
  assert.deepEqual(await page.locator('.dispatch-trip-metrics strong').allTextContents(),['3','12','2']);
  assert.equal(await page.locator('.dispatch-shortcuts a').count(),4);await shot('operator-dashboard');
  await control('Toggle inventory completeness').click();await page.getByText('Chưa đủ dữ liệu',{exact:true}).first().waitFor();assert.equal(await page.getByText('Chưa đủ dữ liệu',{exact:true}).count(),2);await shot('dashboard-incomplete');
  await control('Toggle empty').click();await page.getByText('Hôm nay chưa có chuyến xe.',{exact:true}).waitFor();assert.deepEqual(await page.locator('.dispatch-totals dd').allTextContents(),['0','0','0','0']);await shot('dashboard-empty');await control('Toggle empty').click();await page.locator('.dispatch-highlight').waitFor();
  await control('Operator bookings').click();await page.locator('.operator-booking-code').first().waitFor();assert.equal(await page.locator('.booking-zones th').count(),3);await shot('bookings');
  await page.getByLabel('Tìm mã đặt vé, tên, điện thoại, email',{exact:true}).fill('101');await page.waitForURL(u=>u.searchParams.get('q')==='101');assert.equal(new URL(page.url()).searchParams.get('q'),'101');await page.getByRole('button',{name:'Xóa bộ lọc',exact:true}).click();await page.waitForURL(u=>!u.search);assert.equal(new URL(page.url()).search,'');
  await page.locator('.booking-identity .row-secondary summary').first().focus();await page.keyboard.press('Enter');assert.equal(await page.locator('.booking-identity details').first().getAttribute('open'),'');await page.locator('.booking-identity details').first().getByText('fixture@example.invalid',{exact:true}).waitFor();
  await page.locator('.operator-booking-code').first().click();await page.locator('.booking-dossier').waitFor();await shot('booking-detail');
  await page.locator('.dossier-payment-history summary').focus();await page.keyboard.press('Enter');await page.locator('.dossier-payment-row').waitFor();await page.locator('.dossier-internal summary').click();await page.locator('.dossier-seat summary').first().click();await shot('booking-detail-expanded');
  await control('Operator passengers').click();await page.locator('.manifest-cleanup .operator-passenger').first().waitFor();assert.equal(await page.locator('.manifest-identity > small').first().textContent(),'Liên hệ đặt vé');await shot('manifest');
  const filter=page.locator('.operator-manifest-search input');await filter.fill('A01');assert.equal(await page.locator('.operator-passenger').count(),1);await filter.fill('no-match-xyz');await page.getByRole('heading',{name:'Không có hành khách phù hợp tìm kiếm'}).waitFor();await filter.fill('');assert.equal(await page.locator('.operator-passenger').count(),2);
  assert.deepEqual(errors,[]);results.push({width,passed:true,noPageOverflow:true,noPageErrors:true,realMetricScope:true,incompleteAndEmpty:true,filters:true,keyboardDetails:true,truthfulContact:true});await page.close();
 }
 writeFileSync(new URL('results.json',root),JSON.stringify(results,null,2));console.log(JSON.stringify(results,null,2));
}finally{await browser.close();}
