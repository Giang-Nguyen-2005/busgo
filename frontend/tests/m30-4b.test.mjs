import assert from 'node:assert/strict';
import { test } from 'node:test';
import { register } from 'node:module';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
register('./helpers/tsx-loader.mjs', import.meta.url);
const { PublicPromotionCard, promotionSearch } = await import('../src/features/marketplace/PublicPromotions.tsx');
const { TripCard }=await import('../src/components/ui.tsx');
const h=React.createElement;
const promo={id:1,operator:{id:9,name:'Nhà xe test'},route:{id:4,name:'A → B',pickupLocationId:21,dropoffLocationId:37},code:'PUBLIC',name:'Campaign <script>',description:'Public only',discountType:'PERCENT',discountValue:25,maxDiscount:50000,minimumSpend:300000,startsAt:'2027-01-01T00:00:00Z',endsAt:'2027-01-31T00:00:00Z',customerLimit:1,eligibility:'REQUIRES_CONTEXT'};
const render=(p,inTrip=false)=>renderToStaticMarkup(h(MemoryRouter,null,h(PublicPromotionCard,{promotion:p,inTrip})));
test('public card shows campaign terms without a quoted net or personal eligibility',()=>{
 const html=render(promo);for(const text of ['Nhà xe test','25%','Giá gốc tối thiểu','Giảm tối đa','Giờ Việt Nam','Cần kiểm tra hành trình và tài khoản của bạn để xác nhận điều kiện áp dụng','không giữ lượt'])assert(html.includes(text));
 assert.doesNotMatch(html,/<script>|Tổng thanh toán|Đã áp dụng|discountAmount|totalAmount/);
 assert.match(html,/Tìm chuyến phù hợp/);
});
test('route discovery keeps operator and endpoints but never invents a departure date',()=>{
 const params=new URL(promotionSearch(promo),'https://example.invalid').searchParams;
 assert.equal(params.get('operatorId'),'9');assert.equal(params.get('pickupLocationId'),'21');assert.equal(params.get('dropoffLocationId'),'37');assert.equal(params.get('departureDate'),null);
 const unrestricted=new URL(promotionSearch({...promo,route:null}),'https://example.invalid').searchParams;assert.equal(unrestricted.get('operatorId'),'9');assert.equal(unrestricted.get('pickupLocationId'),null);
});
test('trip promotion offers copy and seat anchor without claiming hold or applying code',()=>{
 const html=render({...promo,eligibility:'CONDITIONAL'},true);assert.match(html,/có thể phù hợp với chuyến/);assert.match(html,/href="#trip-seats"/);assert.match(html,/Sao chép mã PUBLIC/);assert.doesNotMatch(html,/href="\/operators\//);assert.doesNotMatch(html,/Đã áp dụng|Giữ lượt/);
});
test('search promotion badge is conditional on backend projection and retains gross price/context',()=>{
 const trip={tripId:7,operator:{id:9,name:'Test'},route:{id:4,name:'A → B'},busType:{id:1,name:'Xe'},pickup:{locationId:21,name:'A',departureTime:'2027-01-15T00:00:00Z'},dropoff:{locationId:37,name:'B',arrivalTime:'2027-01-15T02:00:00Z'},price:350000,availableSeats:5,durationMinutes:120};
 const renderTrip=flag=>renderToStaticMarkup(h(QueryClientProvider,{client:new QueryClient()},h(MemoryRouter,null,h(TripCard,{trip:{...trip,hasPublicPromotions:flag},searchContext:'/search?sort=PRICE_ASC'}))));
 assert.doesNotMatch(renderTrip(false),/Có ưu đãi/);const html=renderTrip(true);assert.match(html,/Có ưu đãi — xem điều kiện/);assert.match(html,/#trip-promotions/);assert.match(html,/350\.000/);assert.match(html,/PRICE_ASC/);assert.doesNotMatch(html,/<del/);
});
