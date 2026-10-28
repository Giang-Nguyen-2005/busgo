import test from 'node:test';
import assert from 'node:assert/strict';
import { register } from 'node:module';
register('./helpers/tsx-loader.mjs', import.meta.url);
const {apiClient}=await import('../src/api/client.ts');
const {setTokens,getTokens}=await import('../src/api/session.ts');
const {QueryClient}=await import('@tanstack/react-query');
const tokens=id=>({accessToken:id,refreshToken:id+'-refresh',expiresIn:3600});
test('late private success cannot enter the cache after logout and account replacement',async()=>{
 const oldStorage=globalThis.sessionStorage,oldWindow=globalThis.window,adapter=apiClient.defaults.adapter;
 const storage=new Map();globalThis.sessionStorage={getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)};globalThis.window=new EventTarget();
 const cache=new QueryClient({defaultOptions:{queries:{retry:false}}});let resolve,started;
 const pending=new Promise(r=>started=r);
 try{
  setTokens(tokens('old'));
  apiClient.defaults.adapter=config=>new Promise(r=>{resolve=()=>r({config,status:200,statusText:'OK',headers:{},data:{private:'old identity'}});started();});
  const request=cache.fetchQuery({queryKey:['private'],queryFn:()=>apiClient.get('/bookings').then(r=>r.data)}).catch(e=>e);
  await pending;setTokens(null);cache.clear();setTokens(tokens('new'));resolve();await request;
  assert.equal(cache.getQueryData(['private']),undefined);assert.equal(getTokens().accessToken,'new');
 }finally{cache.clear();apiClient.defaults.adapter=adapter;globalThis.sessionStorage=oldStorage;globalThis.window=oldWindow;}
});
test('late refresh success cannot restore an old identity after logout',async()=>{
 const oldStorage=globalThis.sessionStorage,oldWindow=globalThis.window,adapter=apiClient.defaults.adapter;
 const storage=new Map();globalThis.sessionStorage={getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)};globalThis.window=new EventTarget();let resolve,started;
 const pending=new Promise(r=>started=r);
 try{
  setTokens(tokens('old'));
  apiClient.defaults.adapter=config=>config.url==='/auth/refresh'?new Promise(r=>{resolve=()=>r({config,status:200,statusText:'OK',headers:{},data:{data:tokens('rotated-old')}});started();}):Promise.reject({config,response:{status:401}});
  const request=apiClient.get('/bookings').catch(e=>e);await pending;setTokens(null);setTokens(tokens('new'));resolve();await request;
  assert.equal(getTokens().accessToken,'new');
 }finally{apiClient.defaults.adapter=adapter;globalThis.sessionStorage=oldStorage;globalThis.window=oldWindow;}
});
