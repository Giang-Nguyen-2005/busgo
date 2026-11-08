import assert from 'node:assert/strict';
import { test } from 'node:test';
import { existsSync, readFileSync } from 'node:fs';
import { register } from 'node:module';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { destinationAssets, interiorAssets, interiorForBusType, demoOperatorLogo } from '../src/features/customer/visualAssets.ts';
register('./helpers/tsx-loader.mjs', import.meta.url);
const { VehicleImage, OperatorLogo } = await import('../src/features/customer/CustomerMedia.tsx');
const { HomePromotionalCards, homePromotionalCards } = await import('../src/features/marketplace/HomePromotionalCards.tsx');
const render = (component, props) => renderToStaticMarkup(React.createElement(component, props));

test('all enabled images exist and are encoded as WebP', () => {
  const urls = [...destinationAssets.map(a=>a.imageUrl), ...Object.values(interiorAssets), ...homePromotionalCards.map(c=>c.image).filter(Boolean), ...['An Phú Express','Minh Thành Limousine','Tây Nguyên Travel'].map(demoOperatorLogo)];
  for (const url of urls) {
    const file = new URL('../public'+url, import.meta.url);
    assert(existsSync(file), url);
    const bytes = readFileSync(file);
    assert.equal(bytes.subarray(0,4).toString(), 'RIFF');
    assert.equal(bytes.subarray(8,12).toString(), 'WEBP');
  }
});
test('interiors require exact established type, never guessed ID, limousine capacity or STANDARD seat', () => {
  assert.equal(interiorForBusType({id:999,name:'Giường nằm 34 chỗ'}), interiorAssets.sleeper);
  assert.equal(interiorForBusType({id:347,name:'Limousine 22 chỗ'}), interiorAssets.vip);
  assert.equal(interiorForBusType({id:347,name:'  LIMOUSINE 22 chỗ  '}), interiorAssets.vip);
  for (const name of ['Limousine','Limousine 22 chỗ VIP','Ghế ngồi 40 chỗ','Giường nằm','Limousine giường phòng','Limousine ghế VIP']) {
    assert.equal(interiorForBusType({id:1,name}), undefined);
    assert.doesNotMatch(render(VehicleImage,{busType:{name}}), /<img/);
  }
  const html=render(VehicleImage,{busType:{name:'Giường nằm 34 chỗ'},detail:true});
  assert.match(html,/Ảnh minh họa loại xe/); assert.match(html,/Xem ảnh/); assert.match(html,/aria-labelledby/);
  const limousine=render(VehicleImage,{busType:{name:'Limousine 22 chỗ'},detail:true});
  assert.match(limousine,/interior-limousine-seat-vip.webp/);
  assert.match(limousine,/không xác nhận tiện nghi VIP/);
  assert.doesNotMatch(limousine,/interior-limousine-cabin/);
});
test('demo logos use exact identities and API URL has priority', () => {
  assert.match(demoOperatorLogo('An Phú Express'), /an-phu/);
  for (const name of ['An Phú','An Phu Express','An Phú Express VIP','an phú express','Unknown']) assert.equal(demoOperatorLogo(name),undefined);
  assert.match(render(OperatorLogo,{name:'An Phú Express'}), /minh họa demo/);
  const html=render(OperatorLogo,{name:'An Phú Express',logoUrl:'/api-logo.png'});
  assert.match(html,/api-logo.png/); assert.doesNotMatch(html,/an-phu-express-logo/);
});
test('discovery banners use existing anchors and never publish the unverified 20 percent claim', () => {
  const html=render(HomePromotionalCards,{});
  assert.equal(homePromotionalCards.length,3);
  assert.equal((html.match(/class="card home-promo-card"/g) || []).length,2);
  assert.match(html,/href="#public-promotions"/); assert.match(html,/href="#public-operators"/);
  assert.doesNotMatch(html,/promo-featured|20%|\/promotions/);
});
