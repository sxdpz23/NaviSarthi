import { test, type TestContext } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer, type Server } from 'node:net';
import { once } from 'node:events';
import { randomUUID } from 'node:crypto';
import { secret } from '../src/crypto.js';

async function listener(): Promise<{ server: Server; port: number }> {
  const server = createServer(); server.listen(0, '127.0.0.1'); await once(server, 'listening');
  return { server, port: (server.address() as { port: number }).port };
}
function start(t: TestContext, port: number, override: NodeJS.ProcessEnv = {}, args = ['dist/main.js']) {
  const env = { ...process.env };
  for (const name of Object.keys(env)) if (/^(ADMIN_|LIVEKIT_|FIREBASE_|FIRESTORE_|GOOGLE_APPLICATION_CREDENTIALS|MAINTENANCE_KEY)/.test(name)) delete env[name];
  Object.assign(env, { STORE: 'memory', NODE_ENV: 'development', HOST: '127.0.0.1', PORT: String(port) }, override);
  const child = spawn(process.execPath, args, { env, stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true });
  let output = ''; let errorOutput = '';
  child.stdout.on('data', data => { output = (output + data.toString()).slice(-8192); });
  child.stderr.on('data', data => { errorOutput = (errorOutput + data.toString()).slice(-8192); });
  const exited = once(child, 'exit');
  t.after(async () => {
    if (child.exitCode === null && child.signalCode === null) child.kill();
    await exited;
  });
  const waitForExit = async () => {
    const result = await Promise.race([exited, new Promise<never>((_, reject) => {
      const timer = setTimeout(() => reject(new Error('Server did not exit after startup failure')), 10_000); timer.unref();
    })]);
    return result[0];
  };
  return { child, waitForExit, output: () => output, errors: () => errorOutput };
}

test('compiled entrypoint serves a complete temporary ride flow over real HTTP', { timeout: 30_000 }, async t => {
  const allocated = await listener(); await new Promise<void>(resolve => allocated.server.close(() => resolve()));
  const runtime = start(t, allocated.port); const origin = `http://127.0.0.1:${allocated.port}`;
  for (let attempt = 0; !runtime.output().includes('"event":"listening"') && attempt < 100; attempt++) {
    assert.equal(runtime.child.exitCode, null, 'Server unexpectedly exited');
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert.ok(runtime.output().includes('"event":"listening"'));
  const health = await fetch(origin + '/healthz'); assert.equal(health.status, 200);
  assert.deepEqual(await health.json(), { status: 'ok' });
  const admissionHeaders = { 'content-type': 'application/json', 'x-installation-secret': secret(), 'idempotency-key': randomUUID() };
  const input = JSON.stringify({ name: 'Runtime verification', nickname: 'Lead', expiresAt: Date.now() + 3_600_000 });
  const first = await fetch(origin + '/v1/groups', { method: 'POST', headers: admissionHeaders, body: input });
  assert.equal(first.status, 201); const owner = await first.json() as { credential: string; groupId: string; memberId: string };
  const retried = await fetch(origin + '/v1/groups', { method: 'POST', headers: admissionHeaders, body: input });
  assert.deepEqual(await retried.json(), owner);
  const headers = { 'content-type': 'application/json', authorization: `Bearer ${owner.credential}` };
  const message = await fetch(origin + '/v1/messages', { method: 'POST', headers, body: JSON.stringify({ id: randomUUID(), text: 'Runtime check' }) });
  assert.equal(message.status, 200);
  assert.equal((await message.json() as { memberId: string }).memberId, owner.memberId);
  assert.equal((await fetch(origin + '/v1/session')).status, 401);
  const snapshot = await fetch(origin + '/v1/session', { headers }); assert.equal(snapshot.status, 200);
  assert.equal((await snapshot.json() as { group: { id: string } }).group.id, owner.groupId);
  const ended = await fetch(origin + '/v1/session/end', { method: 'POST', headers, body: '{}' }); assert.equal(ended.status, 200);
  assert.equal((await fetch(origin + '/v1/session', { headers })).status, 401);
  assert.ok(!runtime.output().includes(owner.credential)); assert.equal(runtime.errors(), '');
});
test('compiled entrypoint refuses memory storage in production', { timeout: 15_000 }, async t => {
  const runtime = start(t, 8080, { NODE_ENV: 'production', MAINTENANCE_KEY: secret() });
  assert.equal(await runtime.waitForExit(), 1); assert.ok(!runtime.output().includes('listening'));
  assert.match(runtime.errors(), /Server startup failed/);
});
test('compiled entrypoint refuses incomplete audio configuration', { timeout: 15_000 }, async t => {
  const runtime = start(t, 8080, { LIVEKIT_API_KEY: 'test-key' });
  assert.equal(await runtime.waitForExit(), 1); assert.ok(!runtime.output().includes('listening'));
});
test('compiled entrypoint closes resources and exits when its port is occupied', { timeout: 15_000 }, async t => {
  const occupied = await listener(); t.after(() => new Promise<void>(resolve => occupied.server.close(() => resolve())));
  const runtime = start(t, occupied.port);
  assert.equal(await runtime.waitForExit(), 1); assert.ok(!runtime.output().includes('listening'));
});
