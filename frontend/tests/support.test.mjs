import test from 'node:test';
import assert from 'node:assert/strict';
import { categories, label, actionLabels } from '../src/features/support/supportModel.ts';
import { register } from 'node:module';
register('./helpers/tsx-loader.mjs', import.meta.url);
const { canAccessOperatorPath } = await import('../src/features/auth/access.ts');
const React = await import('react');
const { renderToStaticMarkup } = await import('react-dom/server');
const { MemoryRouter, Routes, Route } = await import('react-router-dom');
const { QueryClient, QueryClientProvider } = await import('@tanstack/react-query');
const { AuthContext } = await import('../src/features/auth/AuthProvider.tsx');
const { SupportDetail, SupportNew } = await import('../src/features/support/Support.tsx');

test('support categories and decisions have readable Vietnamese labels', () => {
  assert.equal(Object.keys(categories).length, 11);
  for (const [code, display] of Object.entries(categories)) assert.notEqual(display, code);
  assert.equal(label('DECISION_CUSTOMER_COMPLAINT_UPHELD'), 'Quyết định: Chấp thuận khiếu nại của khách');
  assert.equal(label('WAITING_INFORMATION'), 'Chờ bổ sung');
  assert.equal(actionLabels['accept-resolution'], 'Xác nhận đã giải quyết');
});
test('staff support routes preserve existing commerce and audit restrictions', () => {
  assert.equal(canAccessOperatorPath(['OPERATOR_STAFF'], '/operator/support'), true);
  assert.equal(canAccessOperatorPath(['OPERATOR_STAFF'], '/operator/support/tickets/12'), true);
  for (const path of ['/operator/audit-events', '/operator/staff', '/operator/customers', '/operator/bookings/new']) {
    assert.equal(canAccessOperatorPath(['OPERATOR_STAFF'], path), false);
  }
  assert.equal(canAccessOperatorPath(['SYSTEM_ADMIN', 'OPERATOR_STAFF'], '/operator/support'), false);
});
test('customer conversation renders HTML as text and exposes no commerce actions', () => {
  const client = new QueryClient({defaultOptions:{queries:{retry:false,staleTime:Infinity}}});
  const key = ['support',7,'/support'];
  client.setQueryData([...key,'ticket','1'], {id:1,bookingId:22,bookingCode:'BG-TEST',operatorName:'Nhà xe thử nghiệm',category:'BAGGAGE',subject:'Hành lý',status:'OPEN',escalation:'NONE',priority:'NORMAL',createdAt:'2026-10-20T12:00:00Z',resolvedAt:null,actions:['messages']});
  client.setQueryData([...key,'messages','1',0], {data:[{id:1,actorRole:'CUSTOMER',eventType:'MESSAGES',visibility:'PUBLIC',content:'<img src=x onerror=alert(1)>',createdAt:'2026-10-20T12:00:00Z',fromStatus:'OPEN',toStatus:'OPEN'}],pagination:{page:0,size:20,totalElements:1,totalPages:1}});
  const html=renderToStaticMarkup(React.createElement(QueryClientProvider,{client},React.createElement(AuthContext.Provider,{value:{authenticated:true,loading:false,user:{id:7,roles:['CUSTOMER']},login(){},logout(){}}},React.createElement(MemoryRouter,{initialEntries:['/support/tickets/1']},React.createElement(Routes,null,React.createElement(Route,{path:'/support/tickets/:id',element:React.createElement(SupportDetail)}))))));
  assert.match(html,/&lt;img src=x onerror=alert\(1\)&gt;/);
  assert.doesNotMatch(html,/<img\b/);
  assert.doesNotMatch(html,/Ghi chú nội bộ|Phân công \/ bỏ phân công|Hoàn tiền ngay/);
  client.clear();
});
test('owned-booking chooser uses the actual bookingId contract without a preselected URL', () => {
  const client = new QueryClient({defaultOptions:{queries:{retry:false,staleTime:Infinity}}});
  client.setQueryData(['support',7,'bookings',0], {data:[{bookingId:22,bookingCode:'BG-OWNED'}],pagination:{page:0,size:20,totalElements:1,totalPages:1}});
  const html=renderToStaticMarkup(React.createElement(QueryClientProvider,{client},React.createElement(AuthContext.Provider,{value:{authenticated:true,loading:false,user:{id:7,roles:['CUSTOMER']},login(){},logout(){}}},React.createElement(MemoryRouter,{initialEntries:['/support/new']},React.createElement(SupportNew)))));
  assert.match(html,/<option value="22">BG-OWNED<\/option>/);
  assert.doesNotMatch(html,/<option>BG-OWNED<\/option>/);
  client.clear();
});
