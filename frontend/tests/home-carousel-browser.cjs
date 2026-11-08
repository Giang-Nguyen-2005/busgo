const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/Giang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const before = process.argv.includes('--before');
const phase = before ? 'before' : 'after';
const output = path.resolve(__dirname, '../../.tools/home-carousel', phase);
const cities = ['TP. Hồ Chí Minh', 'Đà Lạt', 'Buôn Ma Thuột', 'Nha Trang', 'Đà Nẵng', 'Huế'];
const locations = cities.map((name, i) => ({ id: i + 1, name }));
const routes = cities.slice(1, 5).map((city, i) => ({
  id: 100 + i, name: cities[0] + ' – ' + city, pickupLocationId: 1, dropoffLocationId: i + 2
}));
const emptyPage = { data: [], pagination: { page: 0, size: 6, totalPages: 0, totalElements: 0 } };

async function inspect(page, width) {
  await page.setViewportSize({ width, height: 900 });
  await page.goto(process.env.BUSGO_TEST_ORIGIN || 'http://127.0.0.1:5194');
  const destinations = page.locator('section[aria-labelledby="destinations-heading"]');
  const active = page.getByRole('heading', { name: 'Tuyến đang hoạt động', exact: true }).locator('..');
  await active.locator('.discovery-route').first().waitFor();
  await page.locator('img').evaluateAll(async images => {
    images.forEach(image => { image.loading = 'eager'; });
    await Promise.all(images.map(image => image.decode()));
  });
  const cards = destinations.locator('.destination-card');
  assert.deepEqual(await cards.locator('h3').allTextContents(), cities);
  assert.deepEqual(await active.locator('.route-card-copy strong').allTextContents(), routes.map(route => route.name));
  const links = await cards.getByRole('link').evaluateAll(nodes => nodes.map(node => node.href));
  assert.deepEqual(links, await active.locator('.discovery-route').evaluateAll(nodes => nodes.map(node => node.href)));
  assert.equal(await cards.getByText(/chưa có tuyến được giới thiệu/i).count(), 2);
  await destinations.screenshot({ path: path.join(output, 'destinations-' + width + '.png') });
  await active.screenshot({ path: path.join(output, 'routes-' + width + '.png') });
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  if (before) return;
  const track = destinations.getByRole('region', { name: 'Danh sách điểm đến' });
  const next = destinations.getByRole('button', { name: 'Điểm đến tiếp theo' });
  const prev = destinations.getByRole('button', { name: 'Điểm đến trước' });
  const metrics = await cards.evaluateAll(nodes => nodes.map(node => ({ top: node.offsetTop, width: node.getBoundingClientRect().width })));
  assert.equal(new Set(metrics.map(card => card.top)).size, 1);
  const visible = (await track.boundingBox()).width / metrics[0].width;
  assert(width < 600 ? visible >= 1.1 && visible <= 1.2 : Math.abs(visible - (width > 1000 ? 3 : 2)) < .2);
  const tops = await active.locator('.discovery-route').evaluateAll(nodes => nodes.map(node => node.offsetTop));
  assert.equal(new Set(tops).size, width < 600 ? 4 : 2);
  assert(await prev.isDisabled());
  await next.click();
  await page.waitForFunction(() => document.querySelector('.destination-track').scrollLeft > 10);
  assert(await prev.isEnabled());
  await prev.click();
  await page.waitForFunction(() => document.querySelector('[aria-label="Điểm đến trước"]').disabled);
  await track.focus();
  await page.keyboard.press('ArrowRight');
  await page.waitForFunction(() => document.querySelector('.destination-track').scrollLeft > 10);
  await page.keyboard.press('End');
  await page.waitForFunction(() => document.querySelector('[aria-label="Điểm đến tiếp theo"]').disabled);
  await page.keyboard.press('ArrowLeft');
  await next.waitFor({ state: 'visible' });
  await page.waitForFunction(() => !document.querySelector('[aria-label="Điểm đến tiếp theo"]').disabled);
  await page.keyboard.press('Home');
  await page.waitForFunction(() => document.querySelector('[aria-label="Điểm đến trước"]').disabled);
  if (width < 600) {
    await track.scrollIntoViewIfNeeded();
    const box = await track.boundingBox();
    const touch = await page.context().newCDPSession(page);
    const y = Math.round(box.y + box.height / 2);
    for (const [i, fraction] of [.8, .66, .5, .34, .18].entries()) {
      await touch.send('Input.dispatchTouchEvent', { type: i ? 'touchMove' : 'touchStart', touchPoints: [{ x: Math.round(box.x + box.width * fraction), y }] });
    }
    await touch.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await touch.detach();
    await page.waitForFunction(() => document.querySelector('.destination-track').scrollLeft > 30);
  }
}

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    const page = await browser.newPage({ hasTouch: true, locale: 'vi-VN', reducedMotion: 'reduce' });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/api/v1/**', route => {
      const endpoint = new URL(route.request().url()).pathname.replace('/api/v1', '');
      const responses = { '/locations': { data: locations }, '/public/discovery/routes': { data: routes }, '/public/discovery/trips': { data: [] }, '/public/discovery/operators': emptyPage, '/public/promotions': emptyPage, '/public/reviews/recent': emptyPage };
      assert(Object.hasOwn(responses, endpoint), endpoint);
      return route.fulfill({ json: responses[endpoint] });
    });
    for (const width of [1440, 834, 390, 360]) {
      await inspect(page, width);
      console.log(phase, width, 'PASS');
    }
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
