import assert from 'node:assert/strict';
import { test } from 'node:test';
import { register } from 'node:module';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { journeyTitle } from '../src/features/customer/presentation.ts';
register('./helpers/tsx-loader.mjs', import.meta.url);
const { PrebookingDetails } = await import('../src/features/trip/PrebookingDetails.tsx');
const pickup = {name:'Điểm giữa B',departureTime:'2030-09-20T17:30:00Z'};
const dropoff = {name:'Điểm giữa C',arrivalTime:'2030-09-20T18:30:00Z'};
const render = props => renderToStaticMarkup(React.createElement(PrebookingDetails,{pickup,dropoff,...props}));
const info = {pickup:{province:'Tỉnh B',district:'Quận B'},dropoff:{province:'Tỉnh C',district:null},vehicle:{seatCount:40,description:'Cấu hình <script>không chạy</script>'},policy:{customerCutoffAt:'2030-09-20T11:30:00Z',fullCancellation:'Quy tắc hủy từ server',partialCancellation:'Giữ ít nhất một hành khách',modification:'Kiểm tra ghế/chuyến thay thế',refund:'Hoàn tiền mô phỏng',eligibilityNotice:'Kiểm tra điều kiện lại khi xác nhận'}};
test('prebooking uses selected stop context and separates configured count from trip geometry',()=>{
 const html=render({information:info,vehicleName:'Xe cấu hình',seats:[{floor:2},{floor:1},{floor:2}]});
 assert.match(html,/Điểm giữa B/);assert.match(html,/Điểm giữa C/);assert.match(html,/Quận B, Tỉnh B/);
 assert.match(html,/40 chỗ theo cấu hình/);assert.match(html,/3 vị trí · Tầng 1, 2/);
 assert.match(html,/không phải số chỗ còn trống/);assert.match(html,/không xác nhận vị trí đón\/trả chính xác/);
 assert.match(html,/20\/09\/2030 18:30/); assert.match(html,/Quy tắc hủy từ server/);
 assert.doesNotMatch(html,/<script>|Wi-Fi|USB|WC|latitude|licensePlate/);
});
test('missing description, location and policy do not invent data or promise eligibility',()=>{
 const html=render({information:{...info,pickup:{province:null,district:null},vehicle:{seatCount:null,description:' '},policy:null}});
 assert.match(html,/Khu vực: Chưa cập nhật/);assert.match(html,/Chưa có mô tả cấu hình/);
 assert.match(html,/Chưa có bản tóm tắt chính sách/);assert.match(html,/Chưa có địa chỉ và hướng dẫn/);
 assert.doesNotMatch(html,/Mốc yêu cầu|6 giờ|\d+ chỗ theo cấu hình/);
});
test('old checkout without saved metadata has natural fallbacks and no assumed policy',()=>{
 const html=render({snapshot:true}); assert.match(html,/Thông tin tham khảo từ bước chọn ghế/);
 assert.match(html,/Điểm giữa B/);assert.match(html,/Chưa cập nhật tên loại xe/);
 assert.match(html,/Chưa có bản tóm tắt chính sách/);
});
test('customer journey without labels never displays internal location identifiers',()=>{
 const title=journeyTitle({pickupLocationId:123,dropoffLocationId:987,pickupLabel:'',dropoffLabel:'',departureDate:''});
 assert.doesNotMatch(title,/#|123|987/);assert.match(title,/Điểm đón đã chọn/);
});
