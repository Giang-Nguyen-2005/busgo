import assert from "node:assert/strict";
import { test } from "node:test";
import { register } from "node:module";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
register('./helpers/tsx-loader.mjs', import.meta.url);
const { CancellationSection }=await import("../src/features/booking/CancellationSection.tsx");
const { AuthContext }=await import("../src/features/auth/AuthProvider.tsx");
const { BookingDetailContent }=await import("../src/pages/operator/OperatorBookingsPages.tsx");
const { PublicPaymentContent }=await import("../src/pages/PublicPaymentPage.tsx");
const base={status:"PENDING",eligible:true,ineligibleReason:null,paid:false,paymentDueAt:"2030-01-01T01:00:00Z",customerCutoffAt:"2030-01-01T02:00:00Z",cancelledAt:null,cancelledBy:null,reasonCode:null,note:null,refunds:[],tickets:[],history:[]};
function render(recovery,operator=false,roles=["CUSTOMER"]) {
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  client.setQueryData(["recovery",1,operator,9],recovery);
  return renderToStaticMarkup(React.createElement(QueryClientProvider,{client},React.createElement(AuthContext.Provider,{value:{user:{id:1,roles},authenticated:true,loading:false}},React.createElement(CancellationSection,{bookingId:9,bookingCode:"TEST",route:"Route",pickup:"Pickup",seats:["A01"],contact:"Customer",amount:100,operator}))));
}
test("customer sees correct paid/unpaid action and explicit pickup cutoff",()=>{
  assert.match(render(base),/Huỷ đặt chỗ/); assert.match(render(base),/6 giờ trước giờ đón/);
  assert.match(render({...base,paid:true,status:"CONFIRMED"}),/Huỷ và hoàn tiền mô phỏng/);
});
test("staff has read-only cancellation eligibility; operational conflict hides action",()=>{
  assert.doesNotMatch(render(base,true,["OPERATOR_STAFF"]),/<button/);
  assert.match(render(base,true,["OPERATOR_ADMIN"]),/Hủy đặt vé/);
  assert.doesNotMatch(render({...base,eligible:false,ineligibleReason:"CANCELLATION_ATTENDANCE_CONFLICT"}),/<button/);
});
test("cancelled detail explains mock refund and void ticket without a QR",()=>{
  const html=render({...base,status:"CANCELLED",eligible:false,cancelledAt:"2030-01-01T01:00:00Z",reasonCode:"CUSTOMER_CANCELLED",refunds:[{id:1,amount:100,refundedAt:"2030-01-01T01:00:00Z"}],tickets:[{id:1,ticketCode:"TKT",status:"VOID"}]});
  assert.match(html,/Hoàn tiền mô phỏng/); assert.match(html,/không chuyển tiền về tài khoản ngân hàng/);
  assert.match(html,/Đã vô hiệu/); assert.doesNotMatch(html,/<svg|<button/);
});
test("operator dossier hides QR for historical void ticket even with stale booking status",()=>{
  const b={bookingId:9,bookingCode:"T",source:"PHONE",paymentMethod:"QR_TRANSFER",status:"CONFIRMED",createdAt:"2030-01-01T00:00:00Z",updatedAt:"2030-01-01T00:00:00Z",trip:{id:1,status:"SCHEDULED",departureTime:"2030-01-02T00:00:00Z",estimatedArrivalTime:"2030-01-02T01:00:00Z"},route:{name:"Route"},contact:{name:"C",phone:"0900000000"},pickup:{name:"A",time:"2030-01-02T00:00:00Z"},dropoff:{name:"B",time:"2030-01-02T01:00:00Z"},items:[{bookingItemId:1,seatCode:"A01",unitPrice:100,ticket:{ticketCode:"VOID-TKT",status:"VOID",passengerName:"C",seatCode:"A01",paymentId:1,createdAt:"2030-01-01T00:00:00Z"}}],payments:[],totalAmount:100};
  const html=renderToStaticMarkup(React.createElement(MemoryRouter,null,React.createElement(BookingDetailContent,{booking:b})));
  assert.match(html,/Đã vô hiệu/); assert.doesNotMatch(html,/<svg/);
});
test("public cancelled link says no longer payable without internal cancellation details",()=>{
  const html=renderToStaticMarkup(React.createElement(PublicPaymentContent,{payment:{bookingCode:"TEST",operator:"O",journey:"A to B",pickup:{name:"A"},dropoff:{name:"B"},seats:["A01"],amount:100,method:"QR_TRANSFER",status:"CANCELLED"}}));
  assert.match(html,/không còn thanh toán được/); assert.doesNotMatch(html,/Chưa thanh toán/);
});
