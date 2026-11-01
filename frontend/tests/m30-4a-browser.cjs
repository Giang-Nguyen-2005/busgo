// Real React + Edge; synthetic API responses only, never contacts a backend.
const fs=require('node:fs'),path=require('node:path'),Module=require('node:module');
if(process.argv.includes('--previous-journeys')) {
 const previous=path.resolve('frontend/tests/m30-2b-browser.cjs');
 const code=fs.readFileSync(previous,'utf8').replaceAll('.tools/m30-2b/browser','.tools/m30-4a/previous-journeys');
 process.argv.push('--m30-only');
 const mod=new Module(previous,module);mod.filename=previous;mod.paths=Module._nodeModulePaths(path.dirname(previous));mod._compile(code,previous);
} else {
const filename=path.resolve('frontend/tests/m29-customer-browser.cjs');
const out=path.resolve('.tools/m30-4a/browser');fs.mkdirSync(out,{recursive:true});
let source=fs.readFileSync(filename,'utf8').replace("const out = path.resolve(__dirname, '../../.tools/m29-browser/m29-2');",`const out=${JSON.stringify(out)};`);
const fixtures=`
 if(state.voucherMode && p==='/users/me') return ok({data:{...user,id:state.actorId||1,roles:[state.actorRole||'CUSTOMER']}});
 if(state.voucherMode && state.actorId===2 && p.startsWith('/seat-holds/')) return route.fulfill({status:404,contentType:'application/json',body:JSON.stringify({code:'SEAT_HOLD_NOT_FOUND',message:'Không tìm thấy lượt giữ chỗ.'})});
 if(state.voucherMode && p==='/operator/vouchers') return ok(paged([{id:1,code:'PUBLIC10',name:'Ưu đãi công khai fixture',description:'Nội dung được công khai',customer_visible:!!state.visible,state:'ACTIVE',effective_state:'ACTIVE',editable:false,discount_type:'PERCENT',discount_value:10,max_discount:100000,minimum_spend:0,starts_at:'2026-01-01T00:00:00Z',ends_at:'2030-01-01T00:00:00Z',reserved:1,redeemed:1}]));
 if(state.voucherMode && p==='/operator/vouchers/1/visibility') {state.visible=req.postDataJSON().customerVisible;return ok({data:{customer_visible:state.visible}});}
 if(state.voucherMode && p==='/bookings/voucher-options') {
  if(state.optionsError)return fail('TEST_OPTIONS_ERROR');
  const option=(id,code,status='AVAILABLE')=>({id,code,name:'Ưu đãi '+code,description:'Ưu đãi công khai từ nhà xe fixture',discountType:'PERCENT',discountValue:10,maxDiscount:100000,minimumSpend:status==='CONDITION_NOT_MET'?900000:0,startsAt:'2026-01-01T00:00:00Z',endsAt:'2030-01-01T00:00:00Z',customerLimit:1,status,reasonCode:status==='AVAILABLE'?null:'VOUCHER_LIMIT_REACHED',message:status==='CONDITION_NOT_MET'?'Chưa đạt giá trị tối thiểu':status==='UNAVAILABLE'?'Bạn đã đạt giới hạn sử dụng':null,quote:status==='AVAILABLE'?{discountAmount:70000,totalAmount:630000}:null});
  const page=req.postDataJSON().page||0;
  return ok({data:{grossAmount:700000,seatCount:2,options:state.noOffers?[]:page?[option(6,'PUBLIC20')]:[option(1,'PUBLIC10'),option(2,'MIN900K','CONDITION_NOT_MET'),option(3,'USED','UNAVAILABLE'),option(4,'PUBLICA'),option(5,'PUBLICB')],pagination:{page,size:5,totalElements:state.noOffers?0:6,totalPages:state.noOffers?0:2}}});
 }
 if(state.voucherMode && p==='/bookings/voucher-quote') {
  if(state.quoteDelay)await new Promise(r=>setTimeout(r,state.quoteDelay));
  if(state.quoteFailure)return fail('VOUCHER_EXPIRED');
  const code=req.postDataJSON().voucherCode;
  return ok({data:{grossAmount:700000,discountAmount:code==='PRIVATE50'?50000:70000,totalAmount:code==='PRIVATE50'?650000:630000,voucherCode:code,eligible:true}});
 }
 if(state.voucherMode && p==='/bookings' && state.createVoucherFailure)return fail('VOUCHER_EXPIRED');
`;
source=source.replace(" if (p === '/users/me')",fixtures+"\n if (p === '/users/me')");
const checks=`
 state.voucherMode=true;state.status='PENDING';state.holdIds=[1,2];state.actorId=1;state.actorRole='CUSTOMER';
 await p.goto(origin+'/booking');await ready(p);await p.evaluate(({tokens,hold})=>{sessionStorage.setItem('busgo.auth',JSON.stringify(tokens));sessionStorage.setItem('busgo.hold',JSON.stringify(hold));},{tokens,hold:hold()});
 for(const width of [1440,834,390,360]){
  await p.setViewportSize({width,height:900});await p.goto(origin+'/booking');await ready(p);
  await p.locator('.checkout-voucher-options summary').click();
  await p.getByRole('button',{name:'Chọn mã PUBLIC10',exact:true}).waitFor();
  assert(await p.getByRole('button',{name:'Chọn mã MIN900K',exact:true}).isDisabled());
  assert(await p.getByRole('button',{name:'Chọn mã USED',exact:true}).isDisabled());
  await capture(p,'voucher-options-'+width);
  state.quoteDelay=600;
  await p.getByRole('button',{name:'Chọn mã PUBLIC10',exact:true}).click();
  assert(await p.getByRole('button',{name:'Tiếp tục đến thanh toán'}).isDisabled());
  const bookingsBeforeQuote=state.calls.filter(c=>c.p==='/bookings' && c.method==='POST').length;
  await p.locator('#booking-contact').evaluate(e=>e.requestSubmit());
  await p.getByText(/Thanh toán dự kiến: 630\.000/).waitFor();
  assert.equal(state.calls.filter(c=>c.p==='/bookings' && c.method==='POST').length,bookingsBeforeQuote);
  assert.equal(await p.getByLabel('Mã ưu đãi nhà xe').inputValue(),'PUBLIC10');
  await capture(p,'voucher-applied-'+width);
  await p.getByRole('button',{name:'Bỏ ưu đãi',exact:true}).click();
  assert(!(await p.locator('.price-summary').innerText()).includes('630.000'));
  await p.getByRole('button',{name:'Tiếp tục đến thanh toán'}).click({trial:true});
  state.quoteDelay=0;
 }
 check('M30.4A options, eligibility labels and selection at 1440/834/390/360; unresolved quote blocks submit; remove restores gross; no overflow');
 await p.getByRole('button',{name:'Ưu đãi tiếp',exact:true}).click();
 await p.getByRole('button',{name:'Chọn mã PUBLIC20',exact:true}).waitFor();
 await p.getByRole('button',{name:'Ưu đãi trước',exact:true}).click();await p.getByRole('button',{name:'Chọn mã PUBLIC10',exact:true}).waitFor();
 state.quoteFailure=true;await p.getByRole('button',{name:'Chọn mã PUBLIC10',exact:true}).click();await p.getByRole('alert').waitFor();
 assert(await p.getByRole('button',{name:'Tiếp tục đến thanh toán'}).isDisabled());
 assert(!(await p.locator('.price-summary').innerText()).includes('630.000'));
 state.quoteFailure=false;await p.getByRole('button',{name:'Bỏ ưu đãi',exact:true}).click();
 state.noOffers=true;await p.getByRole('button',{name:'Cập nhật danh sách ưu đãi'}).click();await p.getByText(/Không có ưu đãi công khai phù hợp/).waitFor();
 await p.getByLabel('Mã ưu đãi nhà xe').fill('private50');await p.getByRole('button',{name:'Kiểm tra ưu đãi',exact:true}).click();
 await p.getByText(/Thanh toán dự kiến: 650\.000/).waitFor();
 state.createVoucherFailure=true;await p.getByRole('button',{name:'Tiếp tục đến thanh toán'}).click();await p.getByRole('alert').waitFor();
 await p.getByText(/Mã ưu đãi chưa được xác nhận cho đặt vé/).waitFor();
 assert(await p.getByRole('button',{name:'Tiếp tục đến thanh toán'}).isDisabled());
 assert(!(await p.locator('.price-summary').innerText()).includes('650.000'));
 state.createVoucherFailure=false;await p.getByRole('button',{name:'Bỏ ưu đãi',exact:true}).click();assert(await p.getByRole('button',{name:'Tiếp tục đến thanh toán'}).isEnabled());
 check('M30.4A pagination, quote expiry, no-public-offer manual private code and booking rejection require explicit recovery');
 state.noOffers=false;state.optionsError=true;await p.reload();await ready(p);await p.locator('.checkout-voucher-options summary').click();
 await p.locator('.checkout-voucher-options').getByRole('alert').waitFor();state.optionsError=false;await p.locator('.checkout-voucher-options').getByRole('button',{name:'Thử lại',exact:true}).click();await p.getByRole('button',{name:'Chọn mã PUBLIC10',exact:true}).waitFor();
 state.actorId=2;await p.reload();await ready(p);await p.getByRole('alert').waitFor();assert.equal(await p.locator('.voucher-option').count(),0);assert.equal(await p.getByRole('button',{name:'Tiếp tục đến thanh toán'}).count(),0);state.actorId=1;
 await p.evaluate(h=>sessionStorage.setItem('busgo.hold',JSON.stringify(h)),hold());state.expired=true;await p.goto(origin+'/booking');await p.waitForURL('**/trips/7?**');await p.getByText('Thời gian giữ chỗ đã hết. Vui lòng chọn lại ghế.').waitFor();state.expired=false;
 check('M30.4A options error/retry, account replacement hides prior hold/options, expired hold returns to selection');
 for(const role of ['OPERATOR_ADMIN','OPERATOR_STAFF']){
  state.actorRole=role;await p.goto(origin+'/operator/vouchers');await p.reload();await ready(p);await p.getByRole('heading',{name:'Khuyến mãi của nhà xe'}).waitFor();
  for(const width of [1440,834,390,360]){await p.setViewportSize({width,height:900});await capture(p,'voucher-operator-'+role.toLowerCase()+'-'+width);}
  if(role==='OPERATOR_ADMIN'){
   await p.getByRole('button',{name:'Bật công khai',exact:true}).click();await p.getByRole('button',{name:'Xác nhận',exact:true}).click();await p.getByRole('button',{name:'Tắt công khai',exact:true}).waitFor();assert(state.visible);
   await p.getByRole('button',{name:'Tắt công khai',exact:true}).click();await p.getByRole('button',{name:'Xác nhận',exact:true}).click();await p.getByRole('button',{name:'Bật công khai',exact:true}).waitFor();assert(!state.visible);
  }else{assert.equal(await p.getByRole('button',{name:'Bật công khai',exact:true}).count(),0);assert.equal(await p.getByRole('button',{name:'Tắt công khai',exact:true}).count(),0);}
 }
 check('M30.4A operator admin can change visibility independently of used terms; staff remains read-only; all viewport captures fit');
 state.voucherMode=false;
`;
if(process.argv.includes('--voucher-only'))source=source.slice(0,source.indexOf(' for (const width of (process.argv'))+source.slice(source.indexOf(' assert.equal(report.consoleErrors.length,0);'));
source=source.replace(' assert.equal(report.consoleErrors.length,0);',checks+'\n assert.equal(report.consoleErrors.length,0);');
const mod=new Module(filename,module);mod.filename=filename;mod.paths=Module._nodeModulePaths(path.dirname(filename));mod._compile(source,filename);
}
