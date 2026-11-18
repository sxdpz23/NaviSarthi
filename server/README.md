# NaviSarthi temporary ride Groups server

Backend for account-free ride groups: private temporary credentials, QR admission,
text chat, current locations, realtime events, expiry cleanup, and optional LiveKit audio.
No Firebase Authentication is used. This directory does not change the Android app,
navigation ownership, package version, or existing account-sync database.

## Service dashboard

Open `/` for public aggregate counts of unexpired active rides, active rider
memberships and authenticated WebSocket connections. Counts refresh every 15
seconds, with a 10-second server cache. Membership does not mean a rider is online.

The responsive dashboard uses blue/teal/amber gradients, locally hosted Instrument
Sans and IBM Plex Mono fonts, and a structured event table with JSON download.
Focus feedback, tab selection and view changes use short transitions, respecting
the browser's reduced-motion preference. Tabs support arrow keys, Home and End.
Fonts and their SIL licences ship in `public/fonts`; Docker copies those assets
alongside the compiled server. Browser requests stay on the service's own origin.
UI craft informed by Unslop by Damir Sokolovsky (@heydamir), CC BY 4.0.

Set `ADMIN_PASSCODE` privately in the local ignored `.env` or Render environment
variables to enable Logs and Errors. Never commit the passcode. Login issues a
15-minute HttpOnly, SameSite=Strict cookie (also Secure in production); Lock
revokes it. Login attempts are rate limited. Missing configuration disables access.

Diagnostics contain operational metadata and safe error codes, excluding chat,
positions, names, credentials, raw URLs, request bodies and provider error text.
The latest 500 events and 200 errors can be viewed or downloaded as JSON. They
belong to the current process, reset on restart, and are not historical Render
logs. Admin access and its rate limits remain available during a database outage;
counts then report unavailable. Startup failures still require platform logs.
Admin access, limits and socket counts are per instance; use a single instance
for this deployment. Maintenance remains separately protected by MAINTENANCE_KEY.

## Run locally

Requires Node.js 22+ (Node.js 24 is used in the container).

```powershell
cd D:\Softwares\Projects\RE_TFTDash\server
npm ci
Copy-Item .env.example .env
npm run build
node --env-file=.env dist/main.js
```

The example uses **volatile, process-local memory**, binds only to `127.0.0.1:8080`,
and disables audio. All development data disappears on restart. To use the watch
command with the environment file: `node --env-file=.env --import tsx --watch src/main.ts`.
`npm run dev` expects variables already present in the environment.

Checks: `npm run typecheck`, `npm test`, `npm run build`, `npm run test:runtime`.
The runtime suite builds and launches an isolated memory-backed compiled server,
checks a real HTTP ride flow and startup failure behaviour, and stops its own test
processes. It never uses the credentials or data of a separately running service.
Tests include actual local
WebSocket connections, so the test process needs loopback networking permission.
`npm run test:firebase` is a separate emulator integration suite (see below).

## Client contract

All times are integer Unix milliseconds. IDs are opaque UUIDs; nicknames are 1–40
characters and group names 1–80 characters after trimming. JSON objects reject
unknown fields. Errors use `{ "error": "MACHINE_READABLE_CODE" }`; 429 responses
include `Retry-After`. Responses are marked `Cache-Control: no-store`.

### Admission

Before creating or joining, Android generates a 32-byte cryptographically random
installation secret (43-character base64url, no padding) and persists it securely.
Use the same secret while that installation belongs to the ride. This is **not** an
IMEI, account identifier, or Android advertising identifier.

Send these headers on create/join:

```text
Content-Type: application/json
X-Installation-Secret: <installation secret>
Idempotency-Key: <random UUID persisted for this admission attempt>
```

| Method and path | JSON body | Result |
| --- | --- | --- |
| `POST /v1/groups` | `{ "nickname": "Lead", "name": "Hill ride", "expiresAt": 1800000000000 }` | New membership and QR invitation |
| `POST /v1/invitations/resolve` | `{ "inviteToken": "<32 alphanumeric characters>" }` | Group name, deadline, count/capacity only |
| `POST /v1/groups/join` | `{ "nickname": "Rider", "inviteToken": "..." }` | New membership; immediate admission |

Choose a future expiry at most 14 days away; the example timestamp above is only
illustrative. Create/join return:

