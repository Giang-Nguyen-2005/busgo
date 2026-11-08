const assert=require("node:assert/strict"),fs=require("node:fs");
const isolated=require("./helpers/m306-environment.cjs");
const origin=isolated.origin+"/api/v1";
(async()=>{
 assert(process.argv.includes("--allow-live-mutations"));await isolated.verifyM306(isolated.origin);
 const auth=await fetch(origin+"/auth/login",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email:"operator.admin@anphu-demo.example",password:"DemoOperator!2026"})});
 assert.equal(auth.status,200);
 const headers={Authorization:"Bearer "+(await auth.json()).data.accessToken};
 const profile=(await (await fetch(origin+"/operator/marketplace-profile",{headers})).json()).data;
 const publicPath=origin+"/public/operators/"+profile.id+"/media";
 const gallery=async()=>(await (await fetch(publicPath)).json()).data;
 const before=await gallery();assert.equal(before.photos.length,2);
 const base=origin+"/operator/bus-types/"+before.photos[0].busTypeId+"/media";
 const file=fs.readFileSync("frontend/public/images/busgo/interior-sleeper-standard.webp");
 const part=(content,mime,name)=>[new Blob([content],{type:mime}),name];
 const good=part(file,"image/webp","seat.webp"),bad=part("not an image","image/png","fake.png");
 async function upload(parts,auth=headers) {
   const data=new FormData();data.append("category","SEAT");
   for(const args of parts)data.append("files",...args);
   return fetch(base,{method:"POST",headers:auth,body:data});
 }
 assert.equal((await upload([good],{})).status,401);
 assert.equal((await fetch(base+"/"+before.photos[0].id,{method:"DELETE"})).status,401);
 assert.equal((await upload([good,bad])).status,400);
 assert.deepEqual(await gallery(),before);
 assert.equal((await upload([part(Buffer.alloc(5*1024*1024+1),"image/jpeg","large.jpeg")])).status,413);
 const results=await Promise.all(Array.from({length:11},()=>upload([good])));
 assert.deepEqual(results.map(r=>r.status).sort(),[...Array(10).fill(200),400]);
 const full=await gallery();assert.equal(full.photos.length,12);assert.equal(full.photos.filter(p=>p.featured).length,1);
 for(const p of full.photos.filter(p=>!before.photos.some(initial=>initial.id===p.id))){
   const r=await fetch(base+"/"+p.id,{method:"DELETE",headers});assert.equal(r.status,200);
 }
 assert.deepEqual(await gallery(),before);
 console.log("PASS anonymous POST/DELETE 401; fake second part 400 and exact rollback; actual multipart >5 MiB 413; 11 concurrent requests for 10 slots: 10 succeeded, 1 rejected; 1 featured image; restored original gallery");
})().catch(e=>{console.error(e);process.exitCode=1});
