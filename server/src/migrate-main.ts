import { z } from 'zod';
import { firebaseStores } from './firebase.js';
import { migrateLegacyComms, CommsMigrationConflictError } from './migration.js';

async function main() {
  if (process.argv.slice(2).some(arg => arg !== '--apply')) throw new Error('Only --apply is accepted');
  const config = z.object({
    FIREBASE_PROJECT_ID: z.string().min(1),
    FIRESTORE_DATABASE_ID: z.string().regex(/^[a-z][a-z0-9-]{2,61}[a-z0-9]$/),
    FIRESTORE_EDITION: z.enum(['standard', 'enterprise']),
    FIREBASE_DATABASE_URL: z.url(),
  }).parse(process.env);
  const { store } = firebaseStores({ projectId: config.FIREBASE_PROJECT_ID, databaseId: config.FIRESTORE_DATABASE_ID, databaseURL: config.FIREBASE_DATABASE_URL });
  try { console.info(JSON.stringify(await migrateLegacyComms(store, process.argv.includes('--apply')))); }
  finally { await store.close(); }
}
main().catch(error => {
  console.error(error instanceof CommsMigrationConflictError ? error.message : 'Migration failed. Check configuration, provider access and the migration instructions; no conflicting data is overwritten.');
  process.exitCode = 1;
});