```json
{
  "groupId": "<uuid>",
  "memberId": "<uuid>",
  "credential": "<43-character private base64url credential>",
  "expiresAt": 1800000000000,
  "inviteToken": "<create response only>"
}
```

Use QR payload `{"v":1,"type":"navisarthi-ride","inviteToken":"..."}`. Android
parses the version/type and calls invitation resolution, shows the group and end
time, then joins after confirmation. There is no public group search.

Store the private credential encrypted with an Android Keystore key, exclude it
from backups, and clear it on leave/expiry. It is never part of the QR. The server
stores only its SHA-256 hash, plus an AES-GCM-encrypted admission receipt whose key
is derived from the installation secret. Consequently a lost response can be
recovered **only** by repeating the same body and Idempotency-Key with the same
installation secret. Admission retries do not allocate extra memberships.

Do not change the retry UUID or installation secret after a timeout. A changed body
with the same retry UUID returns `IDEMPOTENCY_CONFLICT`. An active installation
trying to join again under a new UUID returns `ALREADY_IN_GROUP`.

After admission, authenticate using `Authorization: Bearer <credential>`.
Credentials are private to one membership and valid only while the group and member
are active. No Firebase ID token, phone number, email address, or sign-in is needed.

### Authenticated endpoints

| Method and path | JSON body | Behaviour |
| --- | --- | --- |
| `GET /v1/session` | — | Group, active members, latest visible positions, own member ID and server time |
| `POST /v1/session/extend` | `{ "expiresAt": 1800000000000 }` | Any member; may only extend, capped at creation + 14 days |
| `POST /v1/session/invitation` | — | Owner rotates the QR; old invitation stops working |
| `POST /v1/session/transfer` | `{ "memberId": "<uuid>" }` | Owner transfers ownership to an active member |
| `POST /v1/session/remove` | `{ "memberId": "<uuid>" }` | Owner removes another member |
| `POST /v1/session/leave` | — | Nonowner leaves; owner must transfer first or end |
| `POST /v1/session/end` | — | Owner ends the whole ride early |
| `POST /v1/messages` | `{ "id": "<client UUID>", "text": "Meet at the bridge" }` | Server derives sender, assigns sequence/time, deduplicates retries |
| `POST /v1/messages/list` | `{ "afterSeq": 0, "limit": 50 }` | Ascending messages, `nextAfter` cursor; maximum page size 100 |
| `POST /v1/location/start` | — | Explicitly enables sharing and returns a new `sharingId` UUID |
| `PUT /v1/location` | `{ "lat": 12.9, "lng": 77.6, "accuracyM": 5, "sharingId": "<uuid>" }` | Replaces latest position; server timestamps it |
| `DELETE /v1/location` | — | Immediately clears shared position |
| `POST /v1/realtime-ticket` | — | Single-use ticket, valid for at most 30 seconds |
| `POST /v1/audio/token` | — | Room-scoped microphone-only LiveKit token and URL, or `AUDIO_NOT_CONFIGURED` |

Leave/remove/end return `{"ok":true,"cleanupPending":false}`. If a provider fails,
`cleanupPending:true` means API access is already revoked but audio/location cleanup
is queued for retry. An unavailable audio provider may continue an existing call
until provider cleanup succeeds; do not promise an instantaneous media cutoff during
a provider outage. An ended group cannot be extended or resurrected.

Messages allow 1–2,000 trimmed characters; retries with altered text return
`MESSAGE_ID_CONFLICT`. Limits: 10 new messages per rider per fixed calendar minute,
10,000 messages per group, 12 active members and 120 lifetime admissions per group
(the latter bounds the embedded member document). Joining members can read prior
session messages. Leaving removes access, but messages already sent remain until
group expiry. No private messages, files, media uploads, or recording are implemented.

Recommended client location cadence: 5 seconds moving, 30 seconds stationary.
The server permits at most one publish per rider per second and retains no trails.
Positions older than 2 minutes are omitted; clients should mark them stale after
30 seconds and compute straight-line distance locally in the selected units. A GPS
fix is not proof of physical presence; coordinates are client-reported.
Starting sharing issues a new `sharingId`; stopping invalidates it before clearing
the position. Queued updates from an earlier sharing run cannot resume sharing or
erase a newer sharing run. After process restart, call start again only if the
rider explicitly resumes sharing.

