import { test, type TestContext } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { once } from 'node:events';
import { WebSocket } from 'ws';
import { buildApp } from '../src/app.js';
import { Groups, DAY } from '../src/groups.js';
import { MemoryStore, MemoryLocations } from '../src/store.js';
import { disabledAudio, LiveKitAudio } from '../src/audio.js';
import { secret } from '../src/crypto.js';

async function fixture(t: TestContext) {
  let clock = Date.now();
  const groups = new Groups(new MemoryStore(), new MemoryLocations(), disabledAudio, () => clock);
  const maintenanceKey = secret();
  const app = await buildApp(groups, { maintenanceKey });
  t.after(() => app.close());
  async function create() {
    const response = await app.inject({ method: 'POST', url: '/v1/groups', headers: { 'x-installation-secret': secret(), 'idempotency-key': randomUUID() },
      payload: { nickname: 'Lead', name: 'Hill ride', expiresAt: clock + DAY } });
    assert.equal(response.statusCode, 201); return response.json();
  }
  return { app, groups, create, maintenanceKey, advance: (ms: number) => { clock += ms; } };
}
const auth = (credential: string) => ({ authorization: `Bearer ${credential}` });

test('session reconnect checks do not write transport rate counters to Firestore', async t => {
  const { app, create, groups } = await fixture(t);
  const owner = await create();
  let writes = 0;
  const transaction = groups.store.transaction.bind(groups.store);
  groups.store.transaction = async run => { writes++; return transaction(run); };
  for (let i = 0; i < 10; i++) {
    assert.equal((await app.inject({ url: '/v1/session', headers: auth(owner.credential) })).statusCode, 200);
  }
  assert.equal(writes, 0);
});

test('resource exhaustion reports capacity failure with retry guidance without leaking provider payloads', async t => {
  const { app, create, groups } = await fixture(t);
  const owner = await create();
  groups.store.get = async () => { throw Object.assign(new Error('private provider details'), { code: 8 }); };
  const response = await app.inject({ url: '/v1/session', headers: auth(owner.credential) });
  assert.equal(response.statusCode, 503);
  assert.equal(response.headers['retry-after'], '60');
  assert.deepEqual(response.json(), { error: 'SERVICE_CAPACITY_EXCEEDED' });
});

