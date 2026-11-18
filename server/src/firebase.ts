import { applicationDefault, initializeApp, deleteApp } from 'firebase-admin/app';
import { getFirestore, type Query as FirestoreQuery } from 'firebase-admin/firestore';
import { getDatabase } from 'firebase-admin/database';
import type { Store, Query, RecordData, Locations, Position } from './store.js';

export function firebaseStores(config: { projectId: string; databaseId: string; databaseURL: string }) {
  const app = initializeApp({ credential: applicationDefault(), projectId: config.projectId, databaseURL: config.databaseURL });
  const db = getFirestore(app, config.databaseId);
  const rtdb = getDatabase(app);
  const store: Store = {
    async get<T>(path: string) { return (await db.doc(path).get()).data() as T | undefined; },
    transaction: run => db.runTransaction(tx => run({
      get: async <T>(path: string) => (await tx.get(db.doc(path))).data() as T | undefined,
      set: (path, value) => { tx.set(db.doc(path), value); },
      delete: path => { tx.delete(db.doc(path)); },
    })),
    async list<T>(collection: string, options: Query = {}) {
      let query: FirestoreQuery = db.collection(collection);
      for (const filter of options.filters ?? []) query = query.where(filter.field, filter.op, filter.value);
      if (options.order) query = query.orderBy(options.order);
      if (options.limit) query = query.limit(options.limit);
      const snapshot = await query.get();
      return snapshot.docs.map(doc => ({ path: doc.ref.path, data: doc.data() as T }));
    },
    watch: (path, changed, failed) => db.doc(path).onSnapshot(snapshot => changed(snapshot.data()), failed),
    async listDocuments(collection) { return (await db.collection(collection).listDocuments()).map(doc => doc.path); },
    async listCollections(document) { return (await db.doc(document).listCollections()).map(collection => collection.path); },
    async deleteTree(path: string) { await db.recursiveDelete(db.doc(path)); },
    async close() { await db.terminate(); await deleteApp(app); },
  };
  const locations: Locations = {
    async transact(id, update) {
      await rtdb.ref(`ridePositions/${id}`).transaction(current => {
        const next = update(current ?? {});
        return Object.keys(next).length ? next : null;
      }, undefined, false);
    },
    async get(id) { return (await rtdb.ref(`ridePositions/${id}/positions`).get()).val() ?? {}; },
    async set(id, member, value, expiresAt, onlySharingId) {
      if (!value) {
        const ref = rtdb.ref(`ridePositions/${id}/positions/${member}`);
        if (onlySharingId) await ref.transaction(current => current?.sharingId === onlySharingId ? null : undefined);
        else await ref.remove();
        return;
      }
      await rtdb.ref(`ridePositions/${id}`).transaction(current => {
        const data = current ?? { positions: {} }; data.positions ??= {};
        if (!data.positions[member] || data.positions[member].receivedAt <= value.receivedAt) data.positions[member] = value;
        data.expiresAt = Math.max(data.expiresAt ?? 0, expiresAt ?? 0);
        return data;
      });
    },
    async expired(now) {
      const value = (await rtdb.ref('ridePositions').orderByChild('expiresAt').endAt(now).limitToFirst(100).get()).val();
      return Object.keys(value ?? {});
    },
    async extendExpiry(id, expiresAt) {
      await rtdb.ref(`ridePositions/${id}`).transaction(current => current ? { ...current, expiresAt: Math.max(current.expiresAt ?? 0, expiresAt) } : undefined);
    },
    async clear(id) { await rtdb.ref(`ridePositions/${id}`).remove(); },
    watch(id, changed, failed) {
      const ref = rtdb.ref(`ridePositions/${id}`);
      ref.on('value', changed, failed);
      return () => ref.off('value', changed);
    },
  };
  return { store, locations };
}
