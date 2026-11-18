# NaviSarthi 0.7.0 (41)

- Rename the Groups section to Comms within the existing NaviSarthi app.
- Add client-side ride creation, nickname entry, QR invitations/scanning, temporary encrypted credentials, text chat, reconnect/catch-up and ride management.
- Add opt-in location sharing, rider distances in the selected units, Nav rider overlays and markers in the existing Dash renderer.
- Add optional backend-issued group audio with join/leave/mute, participant count and user-started microphone/location foreground-service controls.
- Clear local temporary data on confirmed revocation or ride expiry; keep chat and locations in memory only.

This is the full NaviSarthi release APK, not a separate Comms application. Package: com.navisarthi.app. Version: 0.7.0, code 41. Its signing certificate matches the previous 0.6.9 delivery.

Comms connects to https://navisarthi.onrender.com/. Allow additional time for the service to wake after inactivity. Firebase namespace changes stay behind the existing API. Audio requires LiveKit configuration on the backend. No administrator passcode, Firebase service-account credentials or LiveKit API secrets are embedded in the app. This rebuild replaces the previous APK with the same version 0.7.0 and version code 41.

Validation: successful release compilation/packaging, 8 client contract tests passed on the final implementation, correct package/version, verified matching signature, ZIP integrity, 16 KiB ZIP alignment and delivered SHA-256. Lint deferred per project preference. Live audio, Bluetooth, two-phone flows, screen-off sharing and Dash/Pod hardware operation still require device/ride testing.

## Call and reconnect recovery update

- Retain the loaded ride view during foreground revalidation and keep active audio controls accessible while reconnecting.
- Retry exhausted service capacity more slowly and show a specific capacity message.
- Reduce backend Firestore usage by keeping transport rate counters per instance and refreshing idle WebSocket snapshots once per minute while sending lightweight heartbeats every 15 seconds.
- Include QR image sharing and device image selection in the scanner menu.
- Backend changes require deployment. Existing exhausted Firestore capacity must recover before API requests succeed.
- Validation passed: 44 server tests, four runtime checks, eight Android Comms tests, release compilation, package/version/signature, ZIP integrity, alignment and checksum. A 30-minute physical group call has not been repeated with this build.
