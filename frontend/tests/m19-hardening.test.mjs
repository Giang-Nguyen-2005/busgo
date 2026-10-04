import assert from 'node:assert/strict';
import { test } from 'node:test';
import { register } from 'node:module';
register('./helpers/tsx-loader.mjs', import.meta.url);
const { statusLabels, statusPresentation } = await import('../src/utils/status.ts');
const { money, dateTime, paymentStatusLabel } = await import('../src/utils/format.ts');
const { errorMessage } = await import('../src/api/errors.ts');
test('reservation and payment status remain distinct across every domain', () => {
  assert.equal(statusPresentation('booking', 'PENDING').label, 'Đã giữ chỗ');
  assert.equal(paymentStatusLabel('PENDING'), 'Chưa thanh toán');
  for (const [domain, labels] of Object.entries(statusLabels)) for (const status of Object.keys(labels)) {
    const result = statusPresentation(domain, status);
    assert.notEqual(result.label, status);
    assert.ok(['success', 'warning', 'danger', 'info', 'neutral'].includes(result.tone));
  }
  assert.equal(statusPresentation('booking', 'INTERNAL_UNKNOWN').label, 'Chưa xác định');
  assert.match(statusPresentation('ticket', 'VOID').label, /Không dùng để lên xe/);
});
test('Vietnam time and integer VND do not depend on browser timezone', () => {
  const previous = process.env.TZ;
  process.env.TZ = 'America/New_York';
  try {
    assert.match(dateTime('2026-10-04T18:30:00Z'), /05\/10\/2026 01:30/);
    assert.match(money(123456), /123\.456/);
    assert.doesNotMatch(money(123456), /,00/);
  } finally { if (previous === undefined) delete process.env.TZ; else process.env.TZ = previous; }
});
test('unknown internal error details are never displayed', () => {
  const error = { isAxiosError: true, response: { data: { code: 'UNKNOWN', message: 'SQL password=secret' } } };
  assert.equal(errorMessage(error), 'Không thể hoàn tất yêu cầu. Vui lòng thử lại.');
  assert.match(errorMessage({ ...error, response: { data: { code: 'BUS_MAINTENANCE_CONFLICT' } } }), /bảo trì/);
});
test('late successful reads and writes from the previous account are rejected', async () => {
  const values = new Map();
  globalThis.sessionStorage = { getItem: k => values.get(k) ?? null, setItem: (k,v) => values.set(k,v), removeItem: k => values.delete(k) };
  globalThis.window = new EventTarget();
  const { setTokens } = await import('../src/api/session.ts');
  const { apiClient } = await import('../src/api/client.ts');
  const tokens = n => ({ accessToken: 'access'+n, refreshToken: 'refresh'+n, expiresIn: 3600 });
  for (const method of ['get', 'post']) {
    setTokens(tokens(1));
    let complete, started;
    const ready = new Promise(resolve => { started = resolve; });
    const request = apiClient.request({ method, url: '/operator/bookings', adapter: config => {
      started();
      return new Promise(resolve => { complete = () => resolve({ data: { data: 'old private data' }, status: 200, statusText: 'OK', headers: {}, config }); });
    } });
    await ready;
    setTokens(null); setTokens(tokens(2)); complete();
    await assert.rejects(request, { name: 'CanceledError' });
  }
  setTokens(null);
});
