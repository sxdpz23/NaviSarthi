import type { FastifyInstance } from 'fastify';
import { randomBytes } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { z } from 'zod';
import { Groups, type Group } from './groups.js';
import { COMMS_PATHS } from './paths.js';
import { ensure } from './errors.js';
import { AdminAccess, ADMIN_COOKIE } from './admin-access.js';
import { OperationsLog } from './operations.js';
import { dashboardHtml } from './dashboard.js';

export async function registerAdmin(app: FastifyInstance, groups: Groups, journal: OperationsLog, options: {
  passcode?: string; secureCookies?: boolean; connections: () => number;
}) {
  const access = await AdminAccess.create(options.passcode, groups.now);
  // Admin access and its limits must remain usable if Firebase becomes unavailable.
  app.addHook('onRequest', async request => {
    const route = request.routeOptions.url;
    if (route === '/' || route?.startsWith('/admin/')) {
      access.limit(`requests:${request.ip}`, 120, 60_000);
      access.limit('requests-global', 600, 60_000);
    }
  });
  const cookie = (value: string, maxAge: number) => `${ADMIN_COOKIE}=${value}; Path=/admin; HttpOnly; SameSite=Strict; Max-Age=${maxAge}${options.secureCookies ? '; Secure' : ''}`;
  let cached: { asOf: number; activeRides: number; activeRiderSessions: number } | undefined;
  let pending: Promise<NonNullable<typeof cached>> | undefined;
  app.get('/', async (_request, reply) => {
    const nonce = randomBytes(18).toString('base64');
    reply.header('Content-Security-Policy', `default-src 'none'; script-src 'nonce-${nonce}'; style-src 'nonce-${nonce}'; font-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'`);
    return reply.type('text/html; charset=utf-8').send(dashboardHtml(nonce));
  });
  // Explicit routes avoid arbitrary filesystem paths; fonts never call an external CDN.
  for (const name of ['instrument-sans-regular.ttf', 'instrument-sans-medium.ttf', 'instrument-sans-semibold.ttf', 'ibm-plex-mono-regular.ttf']) {
    app.get(`/admin/assets/${name}`, async (_request, reply) => {
      const font = await readFile(new URL(`../public/fonts/${name}`, import.meta.url));
      return reply.type('font/ttf').send(font);
    });
  }
  app.get('/admin/summary', async () => {
    if (!cached || groups.now() - cached.asOf >= 10_000) {
      pending ??= (async () => {
        const now = groups.now();
        const rows = await groups.store.list<Group>(COMMS_PATHS.sessions, { filters: [{ field: 'expiresAt', op: '>', value: now }] });
        const active = rows.map(row => row.data).filter(group => group.status === 'active' && group.expiresAt > groups.now());
        return { asOf: groups.now(), activeRides: active.length, activeRiderSessions: active.reduce((sum, group) => sum + Object.values(group.members).filter(member => member.status === 'active').length, 0) };
      })();
      try { cached = await pending; } finally { pending = undefined; }
    }
    return { ...cached, realtimeConnections: options.connections(), adminEnabled: access.enabled, startedAt: journal.startedAt };
  });
  app.post('/admin/login', async (request, reply) => {
    ensure(access.enabled, 503, 'ADMIN_NOT_CONFIGURED');
    const input = z.object({ passcode: z.string().min(1).max(128) }).strict().parse(request.body);
    access.limit(`login:${request.ip}`, 10, 10 * 60_000);
    access.limit('login-global', 60, 10 * 60_000);
    const token = await access.login(input.passcode);
    if (!token) { journal.record('warn', 'admin_denied'); ensure(false, 401, 'INVALID_PASSCODE'); }
    reply.header('Set-Cookie', cookie(token, 900)); journal.record('info', 'admin_login');
    return { ok: true, expiresInSeconds: 900 };
  });
  app.post('/admin/logout', async (request, reply) => {
    access.logout(request.headers.cookie); reply.header('Set-Cookie', cookie('', 0));
    journal.record('info', 'admin_logout'); return { ok: true };
  });
  for (const errorsOnly of [false, true]) app.get(errorsOnly ? '/admin/errors' : '/admin/logs', async request => {
    ensure(access.authorised(request.headers.cookie), 401, 'ADMIN_AUTH_REQUIRED');
    return { events: journal.snapshot(errorsOnly), startedAt: journal.startedAt, retention: { events: 500, errors: 200 }, scope: 'current-instance' };
  });
}
