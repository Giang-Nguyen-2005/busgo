// Real React + Edge against synthetic API responses. Live evidence uses m30-4b-live.cjs separately.
const fs=require('node:fs'),path=require('node:path'),Module=require('node:module');
const filename=path.resolve('frontend/tests/m29-customer-browser.cjs');
const out=path.resolve('.tools/m30-4b/browser');fs.mkdirSync(out,{recursive:true});
let source=fs.readFileSync(filename,'utf8').replace("const out = path.resolve(__dirname, '../../.tools/m29-browser/m29-2');",`const out=${JSON.stringify(out)};`);
const fixture=`
 if(state.publicMode && p==='/users/me')return ok({data:{...user,id:state.publicActorId||1}});
 if(state.publicMode && state.publicActorId===2 && p.startsWith('/seat-holds/'))return route.fulfill({status:404,contentType:'application/json',body:JSON.stringify({code:'SEAT_HOLD_NOT_FOUND',message:'Không tìm thấy lượt giữ chỗ.'})});
 if(state.publicMode && (p==='/public/promotions'||p==='/trips/7/promotions')){
  if(state.promoError)return fail('TEST_ERROR');
  const page=Number(url.searchParams.get('page')||0),size=Number(url.searchParams.get('size')||6);
  const list=state.promoEmpty||state.promoHidden?[]:Array.from({length:7},(_,i)=>({id:i+1,operator,route:{...trip.route,pickupLocationId:1,dropoffLocationId:2},code:'PUBLIC'+(i+1),name:'Ưu đãi công khai '+(i+1),description:'Nội dung fixture, không phải ưu đãi thật.',discountType:'FIXED_AMOUNT',discountValue:50000,maxDiscount:50000,minimumSpend:0,startsAt:'2026-01-01T00:00:00Z',endsAt:state.promoExpired?'2026-01-02T00:00:00Z':'2030-01-01T00:00:00Z',customerLimit:1,eligibility:p==='/public/promotions'?'REQUIRES_CONTEXT':'CONDITIONAL'}));
  return ok({data:list.slice(page*size,(page+1)*size),pagination:{page,size,totalElements:list.length,totalPages:Math.ceil(list.length/size)}});
 }
 if(state.publicMode && p==='/trips/search')return ok(paged([{...trip,hasPublicPromotions:!state.promoHidden}]));
 if(state.publicMode && p==='/bookings/voucher-options')return ok({data:{grossAmount:hold().totalPrice,seatCount:state.holdIds.length,options:[{id:1,code:'PUBLIC1',name:'Ưu đãi công khai 1',description:null,discountType:'FIXED_AMOUNT',discountValue:50000,maxDiscount:50000,minimumSpend:0,startsAt:'2026-01-01T00:00:00Z',endsAt:'2030-01-01T00:00:00Z',customerLimit:1,status:'AVAILABLE',reasonCode:null,message:null,quote:{discountAmount:50000,totalAmount:hold().totalPrice-50000}}],pagination:{page:0,size:5,totalElements:1,totalPages:1}}});
 if(state.publicMode && p==='/bookings/voucher-quote')return ok({data:{grossAmount:hold().totalPrice,discountAmount:50000,totalAmount:hold().totalPrice-50000,voucherCode:req.postDataJSON().voucherCode,eligible:true}});
`;
source=source.replace(" if (p === '/public/promotions'",fixture+"\n if (p === '/public/promotions'");
const checks=`
 await p.setViewportSize({width:360,height:900});
 state.publicMode=true;state.quote=false;state.publicActorId=1;
 await p.goto(origin+'/');await p.evaluate(()=>{sessionStorage.removeItem('busgo.auth');sessionStorage.removeItem('busgo.hold');});await p.reload();await ready(p);
 state.promoEmpty=true;await p.getByRole('button',{name:'Làm mới ưu đãi công khai'}).click();await p.getByText(/Chưa có ưu đãi công khai trong phạm vi này/).waitFor();assert.equal(await p.locator('.public-promotion').count(),0);await capture(p,'public-empty-360');
 state.promoEmpty=false;await p.getByRole('button',{name:'Làm mới ưu đãi công khai'}).click();await p.locator('.public-promotion').first().waitFor();
 for(const width of [1440,834,390,360]){await p.setViewportSize({width,height:900});await capture(p,'public-home-'+width);assert(!(await p.locator('body').innerText()).includes('PRIVATE-CODE-DO-NOT-LEAK'));}
 await p.locator('.public-promotions').getByRole('button',{name:'Sau',exact:true}).click();await p.locator('.public-promotion').getByText('PUBLIC7',{exact:true}).waitFor();await p.getByRole('button',{name:'Làm mới ưu đãi công khai'}).click();await p.locator('.public-promotion').filter({hasText:'PUBLIC1'}).waitFor();
 check('M30.4B Home zero/many public promotions, pagination and four widths, no private code or invented net price');
 const first=p.locator('.public-promotion').filter({has:p.getByText('PUBLIC1',{exact:true})});await first.getByRole('link',{name:'Tìm chuyến phù hợp'}).click();await p.waitForURL('**/search?**');await p.getByRole('heading',{name:'Bạn muốn đi đâu?',exact:true}).waitFor();assert.equal(await p.getByLabel('Ngày đi',{exact:true}).inputValue(),'');
 await p.getByLabel('Ngày đi',{exact:true}).fill('2027-01-15');await p.getByRole('button',{name:'Tìm chuyến',exact:true}).click();await p.getByRole('link',{name:'Có ưu đãi — xem điều kiện',exact:true}).waitFor();
 const holdsBefore=state.calls.filter(c=>c.p==='/seat-holds').length,quotesBefore=state.calls.filter(c=>c.p==='/bookings/voucher-quote').length;
 await p.getByRole('link',{name:'Có ưu đãi — xem điều kiện',exact:true}).click();await p.waitForURL('**/trips/7?**');await p.locator('#trip-promotions .public-promotion').first().waitFor();
 assert(await p.locator('#trip-promotions').evaluate(e=>e.open));
 await p.getByRole('button',{name:/^Chỗ A01,/}).click();
 await context.grantPermissions(['clipboard-read','clipboard-write']);
 await p.getByRole('button',{name:'Sao chép mã PUBLIC1',exact:true}).click();await p.getByText(/Đã sao chép PUBLIC1/).waitFor();assert.equal(await p.evaluate(()=>navigator.clipboard.readText()),'PUBLIC1');assert.equal(await p.locator('.seat.selected').count(),1);
 for(const width of [1440,834,390,360]){await p.setViewportSize({width,height:900});await capture(p,'public-trip-'+width);}
 assert.equal(state.calls.filter(c=>c.p==='/seat-holds').length,holdsBefore);assert.equal(state.calls.filter(c=>c.p==='/bookings/voucher-quote').length,quotesBefore);
 check('M30.4B Home -> date -> Search badge -> Trip terms/copy; no quote per card, no hold/reservation from viewing or copying; selection retained');
 await p.getByRole('button',{name:'Tiếp tục',exact:true}).click();await p.waitForURL('**/login?**');
 await p.getByLabel('Email',{exact:true}).fill(user.email);await p.getByLabel('Mật khẩu',{exact:true}).fill('fixture-password');await p.getByRole('button',{name:'Đăng nhập',exact:true}).click();await p.waitForURL('**/trips/7?**');await p.getByText(/Đã khôi phục ghế/).waitFor();assert.equal(await p.locator('.seat.selected').count(),1);
 await p.getByRole('button',{name:'Tiếp tục',exact:true}).click();await p.waitForURL('**/booking');await ready(p);assert.equal(await p.getByLabel('Mã ưu đãi nhà xe').inputValue(),'');
 await p.locator('.checkout-voucher-options summary').click();await p.getByRole('button',{name:'Chọn mã PUBLIC1',exact:true}).click();await p.getByText(/Ưu đãi PUBLIC1:/).waitFor();assert.equal(state.calls.filter(c=>c.p==='/bookings/voucher-quote').length,quotesBefore+1);await capture(p,'public-checkout-360');
 state.publicActorId=2;await p.reload();await ready(p);await p.getByRole('alert').waitFor();assert.equal(await p.locator('.voucher-option').count(),0);
 check('M30.4B login return restores seats, checkout never auto-applies copied code; explicit select uses options/quote; account replacement cannot reuse old hold/voucher');
 state.publicActorId=1;await p.goto(origin+'/');await ready(p);state.promoHidden=true;await p.getByRole('button',{name:'Làm mới ưu đãi công khai'}).click();await p.getByText(/Chưa có ưu đãi công khai/).waitFor();assert.equal(await p.locator('.public-promotion').count(),0);
 state.promoHidden=false;state.promoExpired=true;await p.getByRole('button',{name:'Làm mới ưu đãi công khai'}).click();await ready(p);assert.equal(await p.locator('.public-promotion').count(),0);
 state.promoExpired=false;state.promoError=true;await p.getByRole('button',{name:'Làm mới ưu đãi công khai'}).click();await p.locator('.public-promotions').getByRole('alert').waitFor();state.promoError=false;await p.locator('.public-promotions').getByRole('button',{name:'Thử lại',exact:true}).click();await p.locator('.public-promotion').first().waitFor();
 check('M30.4B refresh removes hidden/paused response and expired public cache; public error/retry leaves booking journey intact');
`;
if(process.argv.includes('--public-only'))source=source.slice(0,source.indexOf(' for (const width of (process.argv'))+source.slice(source.indexOf(' assert.equal(report.consoleErrors.length,0);'));
source=source.replace(' assert.equal(report.consoleErrors.length,0);',checks+'\n assert.equal(report.consoleErrors.length,0);');
const mod=new Module(filename,module);mod.filename=filename;mod.paths=Module._nodeModulePaths(path.dirname(filename));mod._compile(source,filename);
