import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createRequire } from 'node:module';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const { loadPlaywright, verifyLiveEnvironment } = require('./m30-4b-live.cjs');
const optIn = ['--allow-live-mutations'];

test('live mutation guard requires explicit CLI opt-in, including baseline mode', () => {
  for (const args of [[], ['--baseline']]) {
    assert.throws(() => verifyLiveEnvironment(args, { BUSGO_ALLOW_LIVE: 'true' }), /Live mutations are disabled/);
  }
});

test('live mutation guard refuses remote origins and ambiguous local URLs', () => {
  for (const origin of ['https://127.0.0.1:5190', 'http://example.test', 'http://127.0.0.1.example.test', 'http://user:pass@localhost:5190', 'http://localhost:5190/path', 'http://localhost:5190?db=test', 'http://localhost:5190#test']) {
    assert.throws(() => verifyLiveEnvironment(optIn, { BUSGO_TEST_ORIGIN: origin }), /local HTTP origin/);
  }
});

test('loopback and self-declared test schema never substitute for database verification', () => {
  for (const origin of ['http://127.0.0.1:5190', 'http://localhost:5190', 'http://[::1]:5190']) {
    assert.throws(() => verifyLiveEnvironment(optIn, { BUSGO_TEST_ORIGIN: origin, DB_URL: 'jdbc:mysql://127.0.0.1:3332/busgo_m30_4b_live', BUSGO_TEST_VERIFIED: 'true' }), /Cannot independently verify/);
  }
});

test('Playwright resolves through the existing override or a portable package name', () => {
  const playwright = { chromium: { launch() {} } };
  for (const [env, expected] of [[{}, 'playwright'], [{ PLAYWRIGHT_MODULE: 'custom-playwright' }, 'custom-playwright']]) {
    assert.equal(loadPlaywright(env, name => { assert.equal(name, expected); return playwright; }), playwright);
  }
});

test('missing or incompatible Playwright reports installation and override instructions', () => {
  for (const load of [() => { throw new Error('MODULE_NOT_FOUND'); }, () => ({})]) {
    assert.throws(() => loadPlaywright({}, load), /Install playwright.*PLAYWRIGHT_MODULE.*chromium.launch/);
  }
});

test('CLI rejects before loading Playwright or executing the live workflow', () => {
  const script = fileURLToPath(new URL('./m30-4b-live.cjs', import.meta.url));
  for (const args of [[], optIn, ['--baseline', ...optIn]]) {
    const result = spawnSync(process.execPath, [script, ...args], {
      env: { ...process.env, BUSGO_TEST_ORIGIN: 'http://127.0.0.1:5190', PLAYWRIGHT_MODULE: 'intentionally-unavailable-playwright' }, encoding: 'utf8', timeout: 5000,
    });
    assert.ifError(result.error);
    assert.equal(result.status, 1);
    assert.match(result.stderr, args.length ? /Cannot independently verify/ : /Live mutations are disabled/);
    assert.doesNotMatch(result.stderr, /Cannot load Playwright|MODULE_NOT_FOUND/);
  }
});
