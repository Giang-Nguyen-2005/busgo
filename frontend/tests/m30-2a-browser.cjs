// Extend the M29 customer regression without changing its assertions or fixtures.
// Every API request is intercepted: this suite never calls the backend.
const fs = require('node:fs'), path = require('node:path'), Module = require('node:module');
const filename = path.resolve('frontend/tests/m29-customer-browser.cjs');
const out = path.resolve('.tools/m30-2a');
fs.mkdirSync(out, {recursive:true});
let source = fs.readFileSync(filename, 'utf8');
source = source.replace("const out = path.resolve(__dirname, '../../.tools/m29-browser/m29-2');", `const out = ${JSON.stringify(out)};`);
source = source.replace("if (p === '/users/me')", `
 if (p === '/public/operators/1/profile') return ok({data:{operator,routes:[{...trip.route,pickupLocationId:1,dropoffLocationId:2}],busTypes:[trip.busType],trips:[trip]}});
 if (p === '/public/operators/1/reviews') return ok(paged([]));
 if (p === '/bookings/101/review-eligibility') return ok({data:{eligible:false,review:null}});
 if (p === '/bookings/101/partial-cancellations' || p === '/bookings/101/modifications') return ok({data:[]});
 if (p === '/bookings/101/partial-cancellation-eligibility') return ok({data:{eligibility:{allowed:!state.blocked,message:'Không đủ điều kiện hủy một phần'},items:[]}});
 if (p === '/bookings/101/modification-eligibility') return ok({data:{seatChange:{allowed:!state.blocked,message:'Không thể đổi ghế'},tripChange:{allowed:!state.blocked,message:'Không thể đổi chuyến'}}});
 if (p === '/bookings/101/recovery') return ok({data:{status:state.status,eligible:!state.blocked,paid:true,refunds:[],tickets:[],history:[],ineligibleReason:'CUSTOMER_CANCELLATION_CUTOFF'}});
 if (p === '/users/me')`);
