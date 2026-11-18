/** Physical Firestore paths. No feature may write outside its own namespace. */
export const COMMS_ROOT = 'features/comms';
export const COMMS_PATHS = {
  sessions: `${COMMS_ROOT}/sessions`,
  credentials: `${COMMS_ROOT}/credentials`,
  installations: `${COMMS_ROOT}/installations`,
  invitations: `${COMMS_ROOT}/invitations`,
  receipts: `${COMMS_ROOT}/receipts`,
  cleanup: `${COMMS_ROOT}/cleanup`,
  tickets: `${COMMS_ROOT}/tickets`,
  rates: `${COMMS_ROOT}/rates`,
} as const;

/** Only these original server-owned roots are eligible for the explicit migration. */
export const LEGACY_COMMS_PATHS = {
  rideGroups: COMMS_PATHS.sessions,
  rideCredentials: COMMS_PATHS.credentials,
  rideInstallations: COMMS_PATHS.installations,
  rideInvites: COMMS_PATHS.invitations,
  rideReceipts: COMMS_PATHS.receipts,
  rideCleanup: COMMS_PATHS.cleanup,
  rideTickets: COMMS_PATHS.tickets,
  rideRates: COMMS_PATHS.rates,
} as const;
