import { test, type TestContext } from 'node:test';
import assert from 'node:assert/strict';
import { Script } from 'node:vm';
import { randomUUID } from 'node:crypto';
import { buildApp } from '../src/app.js';
import { Groups, DAY } from '../src/groups.js';
import { MemoryStore, MemoryLocations } from '../src/store.js';
import { disabledAudio } from '../src/audio.js';
import { secret } from '../src/crypto.js';
import { OperationsLog } from '../src/operations.js';

const passcode = 'test-admin-passcode';
async function fixture(t: TestContext, enabled = true) {
  let now = Date.now();
  const store = new MemoryStore();
  const groups = new Groups(store, new MemoryLocations(), disabledAudio, () => now);
  const app = await buildApp(groups, { adminPasscode: enabled ? passcode : undefined, secureAdminCookies: true });
  t.after(() => app.close());
  const login = () => app.inject({ method: 'POST', url: '/admin/login', payload: { passcode } });
  return { app, store, login, advance: (ms: number) => { now += ms; } };
}

test('dashboard has a runnable nonce-protected script, public aggregate counts and no passcode', async t => {
  const { app, advance } = await fixture(t);
  const page = await app.inject('/');
  assert.equal(page.statusCode, 200);
  assert.equal(page.headers['cache-control'], 'no-store');
  assert.ok(!page.body.includes(passcode));
  const script = page.body.match(/<script nonce="([^"]+)">([\s\S]*?)<\/script>/)!;
  assert.ok(script); new Script(script[2]!);
  assert.ok(String(page.headers['content-security-policy']).includes(`nonce-${script[1]}`));
  const created = await app.inject({ method: 'POST', url: '/v1/groups', headers: { 'x-installation-secret': secret(), 'idempotency-key': randomUUID() }, payload: { nickname: 'Private rider', name: 'Private ride', expiresAt: Date.now() + DAY } });
  assert.equal(created.statusCode, 201);
  const summary = await app.inject('/admin/summary');
  assert.equal(summary.json().activeRides, 1);
  assert.equal(summary.json().activeRiderSessions, 1);
  assert.equal(summary.json().realtimeConnections, 0);
  assert.ok(!summary.body.includes('Private'));
  advance(DAY + 10_000);
  assert.equal((await app.inject('/admin/summary')).json().activeRides, 0);
});

test('logs require passcode, secure cookie, expiry and logout; credentials stay out of events', async t => {
  const { app, login, advance } = await fixture(t);
  assert.equal((await app.inject('/admin/logs')).statusCode, 401);
  assert.equal((await app.inject({ method: 'POST', url: '/admin/login', payload: { passcode: 'wrong-passcode' } })).statusCode, 401);
  const accepted = await login(); assert.equal(accepted.statusCode, 200);
  const setCookie = String(accepted.headers['set-cookie']);
  for (const flag of ['HttpOnly', 'SameSite=Strict', 'Secure', 'Path=/admin']) assert.ok(setCookie.includes(flag));
  const cookie = setCookie.split(';')[0]!;
  const logs = await app.inject({ url: '/admin/logs', headers: { cookie } });
  assert.equal(logs.statusCode, 200);
  assert.ok(!logs.body.includes(cookie.split('=')[1]!)); assert.ok(!logs.body.includes(passcode));
  assert.equal((await app.inject({ url: '/admin/errors', headers: { cookie } })).statusCode, 200);
  await app.inject({ method: 'POST', url: '/admin/logout', headers: { cookie } });
  assert.equal((await app.inject({ url: '/admin/logs', headers: { cookie } })).statusCode, 401);
  const renewed = String((await login()).headers['set-cookie']).split(';')[0];
  advance(900_000);
  assert.equal((await app.inject({ url: '/admin/logs', headers: { cookie: renewed } })).statusCode, 401);
});

test('dashboard fonts are self-hosted, explicitly routed and compatible with its strict CSP', async t => {
  const { app } = await fixture(t);
  const page = await app.inject('/');
  assert.ok(String(page.headers['content-security-policy']).includes("font-src 'self'"));
  assert.ok(!page.body.includes('fonts.googleapis.com'));
  assert.ok(!page.body.includes('fonts.gstatic.com'));
  for (const name of ['instrument-sans-regular.ttf', 'instrument-sans-medium.ttf', 'instrument-sans-semibold.ttf', 'ibm-plex-mono-regular.ttf']) {
    const font = await app.inject(`/admin/assets/${name}`);
    assert.equal(font.statusCode, 200);
    assert.equal(font.headers['content-type'], 'font/ttf');
    assert.equal(font.rawPayload.readUInt32BE(0), 0x00010000);
  }
  assert.equal((await app.inject('/admin/assets/unknown.ttf')).statusCode, 404);
});

test('dashboard and protected diagnostics remain available during storage outage without leaking provider errors', async t => {
  const { app, store, login } = await fixture(t);
  const cookie = String((await login()).headers['set-cookie']).split(';')[0];
  store.list = async () => { throw Object.assign(new Error('private-provider-secret'), { code: 7 }); };
  assert.equal((await app.inject('/')).statusCode, 200);
  assert.equal((await app.inject('/admin/summary')).statusCode, 503);
  const errors = await app.inject({ url: '/admin/errors', headers: { cookie } });
  assert.equal(errors.statusCode, 200);
  assert.ok(errors.body.includes('UPSTREAM_RPC_7')); assert.ok(!errors.body.includes('private-provider-secret'));
});

test('login attempts are limited and missing passcode disables access', async t => {
  const { app } = await fixture(t);
  for (let i = 0; i < 10; i++) assert.equal((await app.inject({ method: 'POST', url: '/admin/login', payload: { passcode: 'incorrect' } })).statusCode, 401);
  assert.equal((await app.inject({ method: 'POST', url: '/admin/login', payload: { passcode } })).statusCode, 429);
  const disabled = await fixture(t, false);
  assert.equal((await disabled.login()).statusCode, 503);
});

test('operational events and errors have bounded independent retention', () => {
  const journal = new OperationsLog();
  for (let i = 0; i < 700; i++) journal.record('error', 'request', { status: 503 });
  assert.equal(journal.snapshot().length, 500); assert.equal(journal.snapshot(true).length, 200);
  const snapshot = journal.snapshot(); snapshot[0]!.status = 0;
  assert.equal(journal.snapshot()[0]!.status, 503);
});
