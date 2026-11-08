const assert=require('node:assert/strict');
const {randomUUID}=require('node:crypto');
const {origin,verifyM306}=require('./helpers/m306-environment.cjs');
const {loadPlaywright}=require('./m30-4b-live.cjs');

async function main(){
  assert(process.argv.includes('--allow-live-mutations'));
  const {sql}=await verifyM306(origin);
  async function api(path,token,body,expectedCode){
    const r=await fetch(origin+'/api/v1'+path,{method:body?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body?JSON.stringify(body):undefined});
    const json=await r.json();
    if(expectedCode){assert(!r.ok,expectedCode+' expected rejection');assert.equal(json.code,expectedCode);}
    else assert(r.ok,`${r.status}: ${json.code}`);
    return json.data;
  }
  const admin=(await api('/auth/login',null,{email:'operator.admin@anphu-demo.example',password:'DemoOperator!2026'})).accessToken;
  const owner=await api('/operator/marketplace-profile',admin);
  const profile=await api('/public/operators/'+owner.id+'/profile');
  const trip=profile.trips.find(t=>t.availableSeats>=5);assert(trip);
  const q=new URLSearchParams({pickupLocationId:trip.pickup.locationId,dropoffLocationId:trip.dropoff.locationId});
  const seats=(await api('/trips/'+trip.tripId+'/seats?'+q)).seats.filter(s=>s.available);
  async function customer(seat){
    const email='recovery.'+randomUUID()+'@example.test',password=randomUUID()+'Aa!';
    await api('/auth/register',null,{email,password,fullName:'M30 Test',phone:'0900000306'});
    const tokens=await api('/auth/login',null,{email,password});
    const hold=await api('/seat-holds',tokens.accessToken,{tripId:trip.tripId,...Object.fromEntries(q),tripSeatIds:[seat.tripSeatId]});
    return {tokens,hold,email};
  }
  const a=await customer(seats.at(-1)),b=await customer(seats.at(-2));
  const newOffer=async(minimumSpend,totalLimit)=>{
    const code='QA'+randomUUID().replaceAll('-','').slice(0,16).toUpperCase();
    const v=await api('/operator/vouchers',admin,{code,name:'M30 test',discountType:'PERCENT',discountValue:10,minimumSpend,totalLimit,startsAt:new Date(Date.now()-60000),endsAt:new Date(Date.now()+86400000)});
    await api('/operator/vouchers/'+v.id+'/activate',admin,{});
    return {code,id:v.id};
  };
  const limited=await newOffer(0,1),exp=await newOffer(0,2),minimum=await newOffer(trip.price+1,2);
  assert(Number.isSafeInteger(exp.id));sql(`UPDATE operator_vouchers SET starts_at=UTC_TIMESTAMP()-INTERVAL 2 DAY, ends_at=UTC_TIMESTAMP()-INTERVAL 1 DAY WHERE id=${exp.id} AND code='${exp.code}'; SELECT ROW_COUNT();`);
  const quote=(customer,code,expected)=>api('/bookings/voucher-quote',customer.tokens.accessToken,{holdToken:customer.hold.holdToken,voucherCode:code},expected);
  await quote(a,exp.code,'VOUCHER_EXPIRED');await quote(a,minimum.code,'VOUCHER_MINIMUM_SPEND');
  const options=await api('/bookings/voucher-options',a.tokens.accessToken,{holdToken:a.hold.holdToken});
  assert(!options.options.some(v=>v.code===limited.code));
  const price=await quote(a,limited.code);
  const booking=await api('/bookings',a.tokens.accessToken,{holdToken:a.hold.holdToken,voucherCode:limited.code,contactName:'M30 Test',contactEmail:a.email,contactPhone:'0900000306'});
  assert.equal(booking.totalAmount,price.totalAmount);
  await quote(b,limited.code,'VOUCHER_LIMIT_REACHED');
  console.log('PASS expired/minimum-spend/quota; private code excluded from discovery but manual quote permitted');
  const browser=await loadPlaywright().chromium.launch({channel:'msedge'});
  try {
    const p=await browser.newPage({viewport:{width:1440,height:900}});
    const cdp=await p.context().newCDPSession(p);await cdp.send('Network.enable');
    await p.goto(origin);await p.evaluate(tokens=>sessionStorage.setItem('busgo.auth',JSON.stringify(tokens)),a.tokens);
    await p.goto(origin+'/payment?bookingId='+booking.bookingId);
    const confirm=p.getByRole('button',{name:'Xác nhận thanh toán mô phỏng'});
    await confirm.waitFor();
    await cdp.send('Network.setBlockedURLs',{urls:['*/payments/mock-confirm']});
    await confirm.click();await p.getByRole('alert').waitFor();
    assert.equal((await api('/bookings/'+booking.bookingId,a.tokens.accessToken)).status,'PENDING');
    await cdp.send('Network.setBlockedURLs',{urls:[]});await confirm.click();
    await p.waitForURL('**/booking-success?bookingId='+booking.bookingId);await p.locator('.ticket-stub svg').waitFor();
    await p.goto(origin+'/my-bookings');await p.getByText(booking.bookingCode,{exact:true}).first().waitFor();
    console.log('PASS real network failure on mock-confirm, booking stayed pending, retry issued QR and booking history');
    const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date(trip.pickup.departureTime));
    const search='/search?'+q+'&'+new URLSearchParams({pickupLabel:trip.pickup.name,dropoffLabel:trip.dropoff.name,departureDate:date});
    await cdp.send('Network.setBlockedURLs',{urls:['*/trips/search*']});
    await p.goto(origin+search);await p.getByRole('alert').getByRole('button',{name:'Thử lại'}).waitFor();
    await cdp.send('Network.setBlockedURLs',{urls:[]});await p.getByRole('alert').getByRole('button',{name:'Thử lại'}).click();await p.locator('.trip-card').first().waitFor();
    await p.getByLabel('Sắp xếp').selectOption('PRICE_ASC');await p.waitForURL('**sort=PRICE_ASC**');
    await p.getByLabel('Giá tối đa').fill('1');await p.getByRole('button',{name:'Áp dụng bộ lọc'}).click();await p.getByRole('heading',{name:'Không có chuyến khớp bộ lọc'}).waitFor();
    await p.locator('.search-editor > summary').click();
    const pickup=p.getByRole('combobox',{name:'Điểm đón',exact:true}),dropoff=p.getByRole('combobox',{name:'Điểm trả',exact:true});
    const previous=await pickup.inputValue(),next=await dropoff.inputValue();
    await p.getByRole('button',{name:'Đổi điểm đón và điểm trả'}).click();
    assert.equal(await pickup.inputValue(),next);assert.equal(await dropoff.inputValue(),previous);
    console.log('PASS live search error/retry, price sort/filter, empty result and swapped journey');
  } finally {await browser.close();}
}
main().catch(e=>{console.error(e.stack);process.exitCode=1});