test('HTTP create and session work with our credential only, without Firebase Auth', async t => {
  const { app, create } = await fixture(t); const owner = await create();
  const snapshot = await app.inject({ url: '/v1/session', headers: auth(owner.credential) });
  assert.equal(snapshot.statusCode, 200);
  assert.equal(snapshot.json().memberId, owner.memberId);
  assert.equal(snapshot.headers['cache-control'], 'no-store');
  assert.equal(snapshot.headers['x-content-type-options'], 'nosniff');
  for (const authorization of [undefined, 'Bearer bad', `Bearer ${secret()}`, 'Bearer firebase-id-token']) {
    const denied = await app.inject({ url: '/v1/session', headers: authorization ? { authorization } : {} });
    assert.equal(denied.statusCode, 401);
  }
});
test('HTTP validates input and does not accept client-supplied authority', async t => {
  const { app, create } = await fixture(t); const owner = await create();
  for (const payload of [ { id: randomUUID(), text: 'Hi', memberId: 'someone-else' }, { id: randomUUID(), text: ' ' }, { id: randomUUID(), text: 'x'.repeat(2001) } ]) {
    assert.equal((await app.inject({ method: 'POST', url: '/v1/messages', headers: auth(owner.credential), payload })).statusCode, 400);
  }
  assert.equal((await app.inject({ method: 'PUT', url: '/v1/location', headers: auth(owner.credential), payload: { lat: 91, lng: 2, accuracyM: 5 } })).statusCode, 400);
  assert.equal((await app.inject({ url: `/v1/session?credential=${owner.credential}` })).statusCode, 400);
  const bad = await app.inject({ method: 'POST', url: '/v1/groups', payload: { nickname: 'Lead' } });
  assert.equal(bad.statusCode, 400);
  assert.deepEqual(bad.json(), { error: 'INVALID_REQUEST' });
});
test('HTTP invitation preview exposes no rider list and group members cannot end the ride', async t => {
  const { app, create } = await fixture(t); const owner = await create();
  const preview = await app.inject({ method: 'POST', url: '/v1/invitations/resolve', payload: { inviteToken: owner.inviteToken } });
  assert.equal(preview.statusCode, 200); assert.equal(preview.json().members, undefined);
  const joined = await app.inject({ method: 'POST', url: '/v1/groups/join', headers: { 'x-installation-secret': secret(), 'idempotency-key': randomUUID() }, payload: { nickname: 'Rider', inviteToken: owner.inviteToken } });
  assert.equal(joined.statusCode, 201);
  assert.equal((await app.inject({ method: 'POST', url: '/v1/session/end', headers: auth(joined.json().credential) })).statusCode, 403);
  assert.equal((await app.inject({ method: 'POST', url: '/v1/audio/token', headers: auth(owner.credential) })).statusCode, 503);
});
test('maintenance is secret-protected, deletes expired sessions, and is safe to repeat', async t => {
  const { app, create, advance, maintenanceKey } = await fixture(t); const owner = await create();
  assert.equal((await app.inject({ method: 'POST', url: '/internal/maintenance' })).statusCode, 401);
  assert.equal((await app.inject({ method: 'POST', url: '/internal/maintenance', headers: { 'x-maintenance-key': secret() } })).statusCode, 401);
  advance(DAY);
  const ended = await app.inject({ url: '/v1/session', headers: auth(owner.credential) }); assert.equal(ended.statusCode, 410);
  const cleanup = await app.inject({ method: 'POST', url: '/internal/maintenance', headers: { 'x-maintenance-key': maintenanceKey } });
  assert.deepEqual(cleanup.json(), { cleaned: 1, failed: 0 });
  assert.equal((await app.inject({ method: 'POST', url: '/internal/maintenance', headers: { 'x-maintenance-key': maintenanceKey } })).json().cleaned, 0);
});

