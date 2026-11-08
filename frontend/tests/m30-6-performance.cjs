const assert=require('node:assert/strict');
const fs=require('node:fs');
const {performance}=require('node:perf_hooks');
const {loadPlaywright}=require('./m30-4b-live.cjs');
const origin='http://127.0.0.1:5197', widths=[1440,1100,834,390,360];
const out='docs/evidence/m30-6';
async function read(url){
  const r=await fetch(origin+'/api/v1'+url);assert.equal(r.status,200,url);return (await r.json()).data;
}
async function main(){
  fs.mkdirSync(out,{recursive:true});
  const toolkit=loadPlaywright();
  const operators=await read('/public/discovery/operators');
  let trip,gallery;
  for(const op of operators){
    gallery=await read('/public/operators/'+op.id+'/media');
    const profile=await read('/public/operators/'+op.id+'/profile');
    trip=profile.trips.find(t=>gallery.photos.some(p=>p.busTypeId===t.busType.id));
    if(trip)break;
  }
  assert(trip,'Expected operator-managed photos in the dedicated live environment');
  const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date(trip.pickup.departureTime));
  const q=new URLSearchParams({pickupLocationId:trip.pickup.locationId,dropoffLocationId:trip.dropoff.locationId});
  const search='/search?'+q+'&'+new URLSearchParams({departureDate:date,pickupLabel:trip.pickup.name,dropoffLabel:trip.dropoff.name,operatorId:trip.operator.id});
  const tripPath='/trips/'+trip.tripId+'?'+q;
  const report={mode:'production build, loopback API, real Edge, fresh context per sample',samples:[],widths:[],engines:{}};
  const browser=await toolkit.chromium.launch({channel:'msedge',headless:true});
  try {
    for(const [name,url,selector] of [['home','/','#public-operators .market-operator'],['search',search,'.trip-card'],['trip',tripPath,'.vehicle-media-trigger']]){
      for(let i=0;i<3;i++){
        const p=await browser.newPage({viewport:{width:1440,height:900}});
        const start=performance.now();await p.goto(origin+url);await p.locator(selector).first().waitFor();
        const readyMs=Math.round(performance.now()-start);await p.waitForLoadState('networkidle');
        const entries=await p.evaluate(()=>performance.getEntriesByType('resource').map(r=>({path:new URL(r.name).pathname,bytes:r.encodedBodySize,transfer:r.transferSize})));
        const apis=entries.filter(r=>r.path.startsWith('/api/'));
        const sample={page:name,readyMs,apiRequests:apis.length,apiBodyBytes:apis.reduce((n,e)=>n+e.bytes,0),transferBytes:entries.reduce((n,e)=>n+e.transfer,0),mediaLists:apis.filter(r=>r.path.endsWith('/media')).length,mediaFilesBeforeOpen:apis.filter(r=>r.path.includes('/media/')).length};
        if(name==='trip'){
          const t=performance.now();await p.locator(selector).click();await p.locator('dialog[open] img').evaluate(img=>img.decode());
          sample.galleryDecodeMs=Math.round(performance.now()-t);
          sample.galleryFileBytes=await p.evaluate(()=>performance.getEntriesByType('resource').filter(e=>e.name===document.querySelector('dialog[open] img').src).at(-1)?.encodedBodySize);
        }
        report.samples.push(sample);await p.close();
      }
    }
    const p=await browser.newPage({reducedMotion:'reduce'});
    for(const width of widths){
      await p.setViewportSize({width,height:900});
      for(const [key,url,selector] of [['home','/','#destination-track'],['search',search,'.trip-card'],['trip',tripPath,'.seat:not(:disabled)']]){
        await p.goto(origin+url);await p.locator(selector).first().waitFor();await p.waitForLoadState('networkidle');
        assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),key+' overflow '+width);
        if(key==='home'){
          assert(await p.locator('#destination-track').evaluate(e=>new Set([...e.children].map(x=>x.offsetTop)).size===1));
          await p.locator('#destination-track').focus();await p.keyboard.press('ArrowRight');
        }
        if(width===390||width===1440)await p.screenshot({path:out+'/'+key+'-'+width+'.png'});
      }
      report.widths.push(width);
    }
    await p.setViewportSize({width:1440,height:900});
    await p.goto(origin+tripPath);await p.locator('.vehicle-media-trigger').waitFor();
    await p.evaluate(()=>document.documentElement.style.zoom='200%');
    assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    await p.locator('.vehicle-media-trigger').click();await p.keyboard.press('Escape');
    assert(await p.locator('.vehicle-media-trigger').evaluate(e=>e===document.activeElement));
    report.cssZoom200='Trip overflow and modal focus checked (CSS zoom)';
    for(const engine of ['firefox','webkit']){
      if(!fs.existsSync(toolkit[engine].executablePath())) {report.engines[engine]='not installed';continue;}
      const other=await toolkit[engine].launch();try{const pg=await other.newPage();await pg.goto(origin+tripPath);await pg.locator('.vehicle-media-trigger').click();await pg.keyboard.press('Escape');report.engines[engine]='Trip modal smoke passed';}finally{await other.close();}
    }
  } finally {await browser.close(); fs.writeFileSync(out+'/performance.json',JSON.stringify(report,null,2));}
}
main().catch(e=>{console.error(e.message);process.exitCode=1});
