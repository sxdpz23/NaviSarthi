import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { Groups, DAY } from '../src/groups.js';
import { MemoryStore, MemoryLocations } from '../src/store.js';
import { disabledAudio } from '../src/audio.js';
import { secret } from '../src/crypto.js';
import { COMMS_ROOT, COMMS_PATHS, LEGACY_COMMS_PATHS } from '../src/paths.js';
import { assertNoLegacyCommsData, initialiseCommsNamespace, migrateLegacyComms, LegacyCommsDataError, CommsMigrationConflictError } from '../src/migration.js';

async function legacyFixture() {
  const store = new MemoryStore(); const locations = new MemoryLocations();
  let now = 1_800_000_000_000;
  const groups = new Groups(store, locations, disabledAudio, () => now);
  const installation = secret(); const requestId = randomUUID();
  const input = { nickname: 'Lead', name: 'Original ride', expiresAt: now + DAY };
  const owner = await groups.admit(installation, requestId, input);
  await groups.send(owner.credential, randomUUID(), 'Keep my message');
  const ticket = await groups.ticket(owner.credential);
  await groups.rate('test-rate', 10);
  const original = structuredClone([...store.records]);
  await store.transaction(async tx => {
    for (const [path, value] of original) {
      const match = Object.entries(LEGACY_COMMS_PATHS).find(([, prefix]) => path.startsWith(prefix + '/'))!;
      tx.set(match[0] + path.slice(match[1].length), value); tx.delete(path);
    }
  });
  return { store, locations, groups, owner, installation, requestId, input, ticket, original, advance: (ms: number) => { now += ms; } };
}

