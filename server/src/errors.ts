export class ApiError extends Error {
  constructor(public status: number, public code: string) { super(code); }
}
export function ensure(condition: unknown, status: number, code: string): asserts condition {
  if (!condition) throw new ApiError(status, code);
}

export function maintenanceErrorCode(stage: string, error: unknown): string {
  const code = (error as { code?: unknown } | null)?.code;
  const detail = typeof code === 'number' && Number.isInteger(code) && code >= 0 && code <= 16
    ? `RPC_${code}`
    : typeof code === 'string' && /^(PERMISSION_DENIED|UNAVAILABLE|NETWORK_ERROR|ETIMEDOUT|ENOTFOUND|ECONNRESET)$/i.test(code)
      ? code.toUpperCase()
      : 'FAILED';
  return `MAINTENANCE_${stage}_${detail}`;
}
