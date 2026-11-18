# Comms Android client

The former Groups tab is now **Comms**. The old `groups` route redirects to `comms`
so restored tasks still open correctly. The client is included in NaviSarthi 0.7.0.

## Implemented

- Create a temporary ride (1–14 days), choose a nickname, and join by scanning the
  backend's versioned group QR. Invitations preview the name, count and end time
  before immediate admission. The QR is generated locally and contains only the
  group invitation, never a rider's private credential.
- Exact create/join retries retain their original installation secret, request UUID
  and body across process death. These and the resulting credential live in an
  AES-GCM encrypted file in `noBackupFilesDir`; its key lives in Android Keystore.
  The client has no Firebase Auth dependency.
- Group chat uses the private API credential, deduplicated message IDs, ordered
  WebSocket catch-up, automatic reconnection and explicit retry for unconfirmed
  messages. Only the most recent 500 messages are retained in memory. No chat or
  position cache is written to disk.
- Ride settings include bounded expiry extension, owner transfer, member removal,
  invitation rotation, leave and early termination. Existing backend permissions
  remain authoritative. Restored credentials are checked before showing data.
- Explicit location sharing uses the existing Nav `LocationProvider`, fresh real
  fixes only, 5-second moving / 30-second stationary publishing and the backend's
  sharing-run ID. Simulated/replayed/cached-old positions are never published.
- Riders shows position freshness and straight-line distance in the app's units
  while your own fresh position is available. Nav draws rider markers and names;
  Dash's existing offscreen renderer draws the same riders. Stale markers fade at
  30 seconds and disappear at 2 minutes. There is no separate Comms map, route
  planner, guidance controller or voice-instruction owner.
- Optional LiveKit audio has join/leave/mute controls and participant count. Audio
  uses only backend-issued credentials and no camera track. Android's audio routing
  remains responsible for Bluetooth/headset devices. A user-started foreground
  service keeps microphone/location access visible with stop controls.
- Audio and location are not restored after process death. The foreground service
  is `START_NOT_STICKY`; the user must explicitly enable them again.
- Expiry uses the last validated server time anchored to Android's monotonic clock.
  Revocation/expiry clears credentials, messages, positions and local audio. Offline
  location stop is immediate locally, but the previously published fix may remain
  visible to other riders for up to two minutes.

## Configure and test

The production endpoint has not been deployed. Supply the public API origin using
`-PcommsApiUrl=https://YOUR-GROUPS-SERVICE` or `COMMS_API_URL`. Leave it unset to show
an honest unavailable state. Origin URLs cannot contain credentials, paths, queries
or fragments. Production permits HTTPS only; debug also permits loopback HTTP.

For local development, run the backend in memory mode (see `server/README.md`), then:

```powershell
adb reverse tcp:8080 tcp:8080
./gradlew.bat :app:assembleDebug -PcommsApiUrl=http://127.0.0.1:8080
```

Each physical test phone needs its own USB reverse mapping. The QR never supplies a
server URL; both phones must use builds configured for the same backend. Do not use
the memory backend for an actual ride: it loses all server sessions on restart.

On this workstation, the existing `app/build` contains locked imported output. The
local ignored `.build-v060/comms.init.gradle` redirects build output safely without
deleting that directory. Use `-I .build-v060/comms.init.gradle` when building here.
The SDK is selected through `ANDROID_HOME`; Android Studio's bundled JBR is usable
at `D:/Softwares/ProgramFiles/Android/Android Studio/jbr`.

Useful checks:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests '*CommsContractTest'
./gradlew.bat :app:assembleDebug -PcommsApiUrl=http://127.0.0.1:8080
```

Contract tests cover QR validation, secure origin restrictions, message catch-up
deduplication, bounded memory, map membership/freshness filtering, distances,
private HTTP headers and redirect refusal. Hardware checks still required: scan and
join on two phones, Keystore persistence across process death, denied permissions,
microphone/Bluetooth/audio-focus behaviour, weak coverage and reconnect, expiry
during an audio call, screen-off sharing, and the actual Dash marker rendering.

## Limits of this increment

There is no background push-notification delivery or independent audio-call invitation
system; riders explicitly join the group's audio room. The optional audio backend
must be configured separately. The backend, Firebase adapters and provider's live
revocation behaviour need deployment testing before a real ride. Account recovery
and cross-device restoration are intentionally absent for temporary sessions.
