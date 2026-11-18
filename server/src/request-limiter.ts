import { ensure } from './errors.js';

/** Transport limits for the single server instance, without a database write per request. */
export class RequestLimiter {
  private buckets = new Map<string, { count: number; expiresAt: number }>();
  private nextPrune = 0;
  constructor(private now: () => number) {}
  check(key: string, limit: number, durationMs = 60_000) {
    const now = this.now();
    if (now >= this.nextPrune) {
      for (const [id, bucket] of this.buckets) if (bucket.expiresAt <= now) this.buckets.delete(id);
      this.nextPrune = now + 60_000;
    }
    let bucket = this.buckets.get(key);
    if (!bucket || bucket.expiresAt <= now) {
      ensure(this.buckets.has(key) || this.buckets.size < 10000, 429, 'RATE_LIMITED');
      bucket = { count: 0, expiresAt: now + durationMs };
      this.buckets.set(key, bucket);
    }
    ensure(bucket.count < limit, 429, 'RATE_LIMITED');
    bucket.count++;
  }
}