function inbox(socket: WebSocket) {
  const queue: any[] = []; let waiting: ((value: any) => void) | undefined;
  socket.on('message', data => {
    const value = JSON.parse(data.toString());
    if (waiting) { const resolve = waiting; waiting = undefined; resolve(value); } else queue.push(value);
  });
  return async function next(type: string): Promise<any> {
    for (;;) {
      const value = queue.length ? queue.shift() : await new Promise<any>((resolve, reject) => {
        const timer = setTimeout(() => { waiting = undefined; reject(new Error(`Timeout waiting for ${type}`)); }, 3000);
        waiting = result => { clearTimeout(timer); resolve(result); };
      });
      if (value.type === type) return value;
    }
  };
}
test('real WebSockets stream chat and locations, reject ticket reuse, and close on removal', async t => {
  const { app, groups, create } = await fixture(t); const owner = await create();
  const rider = await groups.admit(secret(), randomUUID(), { nickname: 'Rider', inviteToken: owner.inviteToken });
  const address = await app.listen({ port: 0, host: '127.0.0.1' });
  const ticket = await groups.ticket(rider.credential);
  const socket = new WebSocket(address.replace('http', 'ws') + '/v1/events');
  t.after(() => socket.terminate()); const next = inbox(socket);
  await once(socket, 'open'); socket.send(JSON.stringify({ type: 'auth', ticket: ticket.ticket }));
  assert.equal((await next('snapshot')).memberId, rider.memberId);
  const sent = await groups.send(owner.credential, randomUUID(), 'Stop for tea');
  assert.equal((await next('messages')).messages[0].text, sent.text);
  const { sharingId } = await groups.startLocation(owner.credential);
  await groups.position(owner.credential, { lat: 12, lng: 77, accuracyM: 5, sharingId });
  let snap;
  do { snap = await next('snapshot'); } while (!snap.positions[owner.memberId]);
  assert.equal(snap.positions[owner.memberId].lat, 12);
  const duplicate = new WebSocket(address.replace('http', 'ws') + '/v1/events'); t.after(() => duplicate.terminate());
  await once(duplicate, 'open'); const duplicateClosed = once(duplicate, 'close');
  duplicate.send(JSON.stringify({ type: 'auth', ticket: ticket.ticket }));
  assert.equal((await duplicateClosed)[0], 4001);
  const closed = once(socket, 'close'); await groups.revoke(owner.credential, rider.memberId);
  assert.equal((await closed)[0], 4001);
});
test('WebSockets isolate unrelated groups and resume messages by sequence', async t => {
  const { app, groups, create } = await fixture(t); const a = await create(); const b = await create();
  await groups.send(a.credential, randomUUID(), 'Already seen');
  await groups.send(a.credential, randomUUID(), 'Resume here');
  await groups.send(b.credential, randomUUID(), 'Private to B');
  const address = await app.listen({ port: 0, host: '127.0.0.1' });
  const socket = new WebSocket(address.replace('http', 'ws') + '/v1/events'); t.after(() => socket.terminate());
  const next = inbox(socket); await once(socket, 'open');
  socket.send(JSON.stringify({ type: 'auth', ticket: (await groups.ticket(a.credential)).ticket, afterSeq: 1 }));
  assert.equal((await next('snapshot')).group.id, a.groupId);
  assert.deepEqual((await next('messages')).messages.map((m: any) => m.text), ['Resume here']);
});
test('GPS updates to connected riders do not use Firestore after location sharing starts', { timeout: 5000 }, async t => {
  const { app, groups, create, advance } = await fixture(t);
  const owner = await create();
  const riders = [owner, ...await Promise.all(Array.from({ length: 9 }, (_, i) =>
    groups.admit(secret(), randomUUID(), { nickname: `Rider ${i + 2}`, inviteToken: owner.inviteToken })))];
  const address = await app.listen({ port: 0, host: '127.0.0.1' });
  const sharing = await Promise.all(riders.map(rider => groups.startLocation(rider.credential)));
  const feeds = await Promise.all(riders.map(async rider => {
    const ticket = await groups.ticket(rider.credential);
    const socket = new WebSocket(address.replace('http', 'ws') + '/v1/events');
    t.after(() => socket.terminate());
    const next = inbox(socket);
    await once(socket, 'open');
    socket.send(JSON.stringify({ type: 'auth', ticket: ticket.ticket }));
    await next('snapshot');
    return next;
  }));
  let reads = 0;
  let queries = 0;
  let transactions = 0;
  const get = groups.store.get.bind(groups.store);
  const list = groups.store.list.bind(groups.store);
  const transaction = groups.store.transaction.bind(groups.store);
  groups.store.get = path => { reads++; return get(path); };
  groups.store.list = (path, query) => { queries++; return list(path, query); };
  groups.store.transaction = run => { transactions++; return transaction(run); };
  for (let i = 0; i < 12; i++) {
    advance(5000);
    const index = i % riders.length;
    const rider = riders[index];
    const result = await app.inject({
      method: 'PUT', url: '/v1/location', headers: auth(rider.credential),
      payload: { lat: 12 + i, lng: 77, accuracyM: 5, ...sharing[index] },
    });
    assert.equal(result.statusCode, 200);
    await Promise.all(feeds.map(async next => {
      let snapshot;
      do { snapshot = await next('snapshot'); }
      while (snapshot.positions[rider.memberId]?.lat !== 12 + i);
    }));
  }
  assert.equal(reads, 0);
  assert.equal(queries, 0);
  assert.equal(transactions, 0);
});
test('LiveKit credentials allow only microphone publishing in the scoped opaque room', async () => {
  const audio = new LiveKitAudio('wss://example.livekit.cloud', 'test-api-key', 'test-secret-not-a-real-provider-credential');
  const result = await audio.token('opaque-group', 'opaque-rider', Date.now() + DAY);
  const claims = JSON.parse(Buffer.from(result.token.split('.')[1]!, 'base64url').toString());
  assert.equal(claims.sub, 'opaque-rider'); assert.equal(claims.video.room, 'ride-opaque-group');
  assert.deepEqual(claims.video.canPublishSources, ['microphone']);
  assert.equal(claims.video.canPublishData, false);
  assert.ok(claims.exp - claims.nbf <= 300);
});
