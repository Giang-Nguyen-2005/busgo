import assert from 'node:assert/strict';
import { test } from 'node:test';
import { register } from 'node:module';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
register('./helpers/tsx-loader.mjs', import.meta.url);
const {AuthContext}=await import('../src/features/auth/AuthProvider.tsx');
const {OperatorLayout}=await import('../src/layouts/OperatorLayout.tsx');
const {AdminLayout}=await import('../src/layouts/AdminLayout.tsx');
const {CustomerLayout}=await import('../src/layouts/CustomerLayout.tsx');
const {operatorNavigation}=await import('../src/features/auth/access.ts');
const {operatorIcons,adminNavigation}=await import('../src/components/foundations/navigationIcons.ts');
const {LoadingSkeleton,LoadingIndicator}=await import('../src/components/foundations/Loading.tsx');
const {IconButton}=await import('../src/components/foundations/IconButton.tsx');
const {Field,ErrorState,Empty}=await import('../src/components/ui.tsx');
const h=React.createElement;
function shell(component,role,url){const client=new QueryClient({defaultOptions:{queries:{retry:false}}});return renderToStaticMarkup(h(QueryClientProvider,{client},h(AuthContext.Provider,{value:{user:{id:1,fullName:'Test user',email:'fixture@example.invalid',roles:[role]},authenticated:!!role,loading:false,login(){},logout(){}}},h(MemoryRouter,{initialEntries:[url]},h(component)))));}
test('operator menus retain exactly the access helper destinations for admin and staff',()=>{
 for(const role of ['OPERATOR_ADMIN','OPERATOR_STAFF']){
  const html=shell(OperatorLayout,role,'/operator/bookings');
  const nav=[...html.matchAll(/<nav[^>]*class="ws-nav"[^>]*>([\s\S]*?)<\/nav>/g)];assert.equal(nav.length,2);
  for(const [,markup]of nav){const links=[...markup.matchAll(/href="([^"]+)"/g)].map(x=>x[1]);assert.deepEqual(links.sort(),operatorNavigation([role]).map(([p])=>'/operator'+p).sort());assert.match(markup,/aria-current="page"[^>]*href="\/operator\/bookings"/);}
  assert.match(html,/aria-controls="operator-navigation"/);assert.match(html,/Điều hành nhà xe/);
 }
});
test('every authorized operator destination has an explicit Lucide icon',()=>{
 for(const [path]of operatorNavigation(['OPERATOR_ADMIN']))assert.ok(operatorIcons[path],path);
 assert.equal(Object.keys(operatorIcons).length,operatorNavigation(['OPERATOR_ADMIN']).length);
});
test('platform shell preserves its six destinations and distinct identity',()=>{
 const html=shell(AdminLayout,'SYSTEM_ADMIN','/admin/disputes');
 assert.equal(adminNavigation.length,6);for(const {to}of adminNavigation)assert.ok(html.includes(`href="${to}"`));
 assert.match(html,/Quản trị hệ thống/);assert.match(html,/Quản trị viên BusGo/);assert.match(html,/Đăng xuất/);
 assert.doesNotMatch(html,/href="\/operator/);
});
test('customer mobile navigation preserves guest and signed-in account routes',()=>{
 const customer=shell(CustomerLayout,'CUSTOMER','/my-bookings');
 for(const href of ['/profile','/my-bookings','/support','/operator-application'])assert.ok(customer.includes(`href="${href}"`));
 assert.match(customer,/aria-controls="customer-navigation"/);assert.match(customer,/Đăng xuất/);
 const guest=shell(CustomerLayout,'','/');assert.match(guest,/href="\/login"/);assert.match(guest,/href="\/register"/);assert.doesNotMatch(guest,/href="\/profile"/);
 const admin=shell(CustomerLayout,'SYSTEM_ADMIN','/');assert.match(admin,/href="\/admin"/);assert.doesNotMatch(admin,/href="\/profile"/);
});
test('loading primitives describe loading once and never invent business amounts',()=>{
 for(const variant of ['summary','list','table','panel']){
  const html=renderToStaticMarkup(h(LoadingSkeleton,{variant,label:'Loading bookings',rows:2}));
  assert.equal((html.match(/role="status"/g)||[]).length,1);assert.match(html,/aria-label="Loading bookings"/);assert.equal((html.match(/aria-hidden="true"/g)||[]).length,6);assert.doesNotMatch(html,/₫|CONFIRMED|PAID/);
 }
 assert.match(renderToStaticMarkup(h(LoadingIndicator)),/role="status"/);
});
test('icon controls and existing field/error/empty contracts remain accessible',()=>{
 const button=renderToStaticMarkup(h(IconButton,{label:'Close',disabled:true},'x'));assert.match(button,/aria-label="Close"/);assert.match(button,/type="button"/);assert.match(button,/disabled/);
 const field=renderToStaticMarkup(h(Field,{id:'contact',label:'Contact',error:'Required',description:'Use your name'}));assert.match(field,/aria-invalid="true"/);assert.match(field,/aria-describedby="contact-hint contact-error"/);
 assert.match(renderToStaticMarkup(h(ErrorState,{error:new Error('Failure'),retry(){}})),/Thử lại/);
 assert.match(renderToStaticMarkup(h(Empty,{title:'No items'},h('a',{href:'/'} ,'Recover'))),/Recover/);
});