source = source.replace("if (p === '/seat-holds' && method === 'POST') {", "if (p === '/seat-holds' && method === 'POST') { if (state.holdDelay) await new Promise(r=>setTimeout(r,state.holdDelay));");
const extra = `
 // New M30.2A checks, running the real app against synthetic fixtures.
 await p.goto(origin+'/'); await ready(p);
 await p.evaluate(()=>{sessionStorage.removeItem('busgo.auth');sessionStorage.removeItem('busgo.hold');}); await p.reload(); await ready(p);
 const callsBefore = state.calls.filter(c=>c.p==='/trips/search').length;
 await p.locator('.discovery-route').first().click(); await p.waitForURL('**/search?**'); await p.getByRole('heading',{name:'Bạn muốn đi đâu?',exact:true}).waitFor(); await ready(p);
 assert.equal(new URL(p.url()).searchParams.get('departureDate'),null);
 assert.equal(await p.getByLabel('Ngày đi',{exact:true}).inputValue(),'');
 assert.equal(new URL(p.url()).searchParams.get('pickupLocationId'),'1');
 assert.equal(await p.getByRole('combobox',{name:'Điểm đón',exact:true}).inputValue(),pickup.name);
 assert.equal(state.calls.filter(c=>c.p==='/trips/search').length,callsBefore);
 await p.getByRole('button',{name:'Tìm chuyến',exact:true}).click();
 await p.getByText('Chọn ngày đi.',{exact:true}).waitFor();
 await p.getByLabel('Ngày đi',{exact:true}).fill('2027-01-15');
 await p.getByRole('button',{name:'Tìm chuyến',exact:true}).click(); await p.waitForURL('**departureDate=2027-01-15**'); await ready(p);
 const chosenSearch = new URL(p.url()).pathname + new URL(p.url()).search;
 await p.getByLabel('Sắp xếp').selectOption('PRICE_ASC'); await ready(p);
 const sortedSearch = new URL(p.url()).pathname + new URL(p.url()).search;
 await p.goBack(); await ready(p); assert.equal(new URL(p.url()).pathname+new URL(p.url()).search,chosenSearch);
 await p.goForward(); await ready(p); assert.equal(new URL(p.url()).pathname+new URL(p.url()).search,sortedSearch);
 check('Guest discovery requires chosen date; route names/IDs, validation and search history retained');
 await p.locator('.trip-meta a').first().click(); await p.waitForURL('**/operators/1?**'); await ready(p);
 await p.getByRole('link',{name:'← Kết quả tìm chuyến',exact:true}).click(); await p.waitForURL(origin+sortedSearch); await ready(p);
 assert.equal(new URL(p.url()).pathname+new URL(p.url()).search,sortedSearch);
 check('Search card profile return preserves sort and journey URL');
 await p.goto(origin+tripUrl+'&search='+encodeURIComponent(sortedSearch)); await ready(p);
 const tripReturn = new URL(p.url()).pathname+new URL(p.url()).search;
 const holdsBefore = state.calls.filter(c=>c.p==='/seat-holds').length;
 await p.getByRole('button',{name:/^Chỗ A01,/}).click();
 await p.getByRole('link',{name:'Thông tin và đánh giá nhà xe'}).click(); await p.waitForURL('**/operators/1?**'); await ready(p);
 const profileURL = p.url();
 await p.getByRole('link',{name:'Chọn ngày tìm chuyến',exact:true}).click(); await p.waitForURL('**/search?**'); await p.getByRole('heading',{name:'Bạn muốn đi đâu?',exact:true}).waitFor(); await ready(p);
 assert.equal(new URL(p.url()).searchParams.get('operatorId'),'1');
 assert.equal(await p.getByLabel('Ngày đi',{exact:true}).inputValue(),'');
 await p.goto(profileURL); await ready(p);
 await p.getByRole('link',{name:'← Quay lại chọn ghế',exact:true}).click(); await p.waitForURL(origin+tripReturn); await p.getByText(/Đã khôi phục ghế/).waitFor(); await ready(p);
 assert.equal(new URL(p.url()).pathname+new URL(p.url()).search,tripReturn);
 assert.equal(await p.locator('.seat.selected').count(),1);
 assert.equal(state.calls.filter(c=>c.p==='/seat-holds').length,holdsBefore);
 await p.getByText(/Ghế đang chọn chưa được giữ chỗ/).waitFor();
 await p.getByText(/Bạn cần đăng nhập hoặc đăng ký/).waitFor();
 check('Trip profile return revalidates draft; profile route CTA scoped to operator; selection never creates hold');
 for (const width of [1440,834,390,360]) {
  await p.setViewportSize({width,height:844}); await p.goto(origin+tripReturn); await ready(p);
  const cta = p.getByRole('button',{name:'Tiếp tục',exact:true});
  await cta.scrollIntoViewIfNeeded();
  await p.getByText(/Bạn cần đăng nhập hoặc đăng ký/).waitFor();
  await capture(p,'m30-guest-seats-'+width);
  await p.getByRole('link',{name:'Thông tin và đánh giá nhà xe'}).click(); await p.waitForURL('**/operators/1?**');
  await p.getByRole('heading',{name:'Tuyến vận hành',exact:true}).waitFor();
  await capture(p,'m30-profile-'+width);
 }
 await p.goto(origin+tripReturn); await ready(p); await p.getByRole('button',{name:/^Chỗ A01,/}).click();

 // Inventory changes while logging in: unavailable selected seats must not return.
 await p.getByRole('button',{name:'Tiếp tục',exact:true}).click(); await p.waitForURL('**/login?**');
 await p.getByRole('link',{name:'Đăng ký ngay',exact:true}).click(); await p.waitForURL('**/register?**');
 assert.equal(new URL(p.url()).searchParams.get('returnTo'),tripReturn);
 await p.locator('#main').getByRole('link',{name:'Đăng nhập',exact:true}).click(); await p.waitForURL('**/login?**');
 state.unavailable=[1];
 await p.getByLabel('Email',{exact:true}).fill(user.email); await p.getByLabel('Mật khẩu',{exact:true}).fill('fixture-password');
 await p.getByRole('button',{name:'Đăng nhập',exact:true}).click(); await p.waitForURL('**/trips/7?**'); await ready(p);
 assert.equal(await p.locator('.seat.selected').count(),0); assert.equal(state.calls.filter(c=>c.p==='/seat-holds').length,holdsBefore);
 state.unavailable=[]; await p.reload(); await ready(p);
 check('Login/register links preserve exact returnTo; changed availability prevents restoring unavailable seat or holding automatically');
 for (const width of [1440,834,390,360]) {
  await p.setViewportSize({width,height:900}); await p.goto(origin+tripReturn); await ready(p);
  await p.getByRole('button',{name:/^Chỗ A01,/}).click();
  await p.getByRole('button',{name:'Tiếp tục',exact:true}).scrollIntoViewIfNeeded();
  const cta = p.getByRole('button',{name:'Tiếp tục',exact:true}); await cta.click({trial:true});
  assert(await cta.evaluate(e=>{const r=e.getBoundingClientRect();return r.top>=-1 && r.bottom<=innerHeight+1 && e.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2));}));
  await capture(p,'m30-seats-'+width);
  await p.goto(origin+'/my-bookings/101'); await ready(p);
  await p.getByRole('heading',{name:'Hủy toàn bộ đặt vé',exact:true}).waitFor();
  await p.getByRole('button',{name:'Hủy một phần',exact:true}).waitFor();
  await p.getByRole('link',{name:'Đổi ghế',exact:true}).waitFor();
  assert.equal(await p.getByRole('link',{name:'Hỗ trợ theo đặt vé'}).getAttribute('href'),'/support/new?bookingId=101');
  await p.getByRole('link',{name:'Điều kiện hủy một phần',exact:true}).click();
  assert.equal(new URL(p.url()).hash,'#booking-partial');
  await capture(p,'m30-booking-'+width);
 }
 state.blocked=true; await p.reload(); await ready(p);
 assert.equal(await p.getByRole('button',{name:'Hủy một phần',exact:true}).count(),0);
 assert.equal(await p.getByRole('link',{name:'Đổi ghế',exact:true}).count(),0);
 assert.equal(await p.getByRole('button',{name:/Huỷ và hoàn tiền/}).count(),0);
 check('Booking navigation and full/partial copy at all viewports; ineligible backend fixtures hide mutation actions; sticky CTA unobscured and no overflow');
 state.blocked=false; await p.goto(origin+tripReturn); await ready(p);
 await p.evaluate(()=>sessionStorage.removeItem('busgo.hold'));
 await p.getByRole('button',{name:/^Chỗ A01,/}).click();
 state.conflict=true; await p.getByRole('button',{name:'Tiếp tục',exact:true}).click(); await p.getByRole('alert').waitFor();
 assert.equal(await p.evaluate(()=>sessionStorage.getItem('busgo.hold')),null);
 assert(new URL(p.url()).pathname.startsWith('/trips/'));
 state.conflict=false; state.holdDelay=800;
 await p.getByRole('button',{name:/^Chỗ A01,/}).click();
 await p.getByRole('button',{name:'Tiếp tục',exact:true}).click();
 await p.getByRole('button',{name:'Đang giữ chỗ…',exact:true}).waitFor();
 assert.equal(await p.evaluate(()=>sessionStorage.getItem('busgo.hold')),null);
 assert(new URL(p.url()).pathname.startsWith('/trips/'));
 await p.waitForURL('**/booking'); await ready(p);
 assert.equal(await p.evaluate(()=>JSON.parse(sessionStorage.getItem('busgo.hold')).holdToken),'fixture-hold');
 check('Failed or pending hold never saves reservation; only successful fixture server response opens checkout');

`;
if (process.argv.includes('--m30-only')) source = source.slice(0, source.indexOf(' for (const width of (process.argv')) + source.slice(source.indexOf(' assert.equal(report.consoleErrors.length,0);'));
source = source.replace(" assert.equal(report.consoleErrors.length,0);", extra+"\n assert.equal(report.consoleErrors.length,0);");
const mod = new Module(filename, module); mod.filename=filename; mod.paths=Module._nodeModulePaths(path.dirname(filename)); mod._compile(source, filename);
