const {loadPlaywright}=require("./m30-4b-live.cjs");
const {origin,verifyM306}=require("./helpers/m306-environment.cjs");
const fs=require("fs"), path=require("path"), assert=require("assert/strict");
const out=path.resolve(".tools/m30-6/media");
const assets=path.resolve("frontend/public/images/busgo");
const widths=[1440,1100,834,390,360];
async function main(){
 assert(process.argv.includes("--allow-live-mutations"));await verifyM306(origin);
 const {chromium}=loadPlaywright();
 fs.mkdirSync(out,{recursive:true});
 const browser=await chromium.launch({channel:"msedge",headless:true});
 try {
 const context=await browser.newContext({viewport:{width:1440,height:900},locale:"vi-VN",timezoneId:"Asia/Ho_Chi_Minh"});
 const p=await context.newPage(), errors=[];
 p.setDefaultTimeout(18000); p.on("pageerror",e=>errors.push(e.message));
 async function api(url){const r=await p.request.get(origin+"/api/v1"+url);assert(r.ok(),url+" "+r.status());return (await r.json()).data;}
 async function snap(page,name,width){await page.setViewportSize({width,height:900});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),name+" overflow "+width);await page.locator("img:visible").evaluateAll(imgs=>Promise.all(imgs.filter(i=>i.getBoundingClientRect().top<innerHeight && i.getBoundingClientRect().bottom>0).map(i=>i.decode().catch(()=>{}))));await page.screenshot({path:path.join(out,name+"-"+width+".png")});}
 const login=await p.request.post(origin+"/api/v1/auth/login",{data:{email:"operator.admin@anphu-demo.example",password:"DemoOperator!2026"}});assert(login.ok());const token=(await login.json()).data;
 const headers={Authorization:"Bearer "+token.accessToken};
 const own=await p.request.get(origin+"/api/v1/operator/marketplace-profile",{headers}); const op=(await own.json()).data;
 const profile=await api("/public/operators/"+op.id+"/profile");const trip=profile.trips.find(t=>t.availableSeats>0);assert(trip,"No available test trip");
 const type=trip.busType.id,base="/api/v1/operator/bus-types/"+type+"/media";const list=async()=> (await (await p.request.get(origin+base,{headers})).json()).data;
 await p.goto(origin);await p.evaluate(t=>sessionStorage.setItem("busgo.auth",JSON.stringify(t)),token);
 await p.goto(origin+"/operator/reviews");
 const logo=p.locator(".operator-media");await logo.getByLabel("Chọn logo mới").setInputFiles(path.join(assets,"operator-an-phu-express-logo.webp"));
 await logo.locator(".media-previews img").waitFor();await logo.getByRole("button",{name:"Lưu logo"}).click();await logo.getByText("Đã lưu hình ảnh.").waitFor();await logo.scrollIntoViewIfNeeded();await snap(p,"operator-logo",1440);
 await p.goto(origin+"/operator/bus-types/"+type);
 const section=p.locator(".operator-media");await section.getByLabel("Chọn ảnh xe").waitFor();
 const photosBefore=await list();
 // An invalid second part must roll back the preceding valid insert.
 const multi=new FormData();multi.append("category","SEAT");
 multi.append("files",new Blob([fs.readFileSync(path.join(assets,"interior-limousine-seat-vip.webp"))],{type:"image/webp"}),"seat.webp");
 multi.append("files",new Blob(["<script>"],{type:"image/png"}),"fake.png");
 const invalid=await fetch(origin+base,{method:"POST",headers,body:multi});assert.equal(invalid.status,400);assert.equal((await list()).length,photosBefore.length);
 if(!photosBefore.length){
 await section.getByLabel("Chọn ảnh xe").setInputFiles(["interior-limousine-seat-vip.webp","interior-sleeper-standard.webp"].map(n=>path.join(assets,n)));
 await section.getByLabel("Mô tả ảnh").first().fill("Khoang hành khách · ảnh kiểm thử");await section.getByRole("button",{name:"Tải ảnh lên"}).click();
 await section.getByText("Đã lưu hình ảnh.").waitFor();
 }
 const cards=section.locator("article");await cards.nth(1).waitFor();
 await cards.nth(1).getByRole("button",{name:"Đặt làm đại diện"}).click();await cards.nth(1).getByRole("button",{name:"Ảnh đại diện",exact:true}).waitFor();
 await cards.nth(1).getByRole("button",{name:"Đưa lên"}).click();await cards.first().getByRole("button",{name:"Ảnh đại diện",exact:true}).waitFor();
 await cards.first().getByRole("button",{name:"Xem ảnh lớn"}).click();await p.keyboard.press("Escape");
 for(const width of widths){await p.setViewportSize({width,height:900});await section.evaluate(e=>e.scrollIntoView({block:"start"}));await snap(p,"operator-vehicles",width);}
 const guest=await browser.newPage({locale:"vi-VN",timezoneId:"Asia/Ho_Chi_Minh"});guest.on("pageerror",e=>errors.push(e.message));
 const date=new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Ho_Chi_Minh"}).format(new Date(trip.pickup.departureTime));
 const search="/search?"+new URLSearchParams({pickupLocationId:trip.pickup.locationId,dropoffLocationId:trip.dropoff.locationId,pickupLabel:trip.pickup.name,dropoffLabel:trip.dropoff.name,departureDate:date,operatorId:op.id});
 const tripUrl="/trips/"+trip.tripId+"?pickupLocationId="+trip.pickup.locationId+"&dropoffLocationId="+trip.dropoff.locationId;
 for(const width of widths){
 await guest.setViewportSize({width,height:900});await guest.goto(origin);
 const home=guest.locator("#public-operators");await home.locator('img[src*="/media/"]').first().waitFor();await home.scrollIntoViewIfNeeded();await snap(guest,"home",width);
 await guest.goto(origin+search);const card=guest.locator(".trip-card").filter({has:guest.locator('a[href*="/trips/'+trip.tripId+'?"]')}).first();
 await card.getByText("Ảnh do nhà xe cung cấp",{exact:true}).first().waitFor();await card.scrollIntoViewIfNeeded();await snap(guest,"search",width);
 await guest.goto(origin+tripUrl);await guest.locator(".seat:not(:disabled)").first().click();await guest.locator(".vehicle-media-trigger").getByText("Xem ảnh nhà xe").click();
 const modal=guest.locator("dialog[open]");await modal.waitFor();const first=await modal.locator("img").getAttribute("src");await guest.keyboard.press("ArrowRight");assert.notEqual(await modal.locator("img").getAttribute("src"),first);await guest.keyboard.press("ArrowLeft");assert.equal(await modal.locator("img").getAttribute("src"),first);if(width===390)await snap(guest,"trip-gallery",width);
 await guest.keyboard.press("Escape");assert.equal(await guest.locator(".seat.selected").count(),1);
 assert(await guest.locator(".vehicle-media-trigger").evaluate(el=>el===document.activeElement));await guest.evaluate(()=>window.scrollTo(0,0));await snap(guest,"trip",width);
 }
 await guest.goto(origin+"/operators/"+op.id);await guest.getByText("Ảnh do nhà xe cung cấp",{exact:true}).first().waitFor();await snap(guest,"public-profile",390);
 const q=await list();assert.equal(q.length,2);assert.equal(q.filter(x=>x.featured).length,1);
 assert.deepEqual(errors,[]);console.log("PASS live operator upload/logo/order/preview, atomic rollback, profile, Home/Search/Trip, five widths, modal keyboard/seat/focus");
 } finally {await browser.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1});
