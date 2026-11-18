import { AccessToken, RoomServiceClient } from 'livekit-server-sdk';
export interface Audio {
  enabled: boolean;
  token(groupId: string, memberId: string, expiresAt: number): Promise<{ url: string; token: string }>;
  remove(groupId: string, memberId: string): Promise<void>;
  end(groupId: string, memberIds: string[]): Promise<void>;
}
export const disabledAudio: Audio = {
  enabled: false,
  async token() { throw new Error('Audio is not configured'); },
  async remove() {}, async end() {},
};
export class LiveKitAudio implements Audio {
  readonly enabled = true;
  private client: RoomServiceClient;
  constructor(private url: string, private key: string, private apiSecret: string) {
    this.client = new RoomServiceClient(url.replace(/^ws/, 'http'), key, apiSecret);
  }
  async token(group: string, member: string, expiresAt: number) {
    const ttl = Math.min(300, Math.floor((expiresAt - Date.now()) / 1000));
    if (ttl < 1) throw new Error('Session expired');
    const token = new AccessToken(this.key, this.apiSecret, { identity: member, ttl });
    token.addGrant({ room: `ride-${group}`, roomJoin: true, canPublish: true, canPublishSources: [2], canSubscribe: true, canPublishData: false });
    return { url: this.url, token: await token.toJwt() };
  }
  async remove(group: string, member: string) {
    // Explicit cutoff covers tokens refreshed just before removal (provider default has leeway).
    try { await this.client.removeParticipant(`ride-${group}`, member, { revokeTokenTs: BigInt(Math.floor(Date.now() / 1000) + 1) }); }
    catch (error: any) { if (error?.status !== 404 && error?.code !== 'not_found') throw error; }
  }
  async end(group: string, members: string[]) {
    for (const member of members) await this.remove(group, member);
    try { await this.client.deleteRoom(`ride-${group}`); }
    catch (error: any) { if (error?.status !== 404 && error?.code !== 'not_found') throw error; }
  }
}
