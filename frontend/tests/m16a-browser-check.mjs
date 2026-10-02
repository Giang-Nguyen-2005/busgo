// Live verification against the isolated dev,demo backend proxied by Vite.
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const origin = process.env.BUSGO_BROWSER_ORIGIN || 'http://127.0.0.1:5173';
const api = origin + '/api/v1';
async function call(method, path, token, body) {
  const r = await fetch(api + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
  const json = await r.json();
  return { status: r.status, ...json };
}
const login = async (email, password) => {
  const result = await call('POST', '/auth/login', null, { email, password }); assert.equal(result.status, 200); return result.data.accessToken;
};
const admin = await login('operator.admin@anphu-demo.example', 'DemoOperator!2026');
const trips = await call('GET', '/operator/trips?status=SCHEDULED&size=100', admin);
const chosen = trips.data.find(t => new Date(t.departureTime).getTime() > Date.now() + 3600000);
assert.ok(chosen, 'Demo requires a future scheduled trip');
const trip = (await call('GET', '/operator/trips/' + chosen.id, admin)).data;
const pickup = trip.stops.find(s => s.allowPickup);
const dropoff = trip.stops.at(-1);
const journey = `pickupLocationId=${pickup.locationId}&dropoffLocationId=${dropoff.locationId}`;
const directory = new URL('../../.tools/m16a-browser/', import.meta.url);
mkdirSync(directory, { recursive: true });
const browser = await chromium.launch({ headless: true, channel: 'msedge' });
const results = [];
async function uiLogin(page, email, password) {
  await page.goto(origin + '/login'); await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.waitForURL(url => url.pathname !== '/login');
}
async function overflow(page) { assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'Document must not overflow'); }
async function screenshot(page, name) { await overflow(page); await page.screenshot({ path: new URL(name + '.png', directory).pathname.replace(/^\/([A-Z]:)/, '$1'), fullPage: true }); }
try {
  for (const width of [390, 820, 1440]) {
    const context = await browser.newContext({ viewport: { width, height: 1000 }, permissions: ['clipboard-read', 'clipboard-write'] });
    const page = await context.newPage(); const errors = []; page.on('pageerror', e => errors.push(e.message));
    await uiLogin(page, 'operator.admin@anphu-demo.example', 'DemoOperator!2026');
    for (const method of ['PAY_ON_BOARD', 'QR_TRANSFER']) {
      await page.goto(origin + `/operator/bookings/new?tripId=${trip.id}`);
      await page.getByLabel('Điểm đón', { exact: true }).selectOption(String(pickup.locationId));
      await page.getByLabel('Điểm trả', { exact: true }).selectOption(String(dropoff.locationId));
      const button = page.locator('.seat:not(:disabled)').first(); await button.waitFor();
      const seatCode = await button.locator('span').textContent(); await button.click();
      await page.getByLabel('Tên liên hệ', { exact: true }).fill(`Caller ${width} ${method}`);
      await page.getByLabel('Điện thoại', { exact: true }).fill('0901234567');
      await page.getByLabel('Phương thức thanh toán', { exact: true }).selectOption(method);
      await page.getByRole('button', { name: 'Kiểm tra và tạo đặt vé', exact: true }).click();
      await screenshot(page, `${width}-${method}-review`);
      const response = page.waitForResponse(r => r.url().endsWith('/api/v1/operator/bookings') && r.request().method() === 'POST');
      await page.getByRole('button', { name: 'Tạo đặt vé', exact: true }).click();
      const created = await response; assert.equal(created.status(), 201); const booking = (await created.json()).data;
      await page.waitForURL(`**/operator/bookings/${booking.bookingId}`);
      await page.getByRole('heading', { name: booking.bookingCode, exact: true }).waitFor();
      assert.equal(await page.locator('.dossier-seat svg').count(), 0);
      const detail = (await call('GET', '/operator/bookings/' + booking.bookingId, admin)).data;
      assert.equal(detail.source, 'PHONE'); assert.equal(detail.customer, null); assert.equal(detail.payments.length, 0);
      const map = (await call('GET', `/trips/${trip.id}/seats?${journey}`)).data;
      assert.equal(map.seats.find(s => s.seatCode === seatCode).available, false);
      const conflict = await call('POST', '/operator/bookings', admin, { tripId: trip.id, pickupLocationId: pickup.locationId, dropoffLocationId: dropoff.locationId, tripSeatIds: booking.seats.map(s => s.tripSeatId), contactName: 'Conflict', contactPhone: '0901234567', paymentMethod: method });
      assert.equal(conflict.status, 409); assert.equal(conflict.code, 'SEAT_NOT_AVAILABLE');
      if (method === 'PAY_ON_BOARD') {
        await page.getByRole('button', { name: 'Ghi nhận đã thu tiền', exact: true }).click();
        await page.getByRole('button', { name: 'Xác nhận đã thu tiền', exact: true }).click();
        await page.locator('.dossier-seat svg').waitFor();
      } else {
        await page.getByRole('button', { name: 'Tạo link thanh toán', exact: true }).click();
        const input = page.getByLabel('Link thanh toán', { exact: true }); await input.waitFor(); const link = await input.inputValue();
        await page.getByRole('button', { name: 'Copy link thanh toán', exact: true }).click();
        assert.equal(await page.evaluate(() => navigator.clipboard.readText()), link);
        const anon = await browser.newContext({ viewport: { width, height: 1000 } }); const payment = await anon.newPage();
        await payment.goto(link); await payment.getByRole('heading', { name: 'Thanh toán đặt vé', exact: true }).waitFor();
        await screenshot(payment, `${width}-anonymous-payment`);
        await payment.getByRole('button', { name: 'Xác nhận mô phỏng thanh toán', exact: true }).click();
        await payment.getByText('Đã thanh toán. Liên hệ nhà xe để nhận vé điện tử.', { exact: true }).waitFor();
        await payment.reload(); await payment.getByText('Đã thanh toán. Liên hệ nhà xe để nhận vé điện tử.', { exact: true }).waitFor();
        assert.equal(await payment.getByRole('button', { name: 'Xác nhận mô phỏng thanh toán', exact: true }).count(), 0);
        await anon.close(); await page.reload(); await page.locator('.dossier-seat svg').waitFor();
      }
      await screenshot(page, `${width}-${method}-paid`);
      results.push({ width, method, unpaidReserved: true, conflict: true, paidTicket: true });
    }
    assert.deepEqual(errors, []); await context.close();
  }
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } }); const page = await context.newPage();
  const email = `m16-browser-${Date.now()}@example.test`; const password = 'BrowserCustomer!2026';
  assert.equal((await call('POST', '/auth/register', null, { fullName: 'Browser Customer', email, phone: '090' + String(Date.now()).slice(-7), password })).status, 201);
  await uiLogin(page, email, password);
  const departureDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(pickup.plannedDepartureTime));
  await page.goto(origin + `/search?${journey}&departureDate=${departureDate}&pickupLabel=${encodeURIComponent(pickup.locationName)}&dropoffLabel=${encodeURIComponent(dropoff.locationName)}`);
  await page.getByRole('link', { name: 'Chọn chỗ', exact: true }).first().click();
  await page.locator('.seat:not(:disabled)').first().click(); await page.getByRole('button', { name: 'Tiếp tục', exact: true }).click();
  await page.getByLabel('Họ và tên', { exact: true }).fill('Browser Customer'); await page.getByLabel('Số điện thoại', { exact: true }).fill('0901234567');
  await page.getByRole('button', { name: 'Tiếp tục đến thanh toán', exact: true }).click();
  await page.getByRole('button', { name: 'Xác nhận thanh toán giả lập', exact: true }).click();
  await page.waitForURL('**/booking-success?bookingId=*'); await page.locator('.ticket-stub svg').first().waitFor();
  await screenshot(page, '1440-web-ticket'); results.push({ webCustomerBooking: true }); await context.close();
  const staffContext = await browser.newContext(); const staffPage = await staffContext.newPage();
  await uiLogin(staffPage, 'operator.staff@anphu-demo.example', 'DemoStaff!2026');
  await staffPage.goto(origin + '/operator/bookings/new'); await staffPage.getByText('403 — Không có quyền truy cập', { exact: true }).waitFor();
  await staffContext.close();
  const anonymous = await browser.newPage(); await anonymous.goto(origin + '/pay/' + 'x'.repeat(43));
  await anonymous.getByText('Link thanh toán không hợp lệ hoặc đã được thay thế. Vui lòng liên hệ nhà xe.', { exact: true }).waitFor();
  results.push({ invalidToken: true, staffCreateDenied: true });
  writeFileSync(new URL('results.json', directory), JSON.stringify(results, null, 2)); console.log(JSON.stringify(results));
} finally { await browser.close(); }
