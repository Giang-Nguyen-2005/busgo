import assert from 'node:assert/strict';
import { test } from 'node:test';
import { register } from 'node:module';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
register('./helpers/tsx-loader.mjs', import.meta.url);
const { VoucherOptionCard } = await import('../src/features/booking/CheckoutVoucherOptions.tsx');
const { VouchersPage } = await import('../src/features/operator/Vouchers.tsx');
const { AuthContext } = await import('../src/features/auth/AuthProvider.tsx');
const h=React.createElement;
const offer={id:1,code:'PUBLIC25',name:'Ưu đãi <script>',description:'Điều kiện công khai',discountType:'PERCENT',discountValue:25,maxDiscount:50000,minimumSpend:100000,startsAt:'2027-01-01T00:00:00Z',endsAt:'2027-01-31T00:00:00Z',customerLimit:1,status:'AVAILABLE',message:null,quote:{discountAmount:12345,totalAmount:98765}};
const render=v=>renderToStaticMarkup(h(VoucherOptionCard,{option:v,selected:false,disabled:false,onSelect(){}}));
test('voucher option presents server amounts and public conditions without recalculating percentages',()=>{
 const html=render(offer);assert.match(html,/12\.345/);assert.match(html,/98\.765/);assert.match(html,/Giá gốc tối thiểu/);assert.match(html,/Giảm tối đa/);assert.match(html,/Giờ Việt Nam/);assert.doesNotMatch(html,/<script>/);
 assert.match(html,/Chọn mã PUBLIC25/);assert.doesNotMatch(html,/disabled=""/);
});
test('not met and unavailable offers cannot be selected or claim a reduction',()=>{
 for(const status of ['CONDITION_NOT_MET','UNAVAILABLE']){
  const html=render({...offer,status,message:'Điều kiện do server từ chối',quote:null});assert.match(html,/disabled=""/);assert.match(html,/Điều kiện do server từ chối/);assert.doesNotMatch(html,/Có thể giảm/);
 }
});
test('operator staff sees visibility but only admin can toggle, including used terms',()=>{
 const renderRole=role=>{
  const q=new QueryClient({defaultOptions:{queries:{retry:false}}});q.setQueryData(['vouchers',1,'','',0],{data:[{id:1,name:'Used campaign',code:'USED',customer_visible:true,state:'ACTIVE',effective_state:'ACTIVE',editable:false,discount_type:'PERCENT',discount_value:10,starts_at:offer.startsAt,ends_at:offer.endsAt,reserved:1,redeemed:1}],pagination:{totalPages:1}});
  return renderToStaticMarkup(h(QueryClientProvider,{client:q},h(AuthContext.Provider,{value:{user:{id:1,roles:[role]},authenticated:true,loading:false}},h(MemoryRouter,null,h(VouchersPage)))));
 };
 assert.match(renderRole('OPERATOR_ADMIN'),/Tắt công khai/);
 const staff=renderRole('OPERATOR_STAFF');assert.match(staff,/Cho phép hiển thị tại checkout/);assert.doesNotMatch(staff,/Tắt công khai|Bật công khai|Tạo mã ưu đãi/);
});