test('all new writes are under features/comms; expiry never deletes another feature or active ride', async () => {
  const store = new MemoryStore(); const locations = new MemoryLocations(); let now = 1_800_000_000_000;
  await initialiseCommsNamespace(store);
  const other = { name: 'Future data', expiresAt: 0, cleanupAt: 0 };
  await store.transaction(async tx => { tx.set('features/garage/sessions/future-ride', other); tx.set('users/alice/records/future', other); });
  const groups = new Groups(store, locations, disabledAudio, () => now);
  const ended = await groups.admit(secret(), randomUUID(), { name: 'Temporary', nickname: 'Lead', expiresAt: now + DAY });
  const active = await groups.admit(secret(), randomUUID(), { name: 'Other ride', nickname: 'Lead', expiresAt: now + 2 * DAY });
  await groups.send(ended.credential, randomUUID(), 'Ephemeral'); await groups.ticket(ended.credential); await groups.rate('rate', 1);
  assert.ok([...store.records.keys()].filter(path => !path.startsWith('users/') && !path.startsWith('features/garage/')).every(path => path === COMMS_ROOT || path.startsWith(COMMS_ROOT + '/')));
  now += DAY; await groups.maintenance();
  await groups.auth(active.credential);
  assert.equal(await store.get(groups.groupPath(ended.groupId)), undefined);
  assert.deepEqual(await store.get('features/garage/sessions/future-ride'), other);
  assert.deepEqual(await store.get('users/alice/records/future'), other);
  assert.deepEqual(await store.get(COMMS_ROOT), { feature: 'comms', schemaVersion: 2 });
});
test('namespace initialisation preserves metadata and refuses an unsupported schema', async () => {
  const store = new MemoryStore();
  await store.transaction(async tx => { tx.set(COMMS_ROOT, { note: 'Preserve me' }); });
  await initialiseCommsNamespace(store); await initialiseCommsNamespace(store);
  assert.deepEqual(await store.get(COMMS_ROOT), { note: 'Preserve me', feature: 'comms', schemaVersion: 2 });
  await store.transaction(async tx => { tx.set(COMMS_ROOT, { feature: 'comms', schemaVersion: 3 }); });
  await assert.rejects(initialiseCommsNamespace(store), /Unsupported/);
});
test('startup detects legacy data without changing it, including orphan subcollections', async () => {
  const store = new MemoryStore();
  await store.transaction(async tx => { tx.set('rideGroups/missing-parent/messages/message', { text: 'Orphan' }); });
  const before = structuredClone([...store.records]);
  await assert.rejects(assertNoLegacyCommsData(store), LegacyCommsDataError);
  assert.deepEqual([...store.records], before);
  const empty = new MemoryStore(); await assertNoLegacyCommsData(empty);
});
test('migration dry-run is read-only and does not disclose document contents', async () => {
  const f = await legacyFixture(); const before = structuredClone([...f.store.records]);
  const report = await migrateLegacyComms(f.store);
  assert.equal(report.mode, 'dry-run'); assert.ok(report.documents > 5); assert.equal(report.moved, 0);
  assert.deepEqual([...f.store.records], before);
  assert.ok(!JSON.stringify(report).includes(f.owner.credential));
  assert.ok(!JSON.stringify(report).includes('Keep my message'));
});
test('migration preserves credentials, invite, admission retry, tickets, messages and sequence', async () => {
  const f = await legacyFixture();
  await f.store.transaction(async tx => { tx.set('features/future/settings/one', { keep: true }); });
  const report = await migrateLegacyComms(f.store, true);
  assert.equal(report.moved, report.documents);
  await assertNoLegacyCommsData(f.store);
  for (const [path, data] of f.original) assert.deepEqual(await f.store.get(path), data);
  assert.deepEqual(await f.groups.admit(f.installation, f.requestId, f.input), f.owner);
  await f.groups.auth(f.owner.credential);
  assert.equal((await f.groups.resolve(f.owner.inviteToken!)).name, 'Original ride');
  assert.equal((await f.groups.messages(f.owner.credential)).messages[0]?.text, 'Keep my message');
  assert.equal((await f.groups.consumeTicket(f.ticket.ticket)).memberId, f.owner.memberId);
  assert.deepEqual(await f.store.get('features/future/settings/one'), { keep: true });
  f.advance(DAY); await f.groups.maintenance();
  assert.equal(await f.store.get(f.groups.groupPath(f.owner.groupId)), undefined);
  assert.deepEqual(await f.store.get('features/future/settings/one'), { keep: true });
});
test('migration is resumable after an interrupted atomic move', async () => {
  const f = await legacyFixture(); const transaction = f.store.transaction.bind(f.store);
  let calls = 0;
  f.store.transaction = async run => { if (++calls === 3) throw new Error('Interrupted'); return transaction(run); };
  await assert.rejects(migrateLegacyComms(f.store, true), /Interrupted/);
  await assert.rejects(assertNoLegacyCommsData(f.store), LegacyCommsDataError);
  f.store.transaction = transaction;
  await migrateLegacyComms(f.store, true);
  await f.groups.auth(f.owner.credential);
  assert.equal((await f.groups.messages(f.owner.credential)).messages.length, 1);
  assert.equal((await migrateLegacyComms(f.store, true)).moved, 0);
});
test('migration rejects destination conflicts before moving any records', async () => {
  const f = await legacyFixture();
  await f.store.transaction(async tx => { tx.set(f.groups.groupPath(f.owner.groupId), { different: true }); });
  const before = structuredClone([...f.store.records]);
  await assert.rejects(migrateLegacyComms(f.store, true), CommsMigrationConflictError);
  assert.deepEqual([...f.store.records], before);
});
test('migration refuses an incompatible feature or schema before moving any records', async () => {
  for (const metadata of [{ feature: 'comms', schemaVersion: 3 }, { feature: 'garage', schemaVersion: 2 }]) {
    const f = await legacyFixture();
    await f.store.transaction(async tx => { tx.set(COMMS_ROOT, metadata); });
    const before = structuredClone([...f.store.records]);
    await assert.rejects(migrateLegacyComms(f.store));
    await assert.rejects(migrateLegacyComms(f.store, true));
    assert.deepEqual([...f.store.records], before);
  }
});
test('migration rechecks the namespace atomically if its schema changes after preflight', async () => {
  const f = await legacyFixture(); const transaction = f.store.transaction.bind(f.store);
  let changed = false;
  f.store.transaction = async run => {
    if (!changed) {
      changed = true;
      await transaction(async tx => { tx.set(COMMS_ROOT, { feature: 'comms', schemaVersion: 3 }); });
    }
    return transaction(run);
  };
  const before = structuredClone([...f.store.records]);
  await assert.rejects(migrateLegacyComms(f.store, true), /Unsupported/);
  assert.deepEqual([...f.store.records].filter(([path]) => path !== COMMS_ROOT), before);
});
test('migration includes children whose original parent document has already been deleted', async () => {
  const store = new MemoryStore();
  await store.transaction(async tx => { tx.set('rideGroups/old/messages/message', { seq: 1 }); tx.set('rideCleanup/old', { id: 'old' }); });
  const result = await migrateLegacyComms(store, true);
  assert.equal(result.moved, 2);
  assert.deepEqual(await store.get(`${COMMS_PATHS.sessions}/old/messages/message`), { seq: 1 });
  assert.deepEqual(await store.get(`${COMMS_PATHS.cleanup}/old`), { id: 'old' });
  await assertNoLegacyCommsData(store);
});