### WebSocket protocol

Obtain a ticket over HTTPS, open `wss://<host>/v1/events`, and send within 5 seconds:

```json
{ "type": "auth", "ticket": "<ticket>", "afterSeq": 0 }
```

Tickets are single-use even across different server instances. No token appears in
the URL or upgrade headers. Server frames:

```json
{ "type": "snapshot", "group": {}, "memberId": "<uuid>", "positions": {}, "serverTime": 1800000000000 }
{ "type": "messages", "messages": [{ "id": "<uuid>", "memberId": "<uuid>", "text": "Hello", "seq": 1, "sentAt": 1800000000000 }] }
```

The actual `group` includes ID, name, creation/expiry, owner ID, revision, last
message sequence, audio availability and active member summaries. Positions are
keyed by member ID and contain latitude, longitude, accuracy and `receivedAt`.
Snapshots replace the client's group/location view. Messages append and deduplicate
by sequence; persist the last applied sequence for reconnect. The server catches up
in batches of 100 and rechecks access before sending. Commands use HTTP, not socket
frames. Frames after authentication are rejected. Client WebSocket implementations
must answer standard ping frames. Slow clients are disconnected with code 4008;
expired/revoked sessions use 4001. Reconnect using a new ticket and saved sequence.

Firestore and RTDB subscriptions distribute changes across API instances; sockets
are not dependent on process-local room broadcasting. Cloud Run can disconnect
long requests; always implement client reconnection, even with session affinity.

## Firebase and production setup

The selected application database is **`navidb`, Standard edition, Singapore**.
Comms is isolated under `features/comms`; future features can use sibling feature
documents in the same database. The API URL provided for Render is
`https://navisarthi.onrender.com/`; its `/healthz` endpoint returned HTTP 200 during
this update. That health check is not a verification of credentials, database rules,
audio or the deployed storage schema. No live database was migrated from this workstation.

1. Confirm the target project, region, and native Firestore edition using the Firebase
   console or authenticated CLI (`npx -y firebase-tools@latest firestore:databases:list`, then
   `npx -y firebase-tools@latest firestore:databases:get navidb`). MongoDB-compatible databases
   are not supported by this adapter. The adapter uses the Admin SDK's native
   transactions/queries/listeners; realtime subscriptions are required here.
2. Configure `STORE=firebase`, `NODE_ENV=production`, `FIREBASE_PROJECT_ID`,
   `FIRESTORE_DATABASE_ID=navidb`, `FIRESTORE_EDITION=standard`, `FIREBASE_DATABASE_URL`,
   `HOST=0.0.0.0`, and `MAINTENANCE_KEY`. The server refuses memory storage in
   production and refuses the `(default)` Firestore database to protect existing data.
3. On Render, upload the service-account JSON as a secret file named
   `firebase-service-account.json` and set
   `GOOGLE_APPLICATION_CREDENTIALS=/etc/secrets/firebase-service-account.json`.
   On Cloud Run, a service identity can provide Application Default Credentials;
   local use can point at a private service-account file outside this repo.
   Scope IAM to the required resources where supported. Never put an Admin SDK key in
   the APK or commit one here. Admin SDK bypasses Security Rules: the API's credential
   and membership checks are the actual authorisation boundary.
4. Review and deploy the **separate** prototype rules/index configuration in
   `firebase/`, after replacing the RTDB instance name in `firebase.example.json`.
   All direct client database access is denied; the RTDB expiry field is indexed.
   The Firestore sample targets `navidb`; it denies all direct client access, so do
   not overwrite an existing client-access policy for other features without review.
   All Comms queries use Standard's automatic single-field indexes. The index sample
   has no collection-group exemptions: a generic `sessions` or `messages` exemption
   would also affect future features with those collection IDs. Keep any future
   application indexes in the shared configuration rather than replacing them with
   this initial empty index sample. No deployment is run automatically.
5. Generate a maintenance secret using a CSPRNG (32 random bytes, base64url), supply
   it through the hosting platform's secret environment, and have a scheduler call
   `POST https://navisarthi.onrender.com/internal/maintenance` with `X-Maintenance-Key`
   every minute. The endpoint
   returns 503 if cleanup work failed so the scheduler can retry and monitoring can
   alert. Do not log the secret in scheduler/proxy request headers.
