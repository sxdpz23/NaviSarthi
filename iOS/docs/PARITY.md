# iOS implementation status and remaining validation

Updated 2026-11-19. This supersedes the initial-port gap list. All entries below
refer to source implementation unless a verification result explicitly says otherwise.
Android and server source were not changed. No version bump or source push.

## Implemented in the continuation

- Garage documents and invoice attachments (PDF/images), expiry dates, previews,
  editing and deletion; service-date and document-expiry local notifications.
- Expense ledger; fuel/service editing; confirmed vehicle deletion with garage
  cleanup; recurring maintenance rules and imported scheduled-service completion.
- Full-to-full fuel economy includes partial fills and excludes unfinished tanks.
- Explicit vehicle selection for recording and transactional ride-to-odometer updates.
- Local JSON backup/export and additive, validated restore with stable IDs. Includes
  documents, expenses, maintenance rules, saved routes, destinations and rides.
- Android `navisarthi-backup` schemaVersion 1 import using the actual DriveSnapshot
  table/attachment format. Imports the single-bike identity, fuel, service records,
  invoices, documents, saved locations, rides, recurring items and scheduled services.
  Repeated imports deduplicate by deterministic IDs. Older Android snapshots do not
  record full-tank flags or per-point track timestamps: flags remain unknown/false,
  and GPX timestamps are evenly interpolated across the recorded ride duration.
  Unknown table versions are rejected instead of silently dropping future data.
- Firebase email/password sign-in, create-account, password reset, token refresh and
  device-only Keychain credentials through documented Firebase REST endpoints.
  Explicit iOS cloud backups use atomic, checksummed chunk/manifest commits with
  an optimistic concurrency precondition. 4 MB cloud limit; 100 MB local file limit.
  This is backup/restore, not automatic bidirectional Android Firestore collection sync.
- Comms WebSocket tickets/authentication, live snapshots, reconnect/poll fallback,
  persistent chat retry IDs, live rider map with stale-position labels, explicit
  location sharing and retry of server-side removal after offline stop.
- Comms QR scanner, invitation replacement, member removal, ownership transfer,
  expiry extension and leave/end. No session credentials appear in QR codes.
- LiveKit room/microphone adapter with mute/leave and disconnect/reconnect status.
  **SDK linking is deferred to the receiving Mac.** It is behind canImport(LiveKit),
  so the baseline project remains usable without the SDK; audio requires running
  `tools/setup-audio-macos.sh` and a server with LiveKit configured.
- Configurable Mapbox driving-traffic and HTTPS Valhalla motorcycle routing adapters,
  structured Dash/Pod maneuver glyphs, multi-stop planning and provider settings.
  API credentials are supplied by the rider; none embedded. Mapbox is driving
  costing, while Valhalla explicitly requests motorcycle costing.
- Mapbox congestion is grouped into coloured route strokes on both phone and Dash.
  Unknown traffic values do not fabricate traffic. Blue/vehicle-orange/gold route
  choices, perspective/top-down controls and Dash zoom are provided.
- GPX import with size/coordinate/segment validation; saved geometry/instructions
  can start without a new route request. Track matching uses distance along the
  route, heading preference, a local search window and progress hysteresis.
- Dash route-only fallback frame when basemap snapshots fail. This is explicitly
  labelled as route-only, not presented as downloaded road-map coverage.
- Pod device selection, persisted pairing, CoreBluetooth restoration, bounded
  exponential reconnect, connection timeout and a write queue that coalesces
  guidance without discarding pending control packets.

## Still unfinished

1. Screen-locked/background **Dash video projection**. The app deliberately stops
   its snapshot/encoder stream on background. Location recording and BLE can use
   their real background modes; those do not automatically authorize continuous
   video rendering. A permitted hardware-proven solution is still required.
2. Native offline road-network routing, Android OBF/vector runtime parity,
   downloadable basemap regions, terrain and all specialised road overlays.
   Saved-route guidance and GPX following are implemented, but off-route road
   recalculation needs an online provider. Apple Maps basemap cache is not a
   managed offline download feature.
3. Automatic cross-platform Firebase collection sync, Google OAuth/Drive backup,
   push notifications/OTA delivery and Android-equivalent remote diagnostics.
   Explicit Firebase cloud backup and Android backup-file import are implemented.
4. iOS Share Extension/universal links and a Google shortened-link resolver.
   Custom Nav URL handling and pasted coordinates/place queries exist.
5. Arbitrary third-party now-playing control and cellular call-log/answer/reject
   parity. These require supported iOS APIs and cannot be ported by reproducing
   Android notification-listener code or using private APIs.
6. Exact visual parity, final app icon, accessibility/device-size review and
   release compliance review. Vehicle route accent currently uses orange rather
   than the full Android factory-colour catalogue.
7. LiveKit dependency resolution/linking and all Xcode type checking, simulator
   execution, signing, profiling and bike acceptance.

## Verification actually performed here

- Swift syntax grammar: PASS, 17 files using tree-sitter-swift.
- Project references, shared scheme XML and plist parsing: PASS.
- Core test cases authored for wire packets, RTP, CRC, PIN, fuel economy, GPX,
  route matching and polyline decoding: NOT EXECUTED (Swift unavailable).
- Swift/Xcode compiler and UI tests: NOT RUN.
- Cloud/Mapbox/Valhalla/Comms integration against user accounts or live rides:
  NOT RUN. No real test accounts/groups were created or data uploaded.
- iPhone/Dash/Pod validation and signature verification: NOT RUN.

## Receiving-Mac acceptance

Run core tests and unsigned simulator compilation using tools/verify-macos.sh.
Then run the audio SDK setup if audio is wanted and repeat compilation. Validate
storage migrations, forms, malformed/back-to-back backup import, attachment bounds,
notification permission denial, fuel calculations and vehicle odometer association.

Test routing against fixtures and real Indian roads: flyovers, parallel carriageways,
U-turns, loops, missed stops, arrival and GPS loss. Validate Mapbox segment alignment,
unknown traffic, Valhalla motorcycle support, GPX and saved-route offline behaviour.
MapKit fallback has no structured maneuver glyphs; structured providers do.

Use an intentional temporary group for Android/iOS interoperability: interrupted
admission, chat retry across restart, WebSocket loss, live-location start/stop race,
rejoin, stale locations, removal, expiry and audio disconnect. Test cloud operations
only in your configured Firebase account with the existing owner-only rules.

Validate real Dash RSA/keepalive/route-card ordering, SPS/PPS, IDRs, FU-A, zoom,
foreground switching and snapshot fallback; validate Pod PIN/reconnect/restoration.
Do not call screen-off projection complete until the separate platform gate passes.
Archive/export only after compiler, simulator and hardware checks pass; keep final
binaries, checksums and release notes under builds and versions.
