import assert from 'node:assert/strict';
import { test } from 'node:test';
import { register } from 'node:module';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
register('./helpers/tsx-loader.mjs', import.meta.url);
const { PromotionPrice } = await import('../src/features/booking/PromotionPrice.tsx');
const { money } = await import('../src/utils/format.ts');
const { domainErrorMessage } = await import('../src/api/errors.ts');
test('committed voucher renders authoritative gross discount and net', () => {
  const html = renderToStaticMarkup(React.createElement(PromotionPrice, {price:{grossAmount:300,discountAmount:10,totalAmount:290,originalGrossAmount:300,originalDiscountAmount:10,voucherCode:'SAVE10'}}));
  for (const value of ['SAVE10', '300', '10', '290', 'Tổng thanh toán']) assert.ok(html.includes(value));
});
test('historical non-voucher records do not acquire a promotion presentation', () => {
  assert.equal(renderToStaticMarkup(React.createElement(PromotionPrice,{price:{grossAmount:100,discountAmount:0,totalAmount:100}})), '');
});
test('minimum payable cents and rounding residuals remain visible', () => {
  assert.ok(money(0.01).includes('0,01'));
  assert.ok(money(96.66).includes('96,66'));
  assert.ok(!money(100).includes(',00'));
});
test('customer can distinguish expired ineligible and exhausted vouchers', () => {
  assert.match(domainErrorMessage('VOUCHER_EXPIRED'), /hết hạn/);
  assert.match(domainErrorMessage('VOUCHER_NOT_ELIGIBLE'), /không áp dụng/);
  assert.match(domainErrorMessage('VOUCHER_LIMIT_REACHED'), /bỏ mã hoặc thử mã khác/);
});
