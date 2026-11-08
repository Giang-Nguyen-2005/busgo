import assert from "node:assert/strict";
import { register } from "node:module";
import { test } from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

register("./helpers/tsx-loader.mjs", import.meta.url);
const { OperatorVehicleImage, TripOperatorLogo } = await import("../src/features/customer/OperatorTripMedia.tsx");
const operator={id:81,name:"An Phú Express"}, busType={id:12,name:"Limousine 22 chỗ"};
const url=id=>`/api/v1/public/operators/${operator.id}/media/${id}`;
function photo(id,type,featured=false) {
  return {id,busTypeId:type,url:url(id),alt:"Ảnh ghế "+id,category:"SEAT",featured};
}
function render(component,props) {
  const client=new QueryClient();
  client.setQueryData(["operator-gallery",operator.id],{logoUrl:url("logo"),
    photos:[photo("wrong-type",13,true),photo("first",12),photo("cover",12,true)]});
  return renderToStaticMarkup(React.createElement(QueryClientProvider,{client},React.createElement(component,props)));
}
test("TripCard chooses one managed featured photo from the exact operator and type",()=>{
  const html=render(OperatorVehicleImage,{operatorId:operator.id,busType});
  assert.match(html,/Ảnh do nhà xe cung cấp/);
  assert.match(html,/src="[^"]*\/cover"/);
  assert.equal((html.match(/<img /g)||[]).length,1);
  assert.doesNotMatch(html,/wrong-type|interior-limousine|\/first"/);
  assert.match(render(TripOperatorLogo,{operator}),/src="[^"]*\/logo"/);
});
test("Trip Detail stays compact and another operator with the same catalogue type uses the demo",()=>{
  const compact=render(OperatorVehicleImage,{operatorId:operator.id,busType,compact:true});
  assert.match(compact,/Xem ảnh nhà xe/);
  assert.doesNotMatch(compact,/<img |vehicle-image-button/);
  const other=render(OperatorVehicleImage,{operatorId:82,busType});
  assert.match(other,/Ảnh minh họa loại xe/);
  assert.doesNotMatch(other,/Ảnh do nhà xe cung cấp|\/cover"/);
});
