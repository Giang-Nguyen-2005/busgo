import assert from "node:assert/strict";
import { register } from "node:module";
import { test } from "node:test";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
register("./helpers/tsx-loader.mjs", import.meta.url);
const { VehicleImage, OperatorLogo } = await import("../src/features/customer/CustomerMedia.tsx");
const photo={id:"demo",busTypeId:12,url:"/api/v1/public/operators/61/media/demo",alt:"[DEMO M31] Ảnh minh họa",category:"EXTERIOR",featured:true};
const render=props=>renderToStaticMarkup(React.createElement(VehicleImage,{busType:{name:"Limousine 22 chỗ"},photos:[photo],...props}));
test("managed illustrative media has a truthful caption, accessible trigger and disclaimer",()=>{
  const html=render({detail:true});
  assert.match(html,/Ảnh minh họa loại xe/);assert.match(html,/Xem ảnh minh họa Limousine/);
  assert.match(html,/không xác nhận tiện nghi VIP/);assert.doesNotMatch(html,/Ảnh do nhà xe cung cấp/);
  assert.match(render({compact:true}),/Xem ảnh minh họa loại xe/);
});
test("ordinary managed uploads retain provider caption; logos retain accessible names and media identity",()=>{
  assert.match(render({photos:[{...photo,alt:"Ảnh ghế nhà xe"}]}),/Ảnh do nhà xe cung cấp/);
  const logo=renderToStaticMarkup(React.createElement(OperatorLogo,{name:"An Phú Express",logoUrl:"/api/v1/public/operators/61/media/logo"}));
  assert.match(logo,/alt="Logo An Phú Express"/);assert.match(logo,/operators\/61\/media\/logo/);assert.doesNotMatch(logo,/minh họa demo/);
});
