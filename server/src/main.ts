import { z } from 'zod';
import { buildApp } from './app.js';
import { Groups } from './groups.js';
import { MemoryStore, MemoryLocations } from './store.js';
import { firebaseStores } from './firebase.js';
import { disabledAudio, LiveKitAudio } from './audio.js';

async function main() {
  const config = z.object({
    NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
    STORE: z.enum(['memory', 'firebase']).default('firebase'),
    PORT: z.coerce.number().int().min(1).max(65535).default(8080),
    HOST: z.string().default('127.0.0.1'),
    FIREBASE_PROJECT_ID: z.string().optional(), FIRESTORE_DATABASE_ID: z.string().optional(),
    FIRESTORE_EDITION: z.enum(['standard', 'enterprise']).optional(),
    FIREBASE_DATABASE_URL: z.url().optional(),
    MAINTENANCE_KEY: z.string().min(43).optional(),
    LIVEKIT_URL: z.url().optional(), LIVEKIT_API_KEY: z.string().optional(), LIVEKIT_API_SECRET: z.string().optional(),
  }).parse(process.env);
  if (config.NODE_ENV === 'production' && (config.STORE === 'memory' || !config.MAINTENANCE_KEY)) {
    throw new Error('Production requires Firebase storage and a maintenance key');
  }
  let stores;
  if (config.STORE === 'memory') stores = { store: new MemoryStore(), locations: new MemoryLocations() };
  else {
    if (!config.FIREBASE_PROJECT_ID || !config.FIRESTORE_DATABASE_ID || !config.FIREBASE_DATABASE_URL || !config.FIRESTORE_EDITION) {
      throw new Error('Firebase project, dedicated database ID, confirmed edition and RTDB URL are required');
    }
    if (config.FIRESTORE_DATABASE_ID === '(default)') throw new Error('Use a dedicated named Groups Firestore database');
    stores = firebaseStores({ projectId: config.FIREBASE_PROJECT_ID, databaseId: config.FIRESTORE_DATABASE_ID, databaseURL: config.FIREBASE_DATABASE_URL });
  }
  const anyAudio = config.LIVEKIT_URL || config.LIVEKIT_API_KEY || config.LIVEKIT_API_SECRET;
  if (anyAudio && !(config.LIVEKIT_URL && config.LIVEKIT_API_KEY && config.LIVEKIT_API_SECRET)) throw new Error('All LiveKit settings are required together');
  const audio = config.LIVEKIT_URL && config.LIVEKIT_API_KEY && config.LIVEKIT_API_SECRET
    ? new LiveKitAudio(config.LIVEKIT_URL, config.LIVEKIT_API_KEY, config.LIVEKIT_API_SECRET) : disabledAudio;
  const app = await buildApp(new Groups(stores.store, stores.locations, audio), {
    maintenanceKey: config.MAINTENANCE_KEY, backgroundMaintenance: true, logger: true,
  });
  for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => { void app.close(); });
  await app.listen({ port: config.PORT, host: config.HOST });
  console.info(JSON.stringify({ event: 'listening', port: config.PORT, storage: config.STORE, audioEnabled: audio.enabled }));
}
main().catch(() => { console.error('Server startup failed. Check configuration and provider access.'); process.exitCode = 1; });
