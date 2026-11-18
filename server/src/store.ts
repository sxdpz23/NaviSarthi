export type RecordData = Record<string, any>;
export interface Filter { field: string; op: '==' | '<=' | '>'; value: unknown }
export interface Query { filters?: Filter[]; order?: string; limit?: number }
export interface Transaction {
  get<T = RecordData>(path: string): Promise<T | undefined>;
  set(path: string, value: RecordData): void;
  delete(path: string): void;
}
export interface Store {
  get<T = RecordData>(path: string): Promise<T | undefined>;
  transaction<T>(run: (tx: Transaction) => Promise<T>): Promise<T>;
  list<T = RecordData>(collection: string, query?: Query): Promise<Array<{ path: string; data: T }>>;
  watch(path: string, changed: () => void, failed: () => void): () => void;
  deleteTree(path: string): Promise<void>;
  close(): Promise<void>;
}
export interface Position { lat: number; lng: number; accuracyM: number; receivedAt: number; sharingId: string }
export interface Locations {
  get(groupId: string): Promise<Record<string, Position>>;
  set(groupId: string, memberId: string, value: Position | null, expiresAt?: number, onlySharingId?: string): Promise<void>;
  expired(now: number): Promise<string[]>;
  extendExpiry(groupId: string, expiresAt: number): Promise<void>;
  clear(groupId: string): Promise<void>;
  watch(groupId: string, changed: () => void, failed: () => void): () => void;
}

/** Serialised transactions model the same read-before-write contract as Firestore. Local/test only. */
export class MemoryStore implements Store {
  readonly records = new Map<string, RecordData>();
  private tail: Promise<unknown> = Promise.resolve();
  private listeners = new Map<string, Set<() => void>>();
  async get<T>(path: string): Promise<T | undefined> { return structuredClone(this.records.get(path)) as T | undefined; }
  async transaction<T>(run: (tx: Transaction) => Promise<T>): Promise<T> {
    const work = this.tail.then(async () => {
      const writes = new Map<string, RecordData | undefined>();
      let writing = false;
      const result = await run({
        get: async <V>(path: string) => {
          if (writing) throw new Error('Transaction reads must precede writes');
          return this.get<V>(path);
        },
        set: (path, value) => { writing = true; writes.set(path, structuredClone(value)); },
        delete: path => { writing = true; writes.set(path, undefined); },
      });
      for (const [path, value] of writes) {
        if (value === undefined) this.records.delete(path); else this.records.set(path, value);
        queueMicrotask(() => { for (const cb of this.listeners.get(path) ?? []) cb(); });
      }
      return result;
    });
    this.tail = work.catch(() => undefined);
    return work;
  }
  async list<T>(collection: string, query: Query = {}): Promise<Array<{ path: string; data: T }>> {
    let rows = [...this.records].filter(([path]) => path.startsWith(collection + '/') && path.split('/').length === collection.split('/').length + 1);
    for (const f of query.filters ?? []) rows = rows.filter(([, data]) => f.op === '==' ? data[f.field] === f.value : f.op === '<=' ? data[f.field] <= (f.value as any) : data[f.field] > (f.value as any));
    if (query.order) rows.sort((a, b) => a[1][query.order!] - b[1][query.order!]);
    return rows.slice(0, query.limit ?? rows.length).map(([path, data]) => ({ path, data: structuredClone(data) as T }));
  }
  watch(path: string, changed: () => void): () => void {
    const set = this.listeners.get(path) ?? new Set(); this.listeners.set(path, set); set.add(changed);
    return () => { set.delete(changed); if (!set.size) this.listeners.delete(path); };
  }
  async deleteTree(path: string): Promise<void> {
    await this.transaction(async tx => { for (const key of this.records.keys()) if (key === path || key.startsWith(path + '/')) tx.delete(key); });
  }
  async close() {}
}
export class MemoryLocations implements Locations {
  private data = new Map<string, Record<string, Position>>();
  private expiries = new Map<string, number>();
  private listeners = new Map<string, Set<() => void>>();
  async get(id: string) { return structuredClone(this.data.get(id) ?? {}); }
  async set(id: string, member: string, value: Position | null, expiresAt?: number, onlySharingId?: string) {
    const positions = this.data.get(id) ?? {};
    if (!value && onlySharingId && positions[member]?.sharingId !== onlySharingId) return;
    if (value && (!positions[member] || positions[member]!.receivedAt <= value.receivedAt)) positions[member] = structuredClone(value);
    else if (!value) delete positions[member];
    if (expiresAt !== undefined) this.expiries.set(id, expiresAt);
    this.data.set(id, positions); for (const cb of this.listeners.get(id) ?? []) queueMicrotask(cb);
  }
  async expired(now: number) { return [...this.expiries].filter(([, expiry]) => expiry <= now).slice(0, 100).map(([id]) => id); }
  async extendExpiry(id: string, expiresAt: number) { if (this.data.has(id)) this.expiries.set(id, expiresAt); }
  async clear(id: string) { this.data.delete(id); this.expiries.delete(id); for (const cb of this.listeners.get(id) ?? []) queueMicrotask(cb); }
  watch(id: string, changed: () => void) {
    const set = this.listeners.get(id) ?? new Set(); this.listeners.set(id, set); set.add(changed);
    return () => { set.delete(changed); if (!set.size) this.listeners.delete(id); };
  }
}
