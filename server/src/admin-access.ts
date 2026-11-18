import { randomBytes, scrypt, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';
import { hash, secret } from './crypto.js';
import { ensure } from './errors.js';

const derive = promisify(scrypt);
export const ADMIN_COOKIE = 'navisarthi_admin';
export class AdminAccess {
  get enabled() { return this.verifier !== undefined; }
  private salt = randomBytes(16);
  private verifier?: Buffer;
  private sessions = new Map<string, number>();
  private rates = new Map<string, { count: number; expiresAt: number }>();
  private lastRatePrune = 0;
  private constructor(private now: () => number) {}
  static async create(passcode: string | undefined, now: () => number = Date.now) {
    const access = new AdminAccess(now);
    if (passcode !== undefined) {
      if (passcode.length < 8 || passcode.length > 128) throw new Error('Admin passcode must contain 8–128 characters');
      access.verifier = await derive(passcode, access.salt, 32) as Buffer;
    }
    return access;
  }
  async login(passcode: string): Promise<string | undefined> {
    if (!this.verifier || passcode.length > 128) return;
    const supplied = await derive(passcode, this.salt, 32) as Buffer;
    if (!timingSafeEqual(this.verifier, supplied)) return;
    this.prune();
    if (this.sessions.size >= 32) this.sessions.delete(this.sessions.keys().next().value!);
    const token = secret(); this.sessions.set(hash(token), this.now() + 15 * 60_000); return token;
  }
  authorised(cookie: string | undefined) {
    this.prune(); const token = this.token(cookie);
    return token !== undefined && this.sessions.has(hash(token));
  }
  logout(cookie: string | undefined) { const token = this.token(cookie); if (token) this.sessions.delete(hash(token)); }
  limit(key: string, limit: number, durationMs: number) {
    const now = this.now();
    if (now - this.lastRatePrune >= 30_000) {
      for (const [id, bucket] of this.rates) if (bucket.expiresAt <= now) this.rates.delete(id);
      this.lastRatePrune = now;
    }
    let bucket = this.rates.get(key);
    if (!bucket || bucket.expiresAt <= now) {
      ensure(this.rates.has(key) || this.rates.size < 2048, 429, 'ADMIN_RATE_LIMITED');
      bucket = { count: 0, expiresAt: now + durationMs }; this.rates.set(key, bucket);
    }
    ensure(bucket.count < limit, 429, 'ADMIN_RATE_LIMITED'); bucket.count++;
  }
  private prune() { for (const [key, expiresAt] of this.sessions) if (expiresAt <= this.now()) this.sessions.delete(key); }
  private token(cookie: string | undefined) {
    const value = cookie?.split(';').map(part => part.trim()).find(part => part.startsWith(ADMIN_COOKIE + '='))?.slice(ADMIN_COOKIE.length + 1);
    return value && /^[A-Za-z0-9_-]{43}$/.test(value) ? value : undefined;
  }
}