6. Terminate TLS at the trusted managed edge. Do not expose the container directly
   over HTTP. Apply edge abuse limits and budget alerts before public access.
   The app does not trust forwarded IP headers, so a proxy deployment may aggregate
   users behind the proxy's source IP. Configure edge per-client limits accordingly;
   do not blindly enable trust for arbitrary `X-Forwarded-For` headers.
7. On Render: Root Directory `server`, Docker Build Context `.`, Dockerfile Path
   `Dockerfile`, Health Check Path `/healthz`. Leave Docker Command blank.
   Use the Docker image's `HOST=0.0.0.0`; respect Render's `PORT` environment.
   A free service can sleep, so the external scheduler remains required for cleanup.
   For Cloud Run, allow reconnects at its request timeout and configure CPU allocation
   appropriately if also relying on background intervals.

## Firestore layout and upgrading the initial deployment

```text
navidb (database)
└── features (collection)
    ├── comms (document: feature=comms, schemaVersion=2)
    │   ├── sessions/{groupId}
    │   │   ├── messages/{messageHash}
    │   │   └── messageRates/{memberId}
    │   ├── credentials/{credentialHash}
    │   ├── installations/{installationHash}
    │   ├── invitations/{invitationHash}
    │   ├── receipts/{receiptHash}
    │   ├── tickets/{ticketHash}
    │   ├── rates/{rateHash}
    │   └── cleanup/{groupId}
    └── <future-feature> (sibling document with its own subcollections)
```

The server materialises only the metadata document at startup; subcollections appear
when their first record is written. Do not create placeholder rides or credentials
in the console. Expiry deletes only the relevant ride and its indexes; it never
recursively deletes `features/comms`, `features`, the database or another feature.
The metadata document persists after the last ride ends and contains no rider data.
Realtime Database's `ridePositions` layout is unchanged, so existing position data
does not require a cross-database move. HTTP routes and temporary credentials are unchanged.

The first server version wrote the top-level collections `rideGroups`,
`rideCredentials`, `rideInstallations`, `rideInvites`, `rideReceipts`, `rideCleanup`,
`rideTickets` and `rideRates`. **The new server refuses to start if any of these still
contain documents or orphan subcollections.** This prevents silently abandoning
credentials or allowing an installation to join two rides across layouts. Even a
visit to a rate-limited endpoint may have created a legacy rate bucket.

For this first schema cutover, stop/suspend the previous revision and its maintenance
caller before deploying, even if the database appears empty. A rolling deploy with
the old writer still serving traffic could create a legacy record after the new
revision's startup check. Normal rolling deploys are fine once every revision uses
schema 2. If the database has no legacy records while the old writer is stopped,
deploy the updated service: startup creates the namespace automatically. If startup
reports legacy data, use the explicit migration:

1. Stop/suspend the old service and any external maintenance caller first. Do not
   run old and new revisions against the database during migration.
2. Build this server with `npm ci` and `npm run build`. Supply its Firebase project,
   `FIRESTORE_DATABASE_ID=navidb`, `FIRESTORE_EDITION=standard`, RTDB URL and private
   service-account path through your environment. A local credential path points
   to your downloaded private JSON, not Render's `/etc/secrets` path. Never commit it.
3. Run `npm run migrate:firestore` for a **read-only dry-run**. It prints counts only.
   If using a local `.env`, run `node --env-file=.env dist/migrate-main.js` instead.
4. Review the counts, then run `npm run migrate:firestore -- --apply` (or
   `node --env-file=.env dist/migrate-main.js --apply`). This atomically moves each
   document, preserves IDs, encrypted retry receipts, credentials and message order,
   includes orphaned subcollections, and refuses conflicting destination content.
   It does not rotate identities or delete whole collections/databases. An interrupted
   run is resumable by running the same command again while the service remains stopped.
5. Redeploy/resume the new revision and restart the external maintenance caller.
   Keep `FIRESTORE_DATABASE_ID=navidb` and `FIRESTORE_EDITION=standard` in Render.

No migration command is run automatically by the app or from this development session.

