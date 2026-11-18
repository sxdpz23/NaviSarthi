import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { firebaseStores } from '../src/firebase.js';
import { Groups, DAY } from '../src/groups.js';
import { disabledAudio } from '../src/audio.js';
import { secret } from '../src/crypto.js';

test('Firebase emulator: actual persistence, subscriptions, rules and cleanup', { timeout: 60_000 }, async t => {
  const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
  const databaseHost = process.env.FIREBASE_DATABASE_EMULATOR_HOST;
  assert.match(firestoreHost ?? '', /^127\.0\.0\.1:\d+$/, 'Use the local Firestore emulator');
  assert.match(databaseHost ?? '', /^127\.0\.0\.1:\d+$/, 'Use the local RTDB emulator');
  const projectId = 'demo-ride-groups';
  const stores = firebaseStores({ projectId, databaseId: 'groups', databaseURL: `https://${projectId}.firebaseio.com` });
  const groups = new Groups(stores.store, stores.locations, disabledAudio);
  let groupId: string | undefined;
  t.after(async () => {
    if (groupId) { await stores.locations.clear(groupId); await stores.store.deleteTree(groups.groupPath(groupId)); }
    await stores.store.close();
  });
  const installation = secret(); const requestId = randomUUID();
  const input = { nickname: 'Lead', name: 'Emulator ride', expiresAt: Date.now() + DAY };
  const owner = await groups.admit(installation, requestId, input); groupId = owner.groupId;
  assert.deepEqual(await groups.admit(installation, requestId, input), owner);
  const rider = await groups.admit(secret(), randomUUID(), { nickname: 'Rider', inviteToken: owner.inviteToken });
  await groups.send(owner.credential, randomUUID(), 'Persisted through Admin SDK');
  assert.equal((await groups.messages(rider.credential)).messages.length, 1);
  const { sharingId } = await groups.startLocation(rider.credential);
  await groups.position(rider.credential, { lat: 12, lng: 77, accuracyM: 5, sharingId });
  assert.equal((await groups.snapshot(owner.credential)).positions[rider.memberId]?.lat, 12);
  await groups.ticket(rider.credential);
  const firestoreDenied = await fetch(`http://${firestoreHost}/v1/projects/${projectId}/databases/groups/documents/rideGroups/${groupId}`);
  assert.equal(firestoreDenied.status, 403, 'Unauthenticated clients must not read group documents');
  const rtdbDenied = await fetch(`http://${databaseHost}/ridePositions/${groupId}.json?ns=${projectId}`);
  assert.equal(rtdbDenied.status, 401, 'Unauthenticated clients must not read shared locations');
  let changed = false;
  const stop = stores.store.watch(groups.groupPath(groupId), () => { changed = true; }, () => { throw new Error('Firestore listener failed'); });
  await groups.extend(owner.credential, Date.now() + 2 * DAY);
  await new Promise(resolve => setTimeout(resolve, 500)); stop(); assert.equal(changed, true);
  assert.equal((await groups.end(owner.credential)).cleanupPending, false);
  assert.equal(await stores.store.get(groups.groupPath(groupId)), undefined);
  assert.deepEqual(await stores.locations.get(groupId), {});
  await assert.rejects(groups.auth(rider.credential));
});
