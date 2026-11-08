// Real operator routes and styles, with synthetic inventory for each seat state.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const { dataFor } = require('./fixtures/m29-api.cjs');

function light(rgb) {
  const [r,g,b] = rgb.match(/[\d.]+/g).slice(0,3).map(Number).map(v => v / 255)
    .map(v => v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4);
  return .2126*r + .7152*g + .0722*b;
}
async function colors(seat) {
  const {fg,bg,outline} = await seat.evaluate(el => ({
    fg:getComputedStyle(el.querySelector('strong')).color,
    bg:getComputedStyle(el).backgroundColor, outline:getComputedStyle(el).outlineStyle,
  }));
  const a=light(fg), b=light(bg);
  return {fg,bg,outline,ratio:(Math.max(a,b)+.05)/(Math.min(a,b)+.05)};
}
async function main() {
  const before=process.argv.includes('--before'), phase=before?'before':'after';
  const dir=process.env.BUSGO_HOVER_OUT || '.tools/seat-hover';
  fs.mkdirSync(dir,{recursive:true});
  const browser=await chromium.launch({channel:'msedge',headless:true});
  try {
    const context=await browser.newContext({locale:'vi-VN'});
    await context.addInitScript(()=>sessionStorage.setItem('busgo.auth',JSON.stringify({accessToken:'fixture',refreshToken:'fixture'})));
    await context.route('**/api/v1/**',async route=>{
      const url=new URL(route.request().url()).pathname.replace('/api/v1','');
      let data=dataFor(url,'OPERATOR_ADMIN');
      if(url==='/operator/trips/7') {
        data={data:{...data.data,segments:[data.data.segments[0],{id:91,segmentOrder:2,fromTripStopId:12,toTripStopId:13}]}};
      }
      if(url==='/operator/trips/7/crew') data={data:{assignments:[],ready:false}};
      if(url==='/operator/bus-types/1') data={data:{id:1,name:'Xe thử nghiệm',seatCount:2,seats:[true,false].map((active,i)=>({id:i+1,seatCode:'A0'+(i+1),floor:1,row:1,column:i+1,active,seatType:'STANDARD'}))}};
      if(url.endsWith('/operational-history') || url.endsWith('/bus-types/1/media')) data={data:[]};
      await route.fulfill({status:data?200:404,contentType:'application/json',body:JSON.stringify(data||{})});
    });
    const p=await context.newPage(), base=process.env.BUSGO_TEST_ORIGIN || 'http://127.0.0.1:5199';
    for(const width of [1440,834,390,360]) {
      await p.setViewportSize({width,height:900});
      await p.goto(base+'/operator/dispatch?trip=7');
      await p.locator('.db-context-controls select').nth(1).selectOption('90');
      const available=p.locator('.db-seat.operator-status-AVAILABLE').first();
      await available.scrollIntoViewIfNeeded();
      await p.mouse.move(0,0);await p.waitForTimeout(180);
      if(width===1440)await p.screenshot({path:path.join(dir,phase+'-normal.png')});
      for(const state of ['AVAILABLE','BOOKED','HELD','BLOCKED']){
        const seat=p.locator('.db-seat.operator-status-'+state).first();
        await seat.scrollIntoViewIfNeeded();await p.mouse.move(0,0);await p.waitForTimeout(180);
        const normal=await colors(seat);
        await seat.hover();await p.waitForTimeout(200);const hover=await colors(seat);
        console.log(width,state,normal.bg,hover.bg,hover.fg,hover.ratio.toFixed(2));
        if(state==='AVAILABLE' && width===1440)await p.screenshot({path:path.join(dir,phase+'-hover.png')});
        if(!before) { assert.equal(hover.bg,normal.bg);assert(hover.ratio>=4.5); }
      }
      if(!before) {
        await available.focus();await p.keyboard.press('Tab');await p.keyboard.press('Shift+Tab');
        assert(await available.evaluate(el=>el.matches(':focus-visible')));
        assert.notEqual((await colors(available)).outline,'none');
        await p.keyboard.press('Enter');assert.equal(await available.getAttribute('aria-pressed'),'true');
        await p.getByRole('button',{name:'Đóng chi tiết ghế'}).click();
        await p.locator('.db-context-controls select').first().selectOption('journey');
        assert(await p.locator('.db-seat').first().isDisabled());
        await p.goto(base+'/operator/bus-types/1');
        const preview=p.locator('.operator-seat-layout .operator-seat').first();
        await preview.hover();assert.equal(await preview.evaluate(el=>el.tagName),'DIV');
        assert.equal(await p.locator('.operator-seat-layout button').count(),0);
        await p.goto(base+'/operator/trips/7/seats?segment=90');
        await p.locator('.operator-live-seats .operator-status-AVAILABLE').first().hover();
        assert((await colors(p.locator('.operator-live-seats .operator-status-AVAILABLE').first())).ratio>=4.5);
      }
    }
  } finally { await browser.close(); }
}
main().catch(e=>{console.error(e);process.exitCode=1;});