The backend uses 600 requests/minute/source IP and 180 requests/minute/credential,
plus 5 creates/hour/source IP and 30 invitation resolves/joins/minute/source IP.
Rate buckets are stored transactionally so multiple API instances cannot bypass
them. Counts are intentionally conservative initial defaults, not capacity claims.

I've set up prototype Security Rules to keep the data in Firestore safe. They are
designed to be secure for server-only Groups access by denying every direct client
read and write. However, you should review and verify them before broadly sharing
your app. If you'd like, I can help you harden these rules.

## Audio setup

Set all of `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` using server-side
secrets. LiveKit **Cloud** is required for the revocation semantics used here;
self-hosted deployments need a different revocation/admission strategy. Tokens are
limited to 5 minutes or remaining group time, whichever is shorter, microphone
publication only, one opaque room ID and one opaque rider ID. No nickname is sent
to LiveKit. The Android client obtains mute/participant state from the audio SDK;
membership alone does not imply a rider is in the call.

Remove/end explicitly revoke audio tokens (including refreshed ones) using the
provider's removal API and an explicit revocation cutoff. Pending work remains
durable if the provider fails. No audio recording/egress service is enabled by this
server. Test real provider reconnect/revocation and Android Bluetooth/audio focus
before rollout; the local suite verifies token claims and simulated provider failures,
not an actual cloud audio call.
Keep the provider configuration available until every audio-enabled ride has been
cleaned up. If it disappears, cleanup stays pending instead of silently treating
existing calls as ended.

## Expiry, deletion and operational limits

Server time is authoritative. Every authenticated operation and socket refresh checks
membership and expiry. Socket deadlines update when the group is extended. Expiry
does not rely on Firestore TTL, which is not an exact-time deletion mechanism.

Maintenance marks expired groups ended, revokes audio access, clears RTDB positions,
deletes credential/invitation/installation/receipt indexes and outstanding tickets,
then recursively deletes the Firestore group/messages. It rechecks the deadline
before deletion. Cross-database orphan-position cleanup handles late location writes.
Provider failures leave a durable ended group or pending-removal list for retry.
A separate cleanup journal survives partial recursive deletes, even when the parent
group document has already disappeared; it is deleted only after full cleanup succeeds.
Cleanup of an old ride never deletes a newer ride's installation membership.

The recovery sweep processes up to 100 due groups, 100 unfinished cleanup journals,
and 100 pending-revocation groups
per invocation, plus 500 expired tickets/rate buckets per collection. Under normal
load, target deletion is within 15 minutes. Monitor backlog and failures; this is
not a strict deletion SLA. Repeated calls safely drain larger backlogs. Monitor
Firestore/RTDB/LiveKit costs and service-account permissions separately.

Do not retain Comms content in backups/PITR, content exports, request-body logs or
audio recordings if the ride-end wipe promise is required. Database backups cover
the shared `navidb` database; if a future feature needs backups, account for Comms
retention separately rather than promising that deleting live records erases those
backup copies. Provider-managed
logs/retention need an independent review; this server cannot erase screenshots,
external copies or provider infrastructure records. An offline phone must clear
its local session before showing it again; the server cannot remotely erase a
powered-off device. Retain chat/location only in memory on Android.

There is no permanent account recovery. Reinstalling or losing both local secrets
means scanning again and receiving a new rider identity. One active membership is
enforced per installation secret, not per physical handset; clearing app storage
can bypass that identity and removal block. Creator powers cannot be recovered
after credential loss unless ownership was transferred beforehand.

## Emulator integration

Use an installed Firebase CLI and Java version supported by that CLI (Firebase CLI
15 requires Java 21+). This workstation can use Android Studio's bundled JBR at
`D:/Softwares/ProgramFiles/Android/Android Studio/jbr` by setting `JAVA_HOME` and
putting its `bin` directory first on `PATH`. No production
credentials are required for the demo project. From this directory:

```powershell
npx -y firebase-tools@latest emulators:exec --only firestore,database --config firebase/firebase.emulator.json --project demo-ride-groups "npm run test:firebase"
```

The integration suite refuses to run without both emulator host variables and
uses only `demo-ride-groups`. It exercises the actual Admin SDK adapter, realtime
subscriptions, recursive cleanup and deny-all client rules. The ordinary test suite
uses a serial transactional memory store and needs no Firebase access.
