import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { Groups, DAY, type Group } from '../src/groups.js';
import { MemoryStore, MemoryLocations } from '../src/store.js';
import { disabledAudio, type Audio } from '../src/audio.js';
import { secret, hash } from '../src/crypto.js';
import { ApiError } from '../src/errors.js';
import { COMMS_PATHS } from '../src/paths.js';

function fixture(audio: Audio = disabledAudio) {
  let clock = 1_800_000_000_000;
  const store = new MemoryStore(); const locations = new MemoryLocations();
  const groups = new Groups(store, locations, audio, () => clock);
  const create = (installation = secret(), requestId = randomUUID()) => groups.admit(installation, requestId, { nickname: 'Lead', name: 'Weekend ride', expiresAt: clock + DAY });
  const join = (inviteToken: string, installation = secret()) => groups.admit(installation, randomUUID(), { nickname: 'Rider', inviteToken });
  return { groups, store, locations, create, join, advance: (ms: number) => { clock += ms; }, now: () => clock };
}
const code = (expected: string) => (e: unknown) => e instanceof ApiError && e.code === expected;

test('admission issues private credentials, 32-character QR, and safe member snapshots', async () => {
  const f = fixture(); const owner = await f.create();
  assert.match(owner.credential, /^[A-Za-z0-9_-]{43}$/);
  assert.match(owner.inviteToken!, /^[A-Za-z0-9]{32}$/);
  const snapshot = await f.groups.snapshot(owner.credential);
  const json = JSON.stringify(snapshot);
  for (const forbidden of ['credentialHash', 'installationHash', 'receiptId', owner.credential, owner.inviteToken!]) assert.ok(!json.includes(forbidden));
  assert.equal(snapshot.group.members[0]?.nickname, 'Lead');
  assert.ok(!JSON.stringify([...f.store.records]).includes(owner.credential));
});
test('create retry returns the original response without storing plaintext credentials', async () => {
  const f = fixture(); const installation = secret(); const requestId = randomUUID();
  const first = await f.create(installation, requestId); const retry = await f.create(installation, requestId);
  assert.deepEqual(retry, first);
  assert.equal((await f.store.list(COMMS_PATHS.sessions)).length, 1);
  await assert.rejects(f.groups.admit(installation, requestId, { nickname: 'Changed', name: 'Weekend ride', expiresAt: f.now() + DAY }), code('IDEMPOTENCY_CONFLICT'));
});
test('join retry recovers membership and concurrent joins never exceed twelve', async () => {
  const f = fixture(); const owner = await f.create();
  const installation = secret(); const requestId = randomUUID();
  const input = { nickname: 'Second', inviteToken: owner.inviteToken! };
  assert.deepEqual(await f.groups.admit(installation, requestId, input), await f.groups.admit(installation, requestId, input));
  const attempts = await Promise.allSettled(Array.from({ length: 20 }, () => f.join(owner.inviteToken!)));
  assert.equal(attempts.filter(a => a.status === 'fulfilled').length, 10);
  assert.equal((await f.groups.snapshot(owner.credential)).group.members.length, 12);
});
test('an installation cannot concurrently join different groups', async () => {
  const f = fixture(); const a = await f.create(); const b = await f.create(); const installation = secret();
  const attempts = await Promise.allSettled([f.join(a.inviteToken!, installation), f.join(b.inviteToken!, installation)]);
  assert.equal(attempts.filter(a => a.status === 'fulfilled').length, 1);
});
test('members may extend only within fourteen days; stale cleanup cannot delete an extended group', async () => {
  const f = fixture(); const owner = await f.create(); const member = await f.join(owner.inviteToken!);
  await assert.rejects(f.groups.extend(member.credential, f.now() + 1000), code('INVALID_EXTENSION'));
  await assert.rejects(f.groups.extend(member.credential, f.now() + 15 * DAY), code('INVALID_EXTENSION'));
  await f.groups.extend(member.credential, f.now() + 14 * DAY);
  f.advance(DAY + 1);
  assert.equal(await f.groups.cleanup(owner.groupId), false);
  assert.equal((await f.groups.snapshot(owner.credential)).group.expiresAt, f.now() + 13 * DAY - 1);
});
test('rotation disables old QR without disconnecting existing riders', async () => {
  const f = fixture(); const owner = await f.create(); const rider = await f.join(owner.inviteToken!);
  await assert.rejects(f.groups.rotate(rider.credential), code('OWNER_REQUIRED'));
  const rotated = await f.groups.rotate(owner.credential);
  await assert.rejects(f.groups.resolve(owner.inviteToken!), code('INVITE_UNAVAILABLE'));
  await f.join(rotated.inviteToken);
  await f.groups.auth(rider.credential);
});
test('removal revokes credentials and blocks the same installation from rejoining', async () => {
  const f = fixture(); const owner = await f.create(); const installation = secret(); const rider = await f.join(owner.inviteToken!, installation);
  const { sharingId } = await f.groups.startLocation(rider.credential);
  await f.groups.position(rider.credential, { lat: 10, lng: 20, accuracyM: 5, sharingId });
  await f.groups.revoke(owner.credential, rider.memberId);
  await assert.rejects(f.groups.auth(rider.credential), code('INVALID_CREDENTIAL'));
  await assert.rejects(f.join(owner.inviteToken!, installation), code('REMOVED_FROM_GROUP'));
  assert.deepEqual(await f.locations.get(owner.groupId), {});
});
test('ownership transfers before leaving; a former member may join another group', async () => {
  const f = fixture(); const install = secret(); const owner = await f.create(install); const rider = await f.join(owner.inviteToken!);
  await assert.rejects(f.groups.revoke(owner.credential), code('TRANSFER_OR_END_REQUIRED'));
  await f.groups.transfer(owner.credential, rider.memberId);
  await f.groups.revoke(owner.credential);
  await assert.rejects(f.groups.auth(owner.credential), code('INVALID_CREDENTIAL'));
  await f.create(install);
});
test('text retries deduplicate, sender is derived, and rate limits are transactional', async () => {
  const f = fixture(); const owner = await f.create(); const messageId = randomUUID();
  const first = await f.groups.send(owner.credential, messageId, 'Meet at the bridge');
  assert.deepEqual(await f.groups.send(owner.credential, messageId, 'Meet at the bridge'), first);
  assert.equal(first.memberId, owner.memberId);
  await assert.rejects(f.groups.send(owner.credential, messageId, 'Edited'), code('MESSAGE_ID_CONFLICT'));
  const attempts = await Promise.allSettled(Array.from({ length: 20 }, () => f.groups.send(owner.credential, randomUUID(), 'Hello')));
  assert.equal(attempts.filter(a => a.status === 'fulfilled').length, 9);
  const page = await f.groups.messages(owner.credential, 0, 3);
  assert.deepEqual(page.messages.map(m => m.seq), [1, 2, 3]);
  assert.equal((await f.groups.messages(owner.credential, page.nextAfter, 100)).messages.length, 7);
});
test('locations are latest-only, disappear on clear, and expire from visible snapshots', async () => {
  const f = fixture(); const owner = await f.create();
  const { sharingId } = await f.groups.startLocation(owner.credential);
  await f.groups.position(owner.credential, { lat: 1, lng: 2, accuracyM: 3, sharingId });
  await assert.rejects(f.groups.position(owner.credential, { lat: 2, lng: 3, accuracyM: 3, sharingId }), code('RATE_LIMITED'));
  f.advance(1000);
  await f.groups.position(owner.credential, { lat: 4, lng: 5, accuracyM: 3, sharingId });
  assert.equal((await f.groups.snapshot(owner.credential)).positions[owner.memberId]?.lat, 4);
  f.advance(120_001);
  assert.deepEqual((await f.groups.snapshot(owner.credential)).positions, {});
  await f.groups.position(owner.credential, null);
  assert.deepEqual(await f.locations.get(owner.groupId), {});
});
test('realtime tickets are single-use, expire, and cannot outlive revocation', async () => {
  const f = fixture(); const owner = await f.create(); const rider = await f.join(owner.inviteToken!);
  const ticket = await f.groups.ticket(rider.credential);
  const attempts = await Promise.allSettled([f.groups.consumeTicket(ticket.ticket), f.groups.consumeTicket(ticket.ticket)]);
  assert.equal(attempts.filter(a => a.status === 'fulfilled').length, 1);
  const expired = await f.groups.ticket(rider.credential); f.advance(30_001);
  await assert.rejects(f.groups.consumeTicket(expired.ticket), code('INVALID_TICKET'));
  const revoked = await f.groups.ticket(rider.credential); await f.groups.revoke(owner.credential, rider.memberId);
  await assert.rejects(f.groups.consumeTicket(revoked.ticket), code('CREDENTIAL_REVOKED'));
});
test('expiry denies access immediately and cleanup removes all ride data', async () => {
  const f = fixture(); const owner = await f.create();
  await f.groups.send(owner.credential, randomUUID(), 'Temporary');
  const { sharingId } = await f.groups.startLocation(owner.credential);
  await f.groups.position(owner.credential, { lat: 1, lng: 2, accuracyM: 3, sharingId });
  await f.groups.ticket(owner.credential);
  f.advance(DAY);
  await assert.rejects(f.groups.auth(owner.credential), code('SESSION_ENDED'));
  await assert.rejects(f.groups.extend(owner.credential, f.now() + DAY), code('SESSION_ENDED'));
  assert.equal((await f.groups.maintenance()).cleaned, 1);
  assert.equal([...f.store.records.keys()].filter(k => !k.startsWith(COMMS_PATHS.rates + '/')).length, 0);
  assert.deepEqual(await f.locations.get(owner.groupId), {});
  assert.equal(await f.groups.cleanup(owner.groupId), false);
});
test('cleanup of an old group preserves a newer installation membership', async () => {
  const f = fixture(); const installation = secret(); const old = await f.create(installation);
  f.advance(DAY); const current = await f.create(installation);
  await f.groups.cleanup(old.groupId);
  await f.groups.auth(current.credential);
  assert.equal((await f.store.get<any>(`${COMMS_PATHS.installations}/${hash(installation)}`)).groupId, current.groupId);
});
test('provider failures retain cleanup work and maintenance retries successfully', async () => {
  let fail = true; const removed: string[] = [];
  const audio: Audio = { enabled: true, token: disabledAudio.token,
    async remove(_group, member) { if (fail) throw new Error('Provider down'); removed.push(member); },
    async end() { if (fail) throw new Error('Provider down'); },
  };
  const f = fixture(audio); const owner = await f.create(); const rider = await f.join(owner.inviteToken!);
  assert.equal((await f.groups.revoke(owner.credential, rider.memberId)).cleanupPending, true);
  await assert.rejects(f.groups.auth(rider.credential), code('INVALID_CREDENTIAL'));
  assert.equal((await f.groups.maintenance()).failed, 1);
  fail = false; await f.groups.maintenance(); assert.deepEqual(removed, [rider.memberId]);
  fail = true; assert.equal((await f.groups.end(owner.credential)).cleanupPending, true);
  assert.equal((await f.store.get<Group>(f.groups.groupPath(owner.groupId)))?.status, 'ended');
  fail = false; assert.equal((await f.groups.maintenance()).cleaned, 1);
});
test('unconfigured audio fails closed', async () => {
  const f = fixture(); const owner = await f.create();
  await assert.rejects(f.groups.audioToken(owner.credential), code('AUDIO_NOT_CONFIGURED'));
});
test('maintenance removes a late orphaned RTDB location but preserves extended rides', async () => {
  const f = fixture(); const expired = await f.create(); const extended = await f.create();
  await f.groups.extend(extended.credential, f.now() + 2 * DAY);
  const oldExpiry = f.now() + DAY;
  await f.locations.set(extended.groupId, extended.memberId, { lat: 1, lng: 2, accuracyM: 3, receivedAt: f.now(), sharingId: randomUUID() }, oldExpiry);
  f.advance(DAY); await f.groups.cleanup(expired.groupId);
  // Simulate a network write completing after the session's Firestore tree was deleted.
  await f.locations.set(expired.groupId, expired.memberId, { lat: 4, lng: 5, accuracyM: 3, receivedAt: f.now() - 1, sharingId: randomUUID() }, oldExpiry);
  await f.groups.maintenance();
  assert.deepEqual(await f.locations.get(expired.groupId), {});
  assert.equal((await f.locations.get(extended.groupId))[extended.memberId]?.lat, 1);
});
test('stopping location rejects queued publishes; old sharing runs cannot clear a new run', async () => {
  const f = fixture(); const owner = await f.create();
  const first = await f.groups.startLocation(owner.credential);
  await f.groups.position(owner.credential, { lat: 1, lng: 2, accuracyM: 3, sharingId: first.sharingId });
  await f.groups.position(owner.credential, null);
  f.advance(1000);
  await assert.rejects(f.groups.position(owner.credential, { lat: 4, lng: 5, accuracyM: 3, sharingId: first.sharingId }), code('LOCATION_SHARING_STOPPED'));
  const second = await f.groups.startLocation(owner.credential); f.advance(1000);
  await f.groups.position(owner.credential, { lat: 6, lng: 7, accuracyM: 3, sharingId: second.sharingId });
  await f.locations.set(owner.groupId, owner.memberId, null, undefined, first.sharingId);
  assert.equal((await f.groups.snapshot(owner.credential)).positions[owner.memberId]?.lat, 6);
});
test('a failed recursive delete is recoverable even after the group parent was deleted', async () => {
  const f = fixture(); const owner = await f.create();
  await f.groups.send(owner.credential, randomUUID(), 'Must be wiped');
  const deleteTree = f.store.deleteTree.bind(f.store);
  f.store.deleteTree = async path => {
    await f.store.transaction(async tx => { tx.delete(path); });
    throw new Error('Simulated interruption after deleting parent');
  };
  assert.equal((await f.groups.end(owner.credential)).cleanupPending, true);
  assert.equal(await f.store.get(f.groups.groupPath(owner.groupId)), undefined);
  assert.ok(await f.store.get(`${COMMS_PATHS.cleanup}/${owner.groupId}`));
  f.store.deleteTree = deleteTree;
  assert.equal((await f.groups.maintenance()).cleaned, 1);
  assert.equal([...f.store.records.keys()].filter(k => !k.startsWith(COMMS_PATHS.rates + '/')).length, 0);
});
test('loss of audio configuration retains cleanup rather than treating an existing room as closed', async () => {
  const audio: Audio = { enabled: true, async token() { return { url: 'wss://test.invalid', token: 'test-only' }; }, async remove() {}, async end() {} };
  const f = fixture(audio); const owner = await f.create();
  await f.groups.audioToken(owner.credential);
  const restarted = new Groups(f.store, f.locations, disabledAudio, f.now);
  assert.equal((await restarted.end(owner.credential)).cleanupPending, true);
  assert.ok(await f.store.get(`${COMMS_PATHS.cleanup}/${owner.groupId}`));
  assert.equal((await f.groups.maintenance()).cleaned, 1);
});
