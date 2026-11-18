export interface OperationalEvent {
  id: number; time: string; level: 'info' | 'warn' | 'error';
  event: 'request' | 'admin_login' | 'admin_denied' | 'admin_logout' | 'maintenance' | 'websocket_open' | 'websocket_closed' | 'service_ready' | 'service_stopping';
  method?: string; route?: string; status?: number; durationMs?: number;
  code?: string; cleaned?: number; failed?: number;
}

/** Process-local operational metadata only. Never accepts arbitrary error text or request data. */
export class OperationsLog {
  private events: OperationalEvent[] = [];
  private errors: OperationalEvent[] = [];
  private sequence = 0;
  readonly startedAt: string;
  constructor(private now: () => number = Date.now, private capacity = 500) { this.startedAt = new Date(now()).toISOString(); }
  record(level: OperationalEvent['level'], event: OperationalEvent['event'], details: Omit<OperationalEvent, 'id' | 'time' | 'level' | 'event'> = {}) {
    const entry = { ...details, id: ++this.sequence, time: new Date(this.now()).toISOString(), level, event };
    this.events.push(entry); if (this.events.length > this.capacity) this.events.shift();
    if (level === 'error') { this.errors.push(entry); if (this.errors.length > 200) this.errors.shift(); }
  }
  snapshot(errorsOnly = false) { return (errorsOnly ? this.errors : this.events).map(entry => ({ ...entry })); }
}
