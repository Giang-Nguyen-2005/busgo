import assert from "node:assert/strict";
import { test } from "node:test";
import { register } from "node:module";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
register('./helpers/tsx-loader.mjs', import.meta.url);
const { AuthContext } = await import('../src/features/auth/AuthProvider.tsx');
const { SystemAdminGuard } = await import('../src/features/auth/SystemAdminGuard.tsx');
const { OperatorGuard } = await import('../src/features/auth/OperatorGuard.tsx');
const { loginDestination, canAccessOperatorPath, operatorNavigation } = await import('../src/features/auth/access.ts');
const { adminOperatorFilters, staffFilters, managementLabel, createOperatorRequest, createStaffRequest, updateStaffRequest } = await import('../src/features/operator/management.ts');
const { errorMessage } = await import('../src/api/errors.ts');
const { adminApi } = await import('../src/api/adminApi.ts');
const { operatorApi } = await import('../src/api/operatorApi.ts');
const { apiClient } = await import('../src/api/client.ts');
const { OperatorLayout } = await import('../src/layouts/OperatorLayout.tsx');
const { OperatorHomePage } = await import('../src/pages/operator/OperatorHomePage.tsx');
const { OperatorTripsPage } = await import('../src/pages/operator/OperatorTripsPages.tsx');
const { TripStatusAction } = await import('../src/features/operator/TripStatusAction.tsx');
const { StaffTable } = await import('../src/features/operator/StaffManagement.tsx');
const { AdminOperatorsPage } = await import('../src/pages/admin/AdminPages.tsx');
const h = React.createElement;
function render(element, roles = [], path = '/operator', overrides = {}, seed) {
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const user = { id: 1, fullName: 'Test', roles };
  cache.setQueryData(['me'], user); seed?.(cache);
  const html = renderToStaticMarkup(h(QueryClientProvider, { client: cache }, h(AuthContext.Provider, { value: { authenticated: true, loading: false, user, logout() {}, ...overrides } }, h(MemoryRouter, { initialEntries: [path] }, element))));
  cache.clear(); return html;
}
test('SystemAdminGuard permits only system admin and waits for authentication', () => {
  const tree = h(Routes, null, h(Route, { element: h(SystemAdminGuard) }, h(Route, { path: '*', element: h('p', null, 'ADMIN CONTENT') })));
  for (const role of ['CUSTOMER', 'OPERATOR_STAFF', 'OPERATOR_ADMIN']) {
    const html = render(tree, [role], '/admin'); assert.match(html, /Không có quyền/); assert.doesNotMatch(html, /ADMIN CONTENT/);
  }
  assert.match(render(tree, ['SYSTEM_ADMIN'], '/admin'), /ADMIN CONTENT/);
  assert.match(render(tree, [], '/admin', { loading: true }), /Đang tải/);
  assert.doesNotMatch(render(tree, [], '/admin', { authenticated: false }), /ADMIN CONTENT/);
  assert.match(render(tree, [], '/admin', { error: new Error(), retry() {} }), /Thử lại/);
});
test('role-aware login preserves authorized return paths and customer behavior', () => {
  globalThis.window = { location: { origin: 'http://localhost:5173' } };
  try {
  assert.equal(loginDestination(['SYSTEM_ADMIN'], null), '/admin');
  for (const role of ['OPERATOR_ADMIN', 'OPERATOR_STAFF']) assert.equal(loginDestination([role], null), '/operator');
  assert.equal(loginDestination(['CUSTOMER'], '/booking?tripId=1'), '/booking?tripId=1');
  assert.equal(loginDestination(['CUSTOMER'], null), '/');
  assert.equal(loginDestination(['OPERATOR_STAFF'], '/operator/trips/7/occupancy'), '/operator/trips/7/occupancy');
  assert.equal(loginDestination(['OPERATOR_STAFF'], '/operator/trips/new'), '/operator');
  assert.equal(loginDestination(['SYSTEM_ADMIN'], '/operator'), '/admin');
  assert.equal(loginDestination(['CUSTOMER'], '/admin/operators'), '/');
  assert.equal(loginDestination(['CUSTOMER'], '//evil.test'), '/');
  } finally { delete globalThis.window; }
});
test('staff cannot access mutation URLs or grant system admins operator access', () => {
  for (const path of ['/operator', '/operator/trips', '/operator/trips/2', '/operator/bookings', '/operator/bookings/2', '/operator/trips/2/passengers', '/operator/trips/2/occupancy', '/operator/bus-types']) assert.equal(canAccessOperatorPath(['OPERATOR_STAFF'], path), true);
  for (const path of ['/operator/trips/new', '/operator/buses/new', '/operator/buses/2', '/operator/routes', '/operator/staff']) {
    assert.equal(canAccessOperatorPath(['OPERATOR_STAFF'], path), false);
    assert.match(render(h(OperatorGuard), ['OPERATOR_STAFF'], path), /Không có quyền/);
    assert.equal(canAccessOperatorPath(['OPERATOR_ADMIN'], path), true);
  }
  assert.equal(canAccessOperatorPath(['SYSTEM_ADMIN', 'OPERATOR_ADMIN'], '/operator'), false);
});
test('operator and staff filters validate URL parameters and Vietnamese labels', () => {
  assert.deepEqual(adminOperatorFilters(new URLSearchParams('q=+Bus+&status=INACTIVE&page=2&size=10')), { q: 'Bus', status: 'INACTIVE', page: 2, size: 10 });
  const bad = adminOperatorFilters(new URLSearchParams('status=BAD&page=2147483647&size=13'));
  assert.equal(bad.status, undefined); assert.equal(bad.page, 0); assert.equal(bad.size, 20);
  assert.equal(staffFilters(new URLSearchParams('role=SYSTEM_ADMIN')).role, undefined);
  for (const label of ['ACTIVE', 'INACTIVE', 'LOCKED', 'OPERATOR_ADMIN', 'OPERATOR_STAFF']) assert.notEqual(managementLabel(label), label);
});
test('staff navigation, quick actions and trip mutation controls are read-only', () => {
  assert.ok(operatorNavigation(['OPERATOR_ADMIN']).some(([p]) => p === '/staff'));
  for (const component of [OperatorLayout, OperatorHomePage, OperatorTripsPage]) {
    const html = render(h(component), ['OPERATOR_STAFF']);
    assert.doesNotMatch(html, /href="\/operator\/(staff|buses|routes|trips\/new)/);
    assert.doesNotMatch(html, /Tạo chuyến/);
  }
  assert.equal(render(h(TripStatusAction, { id: 1, status: 'SCHEDULED' }), ['OPERATOR_STAFF']), '');
  assert.match(render(h(TripStatusAction, { id: 1, status: 'SCHEDULED' }), ['OPERATOR_ADMIN']), /Mở lên xe/);
});
test('M13 errors never expose raw backend codes or messages', () => {
  for (const code of ['OPERATOR_NOT_FOUND', 'STAFF_NOT_FOUND', 'OPERATOR_CODE_ALREADY_EXISTS', 'EMAIL_ALREADY_EXISTS', 'STAFF_CODE_ALREADY_EXISTS', 'STAFF_MEMBERSHIP_CONFLICT', 'LAST_OPERATOR_ADMIN_REQUIRED', 'OPERATOR_ACTIVATION_NOT_ALLOWED', 'ACCESS_DENIED']) {
    const text = errorMessage({ isAxiosError: true, response: { data: { code, message: 'Raw message' } } });
    assert.doesNotMatch(text, /Raw message/); assert.ok(!text.includes(code)); assert.notEqual(text, errorMessage(new Error()));
  }
  assert.match(errorMessage({ isAxiosError: true, response: { data: { code: 'LAST_OPERATOR_ADMIN_REQUIRED' } } }), /ít nhất một quản trị viên đang hoạt động/);
  assert.match(errorMessage({ isAxiosError: true, response: { data: { code: 'OPERATOR_ACTIVATION_NOT_ALLOWED' } } }), /có thể đăng nhập/);
});
const form = values => { const data = new FormData(); for (const [k,v] of Object.entries(values)) data.set(k,v); return data; };
test('operator and staff request mapping whitelists fields and preserves passwords', async () => {
  const account = { fullName: ' Admin ', email: 'a@example.com', phone: '0901234567', password: ' secret123 ', staffCode: ' A01 ' };
  const initial = Object.fromEntries(Object.entries(account).map(([k,v]) => ['admin.' + k, v]));
  const operator = createOperatorRequest(form({ name: ' Bus ', code: ' BUS ', status: 'INACTIVE', phone: '', email: '', address: '', ...initial, operatorId: '77' }));
  assert.deepEqual(operator, { name: 'Bus', code: 'BUS', phone: '', email: '', address: '', status: 'INACTIVE', initialAdmin: { ...account, fullName: 'Admin', staffCode: 'A01' } });
  const staff = createStaffRequest(form({ ...account, role: 'OPERATOR_ADMIN', operatorId: '77' }));
  assert.equal(staff.password, ' secret123 '); assert.equal(staff.role, 'OPERATOR_ADMIN'); assert.equal(staff.operatorId, undefined);
  assert.equal(createStaffRequest(form({ ...account, role: 'SYSTEM_ADMIN' })).role, 'OPERATOR_STAFF');
  assert.deepEqual(updateStaffRequest(form({ staffCode: ' S ', status: 'INACTIVE', role: 'OPERATOR_STAFF', operatorId: '77' })), { staffCode: 'S', status: 'INACTIVE', role: 'OPERATOR_STAFF' });
  const requests = []; const original = apiClient.defaults.adapter;
  apiClient.defaults.adapter = async config => { requests.push(config); return { config, status: 200, headers: {}, data: { data: { id: 1 } } }; };
  try {
    await adminApi.createOperator(operator); await operatorApi.createStaff(staff); await operatorApi.updateStaff(3, { staffCode: 'S', role: 'OPERATOR_STAFF', status: 'INACTIVE' }); await adminApi.updateStatus(1, 'ACTIVE');
    assert.deepEqual(requests.map(r => r.url), ['/admin/operators', '/operator/staff', '/operator/staff/3', '/admin/operators/1/status']);
    assert.deepEqual(JSON.parse(requests[0].data), operator); assert.deepEqual(JSON.parse(requests[1].data), staff);
    assert.deepEqual(JSON.parse(requests[3].data), { status: 'ACTIVE' });
  } finally { apiClient.defaults.adapter = original; }
});
test('admin staff table is read-only and nullable fields are safe', () => {
  const rows = [{ staffId: 1, staffCode: null, membershipStatus: 'ACTIVE', role: 'OPERATOR_STAFF', user: { fullName: 'Lan', email: 'lan@example.com', phone: null, status: 'LOCKED' }, createdAt: '2026-09-28T00:00:00Z' }];
  const html = render(h(StaffTable, { rows }), ['SYSTEM_ADMIN']);
  assert.match(html, /Đã khóa/); assert.match(html, /Lan/); assert.doesNotMatch(html, /<button/);
});
test('admin list renders real data, pagination and onboarding sections', () => {
  const html = render(h(AdminOperatorsPage), ['SYSTEM_ADMIN'], '/admin/operators?create=1', {}, cache => cache.setQueryData(['admin', 'operators', adminOperatorFilters(new URLSearchParams())], { data: [{ id: 2, code: 'BUS2', name: 'Nhà xe 2', phone: null, email: null, status: 'ACTIVE', activeStaffCount: 3, activeAdminCount: 1, createdAt: '2026-09-28T00:00:00Z' }], pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1 } }));
  assert.match(html, /admin\/operators\/2/); assert.match(html, /1 kết quả/); assert.match(html, /Thông tin nhà xe/); assert.match(html, /Quản trị viên ban đầu/); assert.match(html, /không có email mời/);
});
