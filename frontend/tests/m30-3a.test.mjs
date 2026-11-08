import assert from 'node:assert/strict';
import { test } from 'node:test';
import { register } from 'node:module';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { destinationAssets, destinationForLocation, safeImageUrl, operatorDescription } from '../src/features/customer/visualAssets.ts';
register('./helpers/tsx-loader.mjs', import.meta.url);
const { OperatorLogo, DestinationImage, AssetImage } = await import('../src/features/customer/CustomerMedia.tsx');
const render = (component, props) => renderToStaticMarkup(React.createElement(component, props));
test('six destinations resolve against API location IDs, independent of seed order', () => {
  assert.equal(destinationAssets.length, 6);
  destinationAssets.forEach((asset, i) => {
    const locations = [{id:900-i*17, name:asset.locationNames[0]}];
    assert.equal(destinationForLocation(locations[0].id, locations), asset);
    assert.equal(asset.imageUrl, '/images/busgo/'+asset.file); // Supplied assets only.
  });
  assert.equal(destinationForLocation(1, [{id:1,name:'Một điểm khác',province:'Lâm Đồng'}]), undefined);
  assert.equal(destinationForLocation(999, [{id:2,name:'Đà Lạt'}]), undefined);
  assert.equal(destinationForLocation(2), undefined);
});
test('logo URLs accept HTTPS/local paths and reject invalid or unsafe sources', () => {
  for (const value of [null, '', ' ', 'javascript:alert(1)', '//other/logo', '/\\other/logo', 'http://example.com/a', 'https://', 'https://a b/logo', 'data:image/svg+xml,x', 'https://user:pass@example.com/a']) {
    assert.equal(safeImageUrl(value), undefined);
    assert.doesNotMatch(render(OperatorLogo, {name:'Nhà xe',logoUrl:value}), /<img/);
  }
  for (const value of ['/images/logo.webp','https://example.com/logo.png']) {
    const html=render(OperatorLogo,{name:'Nhà xe',logoUrl:value});
    assert.match(html, /Logo Nhà xe/); assert.match(html, /width="72"/); assert.ok(html.includes(value));
  }
});
test('missing destination image uses neutral icon, and supplied image is lazy with reserved dimensions', () => {
  assert.doesNotMatch(render(DestinationImage,{}), /<img/);
  const html=render(DestinationImage,{asset:{...destinationAssets[0],imageUrl:'/images/approved.webp'}});
  assert.match(html, /loading="lazy"/); assert.match(html, /width="1200" height="900"/);
  assert.equal(render(AssetImage,{src:'bad',alt:'',width:100,height:50}), '');
});
test('operator description trims blanks and escapes content through React', () => {
  assert.equal(operatorDescription('  '), 'Thông tin giới thiệu chưa được cung cấp.');
  assert.equal(operatorDescription(' Mô tả đã cung cấp '),'Mô tả đã cung cấp');
});
