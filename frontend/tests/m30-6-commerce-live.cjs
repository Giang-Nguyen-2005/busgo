const assert = require('node:assert/strict');
const fs = require('node:fs');
const {randomUUID}=require('node:crypto');
const {origin, verifyM306}=require('./helpers/m306-environment.cjs');
const {loadPlaywright}=require('./m30-4b-live.cjs');

async function request(path, token, body) {
  const response=await fetch(origin+'/api/v1'+path,{
    headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},
    method:body===undefined?'GET':'POST',body:body===undefined?undefined:JSON.stringify(body),redirect:'error'
  });
  return {status:response.status, json:await response.json()};
}
async function ok(path,token,body) {
  const result=await request(path,token,body);
  assert(result.status>=200 && result.status<300,`${path.split('?')[0]} HTTP ${result.status} ${result.json.code||''}`);
  return result.json.data;
}
async function customer(){
  const email='m306.'+randomUUID()+'@example.test', password='A!'+randomUUID();
  await ok('/auth/register',null,{fullName:'Test M30',phone:'0900000306',email,password});
  return {email,tokens:await ok('/auth/login',null,{email,password})};
}
async function main(){
  assert(process.argv.includes('--allow-live-mutations'));
  await verifyM306(origin);
  const checks=[];
  const check=message=>{checks.push(message);console.log('PASS',message);};
  const [a,b]=await Promise.all([customer(),customer()]);
  const trips=await ok('/public/discovery/trips');
  const trip=trips.findLast(t=>t.availableSeats>=8 && new Date(t.pickup.departureTime)>Date.now()+7*3600000);
  assert(trip,'Need bookable demo journey outside customer cutoff');
  const q=new URLSearchParams({pickupLocationId:trip.pickup.locationId,dropoffLocationId:trip.dropoff.locationId});
  const map=()=>ok('/trips/'+trip.tripId+'/seats?'+q);
  const seats=(await map()).seats.filter(s=>s.available).map(s=>s.tripSeatId);
  const form=ids=>({tripId:trip.tripId,pickupLocationId:trip.pickup.locationId,dropoffLocationId:trip.dropoff.locationId,tripSeatIds:ids});
  assert.equal((await request('/seat-holds',a.tokens.accessToken,form(seats.slice(0,6)))).status,400);
  const attempts=await Promise.all([a,b].map(c=>request('/seat-holds',c.tokens.accessToken,form([seats[0]]))));
  assert.deepEqual(attempts.map(r=>r.status).sort(),[201,409]);
  const wi=attempts.findIndex(r=>r.status===201), owner=[a,b][wi], foreign=[a,b][1-wi], token=owner.tokens.accessToken;
  const hold=attempts[wi].json.data;
  const available=async id=>(await map()).seats.find(s=>s.tripSeatId===id).available;
  assert.equal(await available(seats[0]),false);
  assert.equal(hold.totalPrice,trip.price);
  check('Simultaneous holds: one 201 and one 409; six seats rejected; held inventory and price match');
  const expiry=await ok('/seat-holds',foreign.tokens.accessToken,form([seats[2]]));
  const bookingForm={holdToken:hold.holdToken,contactName:'Test customer',contactEmail:owner.email,contactPhone:'0900000306'};
  assert.equal((await request('/bookings',token,{...bookingForm,contactEmail:'invalid'})).status,400);
  const booking=await ok('/bookings',token,bookingForm), path='/bookings/'+booking.bookingId;
  const repeated=await request('/bookings',token,bookingForm);
  if(repeated.status<300) assert.equal(repeated.json.data.bookingId,booking.bookingId);
  const list=await ok('/bookings/me',token);assert.equal(list.filter(item=>item.bookingId===booking.bookingId).length,1);
  assert.equal((await request('/seat-holds',foreign.tokens.accessToken,form([seats[0]]))).status,409);
  assert.equal((await request(path,foreign.tokens.accessToken)).status,404);
  assert.equal((await request(path+'/ticket',token)).status>=400,true);
  await ok(path+'/payments/mock-confirm',token,{});
  const ticket=await ok(path+'/ticket',token);assert(ticket.tickets[0].qrData);
  const repeatPayment=await request(path+'/payments/mock-confirm',token,{});
  assert([200,409].includes(repeatPayment.status));
  assert.deepEqual((await ok(path+'/ticket',token)).tickets,ticket.tickets);
  check('Validation, booking/hold replay, foreign booking denied, mock payment, QR and payment replay');
  const eligibility=await ok(path+'/modification-eligibility',token);
  assert(eligibility.seatChange.allowed,eligibility.seatChange.reasonCode);
  const move={type:'SEAT_CHANGE',targetTripId:trip.tripId,items:[{bookingItemId:eligibility.items[0].bookingItemId,targetSeatId:seats[1]}]};
  await ok(path+'/modification-quotes',token,move);
  const modification=await ok(path+'/modifications',token,move);
  const confirm=path+'/modifications/'+modification.id+'/confirm';
  assert.equal((await ok(confirm,token,{})).status,'COMPLETED');
  assert.equal((await ok(confirm,token,{})).status,'COMPLETED');
  assert.equal(await available(seats[0]),true);assert.equal(await available(seats[1]),false);
  const updatedTicket=await ok(path+'/ticket',token);
  assert(updatedTicket.tickets.some(t=>t.qrData && t.seatCode!==ticket.tickets[0].seatCode));
  check('Changed paid seat, confirmed replay once, verified released and booked seats plus updated ticket');
  assert.equal((await request(path+'/cancel',foreign.tokens.accessToken,{})).status,404);
  assert((await ok(path+'/recovery',token)).eligible);
  const recovery=await ok(path+'/cancel',token,{note:'M30.6 test'});
  assert.equal(recovery.status,'CANCELLED');assert.equal(recovery.refunds.length,1);assert.equal(recovery.refunds[0].amount,booking.totalAmount);
  assert(recovery.tickets.every(t=>t.status==='VOID'));
  assert.equal((await ok(path+'/cancel',token,{})).refunds.length,1);
  assert.equal(await available(seats[1]),true);
  assert.equal((await ok(path+'/modification-eligibility',token)).seatChange.allowed,false);
  assert((await request(path+'/payments/mock-confirm',token,{})).status>=400);
  check('Cancellation refund simulation, void tickets, restored inventory, replay and ineligible changes');
  const browser=await loadPlaywright().chromium.launch({channel:'msedge',headless:true});
  try {
    const p=await browser.newPage();await p.goto(origin);
    await p.evaluate(({tokens,hold})=>{sessionStorage.setItem('busgo.auth',JSON.stringify(tokens));sessionStorage.setItem('busgo.hold',JSON.stringify(hold));},{tokens:foreign.tokens,hold:expiry});
    await p.goto(origin+'/booking');await p.getByRole('button',{name:'Tiếp tục đến thanh toán'}).waitFor();
    await p.reload();await p.getByRole('button',{name:'Tiếp tục đến thanh toán'}).waitFor();
    check('Checkout refresh during valid hold');
    await p.goto(origin+'/'); // Return to checkout after expiry, with the old hold still in tab storage.
    while(Date.now()<Date.parse(expiry.expiresAt)+1000) await new Promise(r=>setTimeout(r,Math.min(10000,Math.max(1,Date.parse(expiry.expiresAt)+1000-Date.now()))));
    await p.goto(origin+'/booking');await p.waitForURL('**/trips/'+trip.tripId+'?**');
    await p.getByText('Thời gian giữ chỗ đã hết. Vui lòng chọn lại ghế.').waitFor();
    assert(await available(seats[2]),'Expired seat must be selectable before physical cleanup');assert((await request('/bookings',foreign.tokens.accessToken,{...bookingForm,holdToken:expiry.holdToken})).status>=400);
    check('Natural hold expiration, refresh, released inventory and expired token rejected');
  } finally {await browser.close();}
  fs.writeFileSync('.tools/m30-6/commerce.json',JSON.stringify({checks},null,2));
}
main().catch(e=>{console.error(e.message);process.exitCode=1});
