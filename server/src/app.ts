import Fastify, { type FastifyRequest } from 'fastify';
import websocket from '@fastify/websocket';
import helmet from '@fastify/helmet';
import { timingSafeEqual } from 'node:crypto';
import { z, ZodError } from 'zod';
import { Groups } from './groups.js';
import { ApiError, ensure } from './errors.js';
import { hash } from './crypto.js';
import { registerAdmin } from './admin.js';
import { OperationsLog } from './operations.js';

const secretSchema = z.string().regex(/^[A-Za-z0-9_-]{43}$/);
const id = z.string().uuid();
const nickname = z.string().trim().min(1).max(40);
const epoch = z.number().int().positive().max(Number.MAX_SAFE_INTEGER);
const invite = z.string().regex(/^[A-Za-z0-9]{32}$/);
const position = z.object({ lat: z.number().min(-90).max(90), lng: z.number().min(-180).max(180), accuracyM: z.number().min(0).max(10000), sharingId: id }).strict();
const body = <T>(schema: z.ZodType<T>, value: unknown) => schema.parse(value);
const bearer = (header?: string) => {
  ensure(typeof header === 'string' && header.startsWith('Bearer '), 401, 'CREDENTIAL_REQUIRED');
  const parsed = secretSchema.safeParse(header.slice(7));
  ensure(parsed.success, 401, 'INVALID_CREDENTIAL'); return parsed.data;
};

