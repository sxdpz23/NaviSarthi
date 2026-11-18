import { randomUUID } from 'node:crypto';
import { hash, invitation, secret, seal, unseal } from './crypto.js';
import { ApiError, ensure, maintenanceErrorCode } from './errors.js';
import type { Store, Transaction, Locations } from './store.js';
import type { Audio } from './audio.js';
import { COMMS_PATHS } from './paths.js';
import { RequestLimiter } from './request-limiter.js';

export const DAY = 86_400_000;
export interface Member {
  id: string; nickname: string; status: 'active' | 'left' | 'removed';
  credentialHash: string; installationHash: string; receiptId: string; joinedAt: number;
  sharingId: string | null;
}
export interface Group {
  id: string; name: string; createdAt: number; expiresAt: number; cleanupAt: number;
  status: 'active' | 'ended'; ownerId: string; inviteHash: string;
  members: Record<string, Member>; revision: number; messageSeq: number;
  pendingAudio: string[]; pendingAudioCount: number; audioUsed: boolean;
}
interface Credential { groupId: string; memberId: string }
interface Admission { groupId: string; memberId: string; credential: string; inviteToken?: string; expiresAt: number }
interface Receipt { groupId: string; inputHash: string; ciphertext: string }
export interface Message { id: string; memberId: string; text: string; seq: number; sentAt: number }
export class Groups {
  private readonly locationLimits: RequestLimiter;
  constructor(readonly store: Store, readonly locations: Locations, readonly audio: Audio, readonly now: () => number = Date.now) {
    this.locationLimits = new RequestLimiter(now);
  }
  groupPath(id: string) { return `${COMMS_PATHS.sessions}/${id}`; }
  private live(group: Group | undefined): asserts group is Group {
    ensure(group && group.status === 'active' && group.expiresAt > this.now(), 410, 'SESSION_ENDED');
  }
  private active(group: Group, memberId: string) {
    this.live(group);
    const member = group.members[memberId];
    ensure(member?.status === 'active', 401, 'CREDENTIAL_REVOKED');
    return member;
  }
  async auth(credential: string, tx?: Transaction) {
    return this.authHash(hash(credential), tx);
  }
  async authHash(credentialHash: string, tx?: Transaction) {
    const read = tx ? tx.get.bind(tx) : this.store.get.bind(this.store);
    const ref = await read<Credential>(`${COMMS_PATHS.credentials}/${credentialHash}`);
    ensure(ref, 401, 'INVALID_CREDENTIAL');
    const group = await read<Group>(this.groupPath(ref.groupId));
    this.live(group);
    const member = this.active(group, ref.memberId);
    ensure(member.credentialHash === credentialHash, 401, 'CREDENTIAL_REVOKED');
    return { group, member };
  }
  publicGroup(group: Group) {
    return {
      id: group.id, name: group.name, createdAt: group.createdAt, expiresAt: group.expiresAt,
      ownerId: group.ownerId, revision: group.revision, messageSeq: group.messageSeq,
      audioAvailable: this.audio.enabled,
      members: Object.values(group.members).filter(m => m.status === 'active').map(m => ({ id: m.id, nickname: m.nickname, joinedAt: m.joinedAt })),
    };
  }
  async snapshot(credential: string) {
    return this.snapshotHash(hash(credential));
  }
  async snapshotHash(credentialHash: string) {
    const { group, member } = await this.authHash(credentialHash);
    const positions = await this.locations.get(group.id);
    const current = await this.authHash(credentialHash); // Recheck after external I/O, including removal/expiry.
    return this.snapshotView(current.group, member.id, positions);
  }
  snapshotView(group: Group, memberId: string, positions: Awaited<ReturnType<Locations['get']>>) {
    const visible = Object.fromEntries(Object.entries(positions).filter(([id, p]) => group.members[id]?.status === 'active' && group.members[id]?.sharingId === p.sharingId && p.receivedAt > this.now() - 120_000));
    return { group: this.publicGroup(group), memberId, positions: visible, serverTime: this.now() };
  }
  async admit(installationSecret: string, requestId: string, input: { nickname: string; name?: string; expiresAt?: number; inviteToken?: string }): Promise<Admission> {
    const installationHash = hash(installationSecret);
    const receiptId = hash(`${installationHash}:${requestId}`);
    const inputHash = hash(JSON.stringify(input));
    const credential = secret(); const memberId = randomUUID(); const groupId = randomUUID();
    const inviteToken = invitation();
    return this.store.transaction(async tx => {
      const receipt = await tx.get<Receipt>(`${COMMS_PATHS.receipts}/${receiptId}`);
      if (receipt) {
        ensure(receipt.inputHash === inputHash, 409, 'IDEMPOTENCY_CONFLICT');
        const response = unseal<Admission>(receipt.ciphertext, installationSecret);
        await this.auth(response.credential, tx);
        return response;
      }
      const existing = await tx.get<Credential>(`${COMMS_PATHS.installations}/${installationHash}`);
      const previous = existing ? await tx.get<Group>(this.groupPath(existing.groupId)) : undefined;
      ensure(!previous || previous.status !== 'active' || previous.expiresAt <= this.now() || previous.members[existing!.memberId]?.status !== 'active', 409, 'ALREADY_IN_GROUP');
      let group: Group;
      if (input.inviteToken) {
        const invitationRef = await tx.get<{ groupId: string }>(`${COMMS_PATHS.invitations}/${hash(input.inviteToken)}`);
        ensure(invitationRef, 404, 'INVITE_UNAVAILABLE');
        const joined = await tx.get<Group>(this.groupPath(invitationRef.groupId));
        this.live(joined); group = joined;
        ensure(group.inviteHash === hash(input.inviteToken), 404, 'INVITE_UNAVAILABLE');
        ensure(!Object.values(group.members).some(m => m.installationHash === installationHash && m.status === 'removed'), 403, 'REMOVED_FROM_GROUP');
        ensure(Object.values(group.members).filter(m => m.status === 'active').length < 12, 409, 'GROUP_FULL');
        // Bound the embedded membership record well below Firestore's document limit.
        ensure(Object.keys(group.members).length < 120, 409, 'ADMISSION_LIMIT');
      } else {
        const now = this.now();
        ensure(input.expiresAt && input.expiresAt > now && input.expiresAt <= now + 14 * DAY, 400, 'INVALID_EXPIRY');
        group = { id: groupId, name: input.name!, createdAt: now, expiresAt: input.expiresAt, cleanupAt: input.expiresAt,
          status: 'active', ownerId: memberId, inviteHash: hash(inviteToken), members: {}, revision: 0, messageSeq: 0, pendingAudio: [], pendingAudioCount: 0, audioUsed: false };
      }
      group.members[memberId] = { id: memberId, nickname: input.nickname, status: 'active', credentialHash: hash(credential), installationHash, receiptId, joinedAt: this.now(), sharingId: null };
      group.revision++;
      const result: Admission = { groupId: group.id, memberId, credential, expiresAt: group.expiresAt, ...(!input.inviteToken ? { inviteToken } : {}) };
      tx.set(this.groupPath(group.id), group);
      tx.set(`${COMMS_PATHS.credentials}/${hash(credential)}`, { groupId: group.id, memberId });
      tx.set(`${COMMS_PATHS.installations}/${installationHash}`, { groupId: group.id, memberId });
      if (!input.inviteToken) tx.set(`${COMMS_PATHS.invitations}/${group.inviteHash}`, { groupId: group.id });
      tx.set(`${COMMS_PATHS.receipts}/${receiptId}`, { groupId: group.id, inputHash, ciphertext: seal(result, installationSecret) });
      return result;
    });
  }
  async resolve(inviteToken: string) {
    const ref = await this.store.get<{ groupId: string }>(`${COMMS_PATHS.invitations}/${hash(inviteToken)}`);
    ensure(ref, 404, 'INVITE_UNAVAILABLE');
    const group = await this.store.get<Group>(this.groupPath(ref.groupId)); this.live(group);
    ensure(group.inviteHash === hash(inviteToken), 404, 'INVITE_UNAVAILABLE');
    return { name: group.name, expiresAt: group.expiresAt, memberCount: Object.values(group.members).filter(m => m.status === 'active').length, capacity: 12 };
  }
  private async mutate<T>(credential: string, apply: (group: Group, member: Member, tx: Transaction) => T | Promise<T>) {
    return this.store.transaction(async tx => {
      const { group, member } = await this.auth(credential, tx);
      const result = await apply(group, member, tx);
      group.pendingAudioCount = group.pendingAudio.length;
      group.revision++;
      tx.set(this.groupPath(group.id), group);
      return result;
    });
  }
  async extend(credential: string, expiresAt: number) {
    return this.mutate(credential, group => {
      ensure(expiresAt >= group.expiresAt && expiresAt <= group.createdAt + 14 * DAY, 400, 'INVALID_EXTENSION');
      group.expiresAt = group.cleanupAt = expiresAt;
      return { expiresAt };
    });
  }
  async rotate(credential: string) {
    const inviteToken = invitation();
    await this.mutate(credential, (group, member, tx) => {
      ensure(group.ownerId === member.id, 403, 'OWNER_REQUIRED');
      tx.delete(`${COMMS_PATHS.invitations}/${group.inviteHash}`); group.inviteHash = hash(inviteToken);
      tx.set(`${COMMS_PATHS.invitations}/${group.inviteHash}`, { groupId: group.id });
    });
    return { inviteToken };
  }
  async transfer(credential: string, memberId: string) {
    await this.mutate(credential, (group, member) => {
      ensure(group.ownerId === member.id, 403, 'OWNER_REQUIRED');
      this.active(group, memberId); group.ownerId = memberId;
    });
  }
  async revoke(credential: string, targetId?: string) {
    const result = await this.mutate(credential, async (group, member, tx) => {
      const target = this.active(group, targetId ?? member.id);
      if (targetId) ensure(group.ownerId === member.id && target.id !== member.id, 403, 'OWNER_REQUIRED');
      else ensure(group.ownerId !== member.id, 409, 'TRANSFER_OR_END_REQUIRED');
      const installation = await tx.get<Credential>(`${COMMS_PATHS.installations}/${target.installationHash}`);
      target.status = targetId ? 'removed' : 'left';
      group.pendingAudio.push(target.id);
      tx.delete(`${COMMS_PATHS.credentials}/${target.credentialHash}`);
      tx.delete(`${COMMS_PATHS.receipts}/${target.receiptId}`);
      if (installation?.groupId === group.id && installation.memberId === target.id) tx.delete(`${COMMS_PATHS.installations}/${target.installationHash}`);
      return { groupId: group.id, memberId: target.id };
    });
    // Durable pendingAudio ensures an external provider failure is retried by maintenance.
    try { await this.flushRevocations(result.groupId); return { ok: true, cleanupPending: false }; }
    catch { return { ok: true, cleanupPending: true }; }
  }
  async end(credential: string) {
    const groupId = await this.mutate(credential, (group, member) => {
      ensure(group.ownerId === member.id, 403, 'OWNER_REQUIRED');
      group.status = 'ended'; group.cleanupAt = this.now(); return group.id;
    });
    try { await this.cleanup(groupId); return { ok: true, cleanupPending: false }; }
    catch { return { ok: true, cleanupPending: true }; }
  }
  async messages(credential: string, after = 0, limit = 50) {
    const { group } = await this.auth(credential);
    const rows = await this.store.list<Message>(`${this.groupPath(group.id)}/messages`, { filters: [{ field: 'seq', op: '>', value: after }], order: 'seq', limit });
    await this.auth(credential);
    return { messages: rows.map(r => r.data), nextAfter: rows.at(-1)?.data.seq ?? after };
  }
  async send(credential: string, messageId: string, text: string) {
    return this.mutate(credential, async (group, member, tx) => {
      const path = `${this.groupPath(group.id)}/messages/${hash(`${member.id}:${messageId}`)}`;
      const existing = await tx.get<Message>(path);
      const ratePath = `${this.groupPath(group.id)}/messageRates/${member.id}`;
      const rate = await tx.get<{ window: number; count: number }>(ratePath);
      if (existing) { ensure(existing.text === text, 409, 'MESSAGE_ID_CONFLICT'); return existing; }
      ensure(group.messageSeq < 10_000, 409, 'MESSAGE_LIMIT');
      const window = Math.floor(this.now() / 60_000);
      const count = rate?.window === window ? rate.count : 0;
      ensure(count < 10, 429, 'MESSAGE_RATE_LIMIT');
      const message: Message = { id: messageId, memberId: member.id, text, seq: ++group.messageSeq, sentAt: this.now() };
      tx.set(path, message); tx.set(ratePath, { window, count: count + 1 });
      return message;
    });
  }
  async startLocation(credential: string) {
    const sharingId = randomUUID();
    return this.mutate(credential, (_group, member) => { member.sharingId = sharingId; return { sharingId }; });
  }
  async position(credential: string, position: { lat: number; lng: number; accuracyM: number; sharingId: string } | null) {
    if (!position) {
      const previous = await this.mutate(credential, (group, member) => {
        const sharingId = member.sharingId; member.sharingId = null;
        return { groupId: group.id, memberId: member.id, sharingId };
      });
      if (previous.sharingId) await this.locations.set(previous.groupId, previous.memberId, null, undefined, previous.sharingId);
      return;
    }
    const { group, member } = await this.auth(credential);
    ensure(member.sharingId === position.sharingId, 409, 'LOCATION_SHARING_STOPPED');
    this.locationLimits.check(member.id, 1, 1000);
    await this.locations.set(group.id, member.id, { ...position, receivedAt: this.now() }, group.expiresAt);
    try {
      const current = await this.auth(credential);
      ensure(current.member.sharingId === position.sharingId, 409, 'LOCATION_SHARING_STOPPED');
    } catch (e) { await this.locations.set(group.id, member.id, null, undefined, position.sharingId); throw e; }
  }
  async ticket(credential: string) {
    const token = secret();
    await this.store.transaction(async tx => {
      const { group, member } = await this.auth(credential, tx);
      tx.set(`${COMMS_PATHS.tickets}/${hash(token)}`, { groupId: group.id, memberId: member.id, credentialHash: member.credentialHash, expiresAt: Math.min(this.now() + 30_000, group.expiresAt) });
    });
    return { ticket: token, expiresInMs: 30_000 };
  }
  async consumeTicket(token: string) {
    return this.store.transaction(async tx => {
      const path = `${COMMS_PATHS.tickets}/${hash(token)}`;
      const ticket = await tx.get<Credential & { credentialHash: string; expiresAt: number }>(path);
      ensure(ticket && ticket.expiresAt > this.now(), 401, 'INVALID_TICKET');
      const group = await tx.get<Group>(this.groupPath(ticket.groupId)); this.live(group);
      const member = this.active(group, ticket.memberId);
      ensure(member.credentialHash === ticket.credentialHash, 401, 'INVALID_TICKET');
      tx.delete(path);
      return { groupId: group.id, memberId: member.id, credentialHash: member.credentialHash };
    });
  }
  async audioToken(credential: string) {
    ensure(this.audio.enabled, 503, 'AUDIO_NOT_CONFIGURED');
    const { group, member } = await this.mutate(credential, (group, member) => { group.audioUsed = true; return { group, member }; });
    const result = await this.audio.token(group.id, member.id, group.expiresAt);
    try { await this.auth(credential); }
    catch (error) { await this.audio.remove(group.id, member.id); throw error; }
    return result;
  }
  async flushRevocations(groupId: string) {
    const group = await this.store.get<Group>(this.groupPath(groupId));
    if (!group) return;
    ensure(!group.audioUsed || this.audio.enabled, 503, 'AUDIO_CLEANUP_NOT_CONFIGURED');
    for (const memberId of group.pendingAudio) {
      await this.audio.remove(group.id, memberId);
      await this.locations.set(group.id, memberId, null);
      await this.store.transaction(async tx => {
        const current = await tx.get<Group>(this.groupPath(group.id));
        if (current) { current.pendingAudio = current.pendingAudio.filter(id => id !== memberId); current.pendingAudioCount = current.pendingAudio.length; tx.set(this.groupPath(group.id), current); }
      });
    }
  }
  async cleanup(groupId: string) {
    const group = await this.store.transaction(async tx => {
      const current = await tx.get<Group>(this.groupPath(groupId));
      const pending = await tx.get<Group>(`${COMMS_PATHS.cleanup}/${groupId}`);
      if (!current) return pending;
      if (current.status === 'active' && current.expiresAt > this.now()) return;
      current.status = 'ended'; current.cleanupAt = this.now(); current.revision++;
      tx.set(this.groupPath(groupId), current);
      // recursiveDelete may delete the parent before failing on a child document.
      // Keep a recovery record outside that tree until every delete has succeeded.
      tx.set(`${COMMS_PATHS.cleanup}/${groupId}`, current); return current;
    });
    if (!group) return false;
    // Do not remove the durable cleanup marker until all external work succeeds.
    ensure(!group.audioUsed || this.audio.enabled, 503, 'AUDIO_CLEANUP_NOT_CONFIGURED');
    await this.audio.end(group.id, Object.keys(group.members));
    await this.locations.clear(group.id);
    await this.store.transaction(async tx => {
      const installations = await Promise.all(Object.values(group.members).map(m => tx.get<Credential>(`${COMMS_PATHS.installations}/${m.installationHash}`)));
      tx.delete(`${COMMS_PATHS.invitations}/${group.inviteHash}`);
      Object.values(group.members).forEach((m, i) => {
        tx.delete(`${COMMS_PATHS.credentials}/${m.credentialHash}`); tx.delete(`${COMMS_PATHS.receipts}/${m.receiptId}`);
        if (installations[i]?.groupId === group.id) tx.delete(`${COMMS_PATHS.installations}/${m.installationHash}`);
      });
    });
    const tickets = await this.store.list(COMMS_PATHS.tickets, { filters: [{ field: 'groupId', op: '==', value: group.id }] });
    for (const { path } of tickets) await this.store.transaction(async tx => { tx.delete(path); });
    await this.store.deleteTree(this.groupPath(group.id));
    await this.store.transaction(async tx => { tx.delete(`${COMMS_PATHS.cleanup}/${groupId}`); });
    return true;
  }
  async maintenance() {
    const run = async <T>(stage: string, operation: () => Promise<T>): Promise<T> => {
      try { return await operation(); }
      catch (error) { throw new ApiError(503, maintenanceErrorCode(stage, error)); }
    };
    let cleaned = 0; let failed = 0;
    const due = await run('FIRESTORE_DUE', () => this.store.list<Group>(COMMS_PATHS.sessions, { filters: [{ field: 'cleanupAt', op: '<=', value: this.now() }], limit: 100 }));
    for (const { data } of due) { try { if (await this.cleanup(data.id)) cleaned++; } catch { failed++; } }
    const unfinished = await run('FIRESTORE_JOURNAL', () => this.store.list<Group>(COMMS_PATHS.cleanup, { filters: [{ field: 'cleanupAt', op: '<=', value: this.now() }], limit: 100 }));
    for (const { data } of unfinished) { try { if (await this.cleanup(data.id)) cleaned++; } catch { failed++; } }
    // Pending revocations also need recovery when the group has not expired.
    const pending = await run('FIRESTORE_AUDIO', () => this.store.list<Group>(COMMS_PATHS.sessions, { filters: [{ field: 'pendingAudioCount', op: '>', value: 0 }], limit: 100 }));
    for (const { data } of pending) { try { await this.flushRevocations(data.id); } catch { failed++; } }
    // Cross-database writes can complete after Firestore cleanup. Sweep those orphans too.
    for (const groupId of await run('RTDB_EXPIRY', () => this.locations.expired(this.now()))) {
      try {
        const group = await this.store.get<Group>(this.groupPath(groupId));
        if (!group || group.status !== 'active' || group.expiresAt <= this.now()) await this.locations.clear(groupId);
        else await this.locations.extendExpiry(groupId, group.expiresAt);
      } catch { failed++; }
    }
    for (const collection of [COMMS_PATHS.tickets, COMMS_PATHS.rates]) {
      const expired = await run('FIRESTORE_EPHEMERAL_QUERY', () => this.store.list(collection, { filters: [{ field: 'expiresAt', op: '<=', value: this.now() }], limit: 500 }));
      for (const { path } of expired) await run('FIRESTORE_EPHEMERAL_DELETE', () => this.store.transaction(async tx => {
        const current = await tx.get<{ expiresAt: number }>(path);
        if (current && current.expiresAt <= this.now()) tx.delete(path);
      }));
    }
    return { cleaned, failed };
  }
  async rate(key: string, limit: number, durationMs = 60_000) {
    const now = this.now(); const path = `${COMMS_PATHS.rates}/${hash(key)}`;
    await this.store.transaction(async tx => {
      const previous = await tx.get<{ count: number; expiresAt: number }>(path);
      const current = previous && previous.expiresAt > now ? previous : { count: 0, expiresAt: now + durationMs };
      ensure(current.count < limit, 429, 'RATE_LIMITED');
      current.count++; tx.set(path, current);
    });
  }
}
