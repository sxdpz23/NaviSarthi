import { isDeepStrictEqual } from 'node:util';
import { COMMS_ROOT, LEGACY_COMMS_PATHS } from './paths.js';
import type { Store, RecordData } from './store.js';

export class LegacyCommsDataError extends Error {
  constructor() { super('Legacy Comms data exists. Stop the previous service and run npm run migrate:firestore (dry-run), then npm run migrate:firestore -- --apply before restarting.'); }
}
export class CommsMigrationConflictError extends Error {
  constructor() { super('Comms migration found different destination data. No conflicting document was overwritten.'); }
}
export async function assertNoLegacyCommsData(store: Store) {
  for (const collection of Object.keys(LEGACY_COMMS_PATHS)) {
    if ((await store.listDocuments(collection)).length) throw new LegacyCommsDataError();
  }
}
function validateNamespace(current: RecordData | undefined) {
  if (current?.schemaVersion !== undefined && current.schemaVersion !== 2) throw new Error('Unsupported Comms storage schema');
  if (current?.feature !== undefined && current.feature !== 'comms') throw new Error('Comms namespace is already in use');
}
export async function initialiseCommsNamespace(store: Store) {
  await store.transaction(async tx => {
    const current = await tx.get(COMMS_ROOT);
    validateNamespace(current);
    if (!current || current.schemaVersion === undefined || current.feature === undefined) {
      tx.set(COMMS_ROOT, { ...current, feature: 'comms', schemaVersion: 2 });
    }
  });
}

/** Explicit operator command only. Startup never silently moves or discards existing rides. */
export async function migrateLegacyComms(store: Store, apply = false) {
  validateNamespace(await store.get(COMMS_ROOT));
  const entries: Array<{ source: string; destination: string }> = [];
  const counts: Record<string, number> = {};
  for (const [legacy, destination] of Object.entries(LEGACY_COMMS_PATHS)) {
    counts[legacy] = 0;
    const visit = async (source: string) => {
      for (const collection of await store.listCollections(source)) {
        for (const child of await store.listDocuments(collection)) await visit(child);
      }
      const value = await store.get(source);
      if (value === undefined) return; // Children of a missing parent have still been visited.
      const target = destination + source.slice(legacy.length);
      const current = await store.get(target);
      if (current !== undefined && !isDeepStrictEqual(current, value)) throw new CommsMigrationConflictError();
      entries.push({ source, destination: target }); counts[legacy] = (counts[legacy] ?? 0) + 1;
    };
    for (const source of await store.listDocuments(legacy)) await visit(source);
  }
  let moved = 0;
  if (apply) {
    // Move each document atomically; an interrupted run can be resumed. The old service
    // must be stopped so it cannot create/update legacy records during this operation.
    for (const entry of entries) {
      const changed = await store.transaction(async tx => {
        const source = await tx.get(entry.source);
        const destination = await tx.get(entry.destination);
        const namespace = await tx.get(COMMS_ROOT);
        validateNamespace(namespace);
        if (source === undefined) return false;
        if (destination !== undefined && !isDeepStrictEqual(destination, source)) throw new CommsMigrationConflictError();
        if (destination === undefined) tx.set(entry.destination, source);
        tx.delete(entry.source); return true;
      });
      if (changed) moved++;
    }
    await assertNoLegacyCommsData(store);
    await initialiseCommsNamespace(store);
  }
  // Never return document contents, IDs, invitation hashes, or credentials in CLI logs.
  return { mode: apply ? 'apply' : 'dry-run', documents: entries.length, moved, collections: counts };
}