export async function buildApp(groups: Groups, options: { maintenanceKey?: string; backgroundMaintenance?: boolean; logger?: boolean; adminPasscode?: string; secureAdminCookies?: boolean; operationsLog?: OperationsLog } = {}) {
  // Operational metadata only: never raw URLs, bodies, headers, credentials or QR values.
  const app = Fastify({ logger: false, bodyLimit: 16 * 1024, trustProxy: false, requestTimeout: 15_000 });
  const journal = options.operationsLog ?? new OperationsLog(groups.now);
  const errors = new WeakMap<FastifyRequest, string>();
  const authenticatedSockets = new Set<import('ws').WebSocket>();
  await app.register(helmet);
  await app.register(websocket, { options: { maxPayload: 4096, perMessageDeflate: false } });
  app.addHook('onRequest', async (request, reply) => {
    reply.header('Cache-Control', 'no-store');
    if (request.url.split('?')[0] === '/healthz' || request.url.split('?')[0] === '/internal/maintenance') return;
    ensure(!request.url.includes('?'), 400, 'QUERY_NOT_SUPPORTED');
    const route = request.routeOptions.url;
    if (route === '/' || route?.startsWith('/admin/')) return;
    await groups.rate(`ip:${request.ip}`, 600);
    if (request.headers.authorization) {
      const token = bearer(request.headers.authorization);
      await groups.auth(token);
      await groups.rate(`member:${hash(token)}`, 180);
    }
  });
  app.addHook('onResponse', async (request, reply) => {
    const route = request.routeOptions.url ?? 'unmatched';
    if ((route === '/healthz' || route === '/admin/summary') && reply.statusCode < 400) return;
    journal.record(reply.statusCode >= 500 ? 'error' : reply.statusCode >= 400 ? 'warn' : 'info', 'request', {
      method: request.method, route, status: reply.statusCode, durationMs: Math.round(reply.elapsedTime),
      ...(errors.has(request) ? { code: errors.get(request)! } : {}),
    });
  });
  app.setErrorHandler((error, request, reply) => {
    if (error instanceof ApiError) {
      errors.set(request, error.code);
      if (error.status === 429) reply.header('Retry-After', '60');
      return reply.code(error.status).send({ error: error.code });
    }
    const status = (error as { statusCode?: number })?.statusCode;
    if (error instanceof ZodError || status === 400 || status === 413 || status === 415) {
      errors.set(request, 'INVALID_REQUEST');
      return reply.code(status ?? 400).send({ error: 'INVALID_REQUEST' });
    }
    // Intentionally exclude exception messages, which can contain provider payloads or URLs.
    const providerCode = (error as { code?: unknown })?.code;
    errors.set(request, typeof providerCode === 'number' && Number.isInteger(providerCode) && providerCode >= 0 && providerCode <= 16 ? `UPSTREAM_RPC_${providerCode}` : 'INTERNAL_ERROR');
    if (options.logger) console.error(JSON.stringify({ event: 'request_failed', category: 'internal' }));
    return reply.code(503).send({ error: 'SERVICE_UNAVAILABLE' });
  });
  app.get('/healthz', async () => ({ status: 'ok' }));
  await registerAdmin(app, groups, journal, { passcode: options.adminPasscode, secureCookies: options.secureAdminCookies, connections: () => authenticatedSockets.size });
  app.post('/v1/groups', async (request, reply) => {
    const installation = secretSchema.parse(request.headers['x-installation-secret']);
    const requestId = id.parse(request.headers['idempotency-key']);
    const input = body(z.object({ nickname, name: z.string().trim().min(1).max(80), expiresAt: epoch }).strict(), request.body);
    await groups.rate(`create:${request.ip}`, 5, 3_600_000);
    return reply.code(201).send(await groups.admit(installation, requestId, input));
  });
  app.post('/v1/invitations/resolve', async request => {
    await groups.rate(`invite:${request.ip}`, 30);
    return groups.resolve(body(z.object({ inviteToken: invite }).strict(), request.body).inviteToken);
  });
  app.post('/v1/groups/join', async (request, reply) => {
    const installation = secretSchema.parse(request.headers['x-installation-secret']);
    const requestId = id.parse(request.headers['idempotency-key']);
    await groups.rate(`join:${request.ip}`, 30);
    const input = body(z.object({ nickname, inviteToken: invite }).strict(), request.body);
    return reply.code(201).send(await groups.admit(installation, requestId, input));
  });
  app.get('/v1/session', request => groups.snapshot(bearer(request.headers.authorization)));
  app.post('/v1/session/extend', request => groups.extend(bearer(request.headers.authorization), body(z.object({ expiresAt: epoch }).strict(), request.body).expiresAt));
  app.post('/v1/session/invitation', request => groups.rotate(bearer(request.headers.authorization)));
  app.post('/v1/session/transfer', async request => {
    await groups.transfer(bearer(request.headers.authorization), body(z.object({ memberId: id }).strict(), request.body).memberId); return { ok: true };
  });
  app.post('/v1/session/leave', request => groups.revoke(bearer(request.headers.authorization)));
  app.post('/v1/session/remove', async request => {
    return groups.revoke(bearer(request.headers.authorization), body(z.object({ memberId: id }).strict(), request.body).memberId);
  });
  app.post('/v1/session/end', request => groups.end(bearer(request.headers.authorization)));
  app.post('/v1/messages/list', request => {
    const input = body(z.object({ afterSeq: z.number().int().min(0).max(10000).default(0), limit: z.number().int().min(1).max(100).default(50) }).strict(), request.body ?? {});
    return groups.messages(bearer(request.headers.authorization), input.afterSeq, input.limit);
  });
  app.post('/v1/messages', request => {
    const input = body(z.object({ id, text: z.string().trim().min(1).max(2000) }).strict(), request.body);
    return groups.send(bearer(request.headers.authorization), input.id, input.text);
  });
  app.post('/v1/location/start', request => groups.startLocation(bearer(request.headers.authorization)));
  app.put('/v1/location', async request => { await groups.position(bearer(request.headers.authorization), body(position, request.body)); return { ok: true }; });
  app.delete('/v1/location', async request => { await groups.position(bearer(request.headers.authorization), null); return { ok: true }; });
  app.post('/v1/realtime-ticket', request => groups.ticket(bearer(request.headers.authorization)));
  app.post('/v1/audio/token', request => groups.audioToken(bearer(request.headers.authorization)));
  app.post('/internal/maintenance', async (request, reply) => {
    const key = request.headers['x-maintenance-key'];
    ensure(options.maintenanceKey && typeof key === 'string' && timingSafeEqual(Buffer.from(hash(key)), Buffer.from(hash(options.maintenanceKey))), 401, 'UNAUTHORISED');
    const result = await groups.maintenance();
    journal.record(result.failed ? 'error' : 'info', 'maintenance', result);
    if (result.failed) reply.code(503);
    return result;
  });

  const sockets = new Set<import('ws').WebSocket>();
  app.get('/v1/events', { websocket: true }, (socket, request) => {
    sockets.add(socket);
    let identity: Awaited<ReturnType<Groups['consumeTicket']>> | undefined;
    let authenticating = false; let busy = false; let dirty = false; let cursor = 0;
    let unwatchGroup = () => {}; let unwatchPositions = () => {};
    let deadline: ReturnType<typeof setTimeout> | undefined;
    const close = () => socket.close(4001, 'Session unavailable');
    const authTimer = setTimeout(close, 5000);
    let alive = true;
    socket.on('pong', () => { alive = true; });
    const heartbeat = setInterval(() => {
      if (!alive) { socket.terminate(); return; }
      alive = false; socket.ping(); void refresh();
    }, 15_000);
    const send = (value: unknown) => {
      if (socket.readyState !== 1) return;
      if (socket.bufferedAmount > 256 * 1024) { socket.close(4008, 'Reconnect and resume'); return; }
      socket.send(JSON.stringify(value));
    };
    async function refresh() {
      if (!identity || socket.readyState !== 1) return;
      if (busy) { dirty = true; return; }
      busy = true;
      try {
        do {
          dirty = false;
          const snapshot = await groups.snapshotHash(identity.credentialHash);
          const rows = await groups.store.list(`${groups.groupPath(identity.groupId)}/messages`, {
            filters: [{ field: 'seq', op: '>', value: cursor }], order: 'seq', limit: 100,
          });
          await groups.authHash(identity.credentialHash);
          send({ type: 'snapshot', ...snapshot });
          if (rows.length) {
            send({ type: 'messages', messages: rows.map(r => r.data) });
            cursor = rows.at(-1)!.data.seq;
            if (rows.length === 100) dirty = true;
          }
          clearTimeout(deadline);
          deadline = setTimeout(close, Math.max(0, snapshot.group.expiresAt - groups.now()));
        } while (dirty && socket.readyState === 1);
      } catch { close(); }
      finally { busy = false; }
    }
    socket.on('message', (data, binary) => {
      if (identity || authenticating || binary) { socket.close(4000, 'Unexpected message'); return; }
      authenticating = true;
      void (async () => {
        try {
          const input = z.object({ type: z.literal('auth'), ticket: secretSchema, afterSeq: z.number().int().min(0).max(10000).default(0) }).strict().parse(JSON.parse(data.toString()));
          identity = await groups.consumeTicket(input.ticket); cursor = input.afterSeq;
          clearTimeout(authTimer);
          if (socket.readyState !== 1) return;
          authenticatedSockets.add(socket); journal.record('info', 'websocket_open');
          unwatchGroup = groups.store.watch(groups.groupPath(identity.groupId), () => { void refresh(); }, close);
          unwatchPositions = groups.locations.watch(identity.groupId, () => { void refresh(); }, close);
          await refresh();
        } catch { close(); }
      })();
    });
    socket.on('error', () => { socket.terminate(); });
    socket.on('close', () => {
      clearTimeout(authTimer); clearTimeout(deadline); clearInterval(heartbeat);
      unwatchGroup(); unwatchPositions(); sockets.delete(socket);
      if (authenticatedSockets.delete(socket)) journal.record('info', 'websocket_closed');
    });
  });
  let timer: ReturnType<typeof setInterval> | undefined;
  if (options.backgroundMaintenance) {
    let running = false;
    timer = setInterval(() => {
      if (running) return;
      running = true;
      void groups.maintenance().then(result => {
        if (result.failed || result.cleaned) journal.record(result.failed ? 'error' : 'info', 'maintenance', result);
        if (options.logger && result.failed) console.error(JSON.stringify({ event: 'maintenance_failed', count: result.failed }));
      }).catch(() => { journal.record('error', 'maintenance', { code: 'MAINTENANCE_FAILED' }); if (options.logger) console.error(JSON.stringify({ event: 'maintenance_failed' })); }).finally(() => { running = false; });
    }, 60_000);
    timer.unref();
  }
  app.addHook('onListen', async () => { journal.record('info', 'service_ready'); });
  app.addHook('preClose', async () => { journal.record('info', 'service_stopping'); clearInterval(timer); for (const socket of sockets) socket.terminate(); });
  app.addHook('onClose', async () => { await groups.store.close(); });
  return app;
}
