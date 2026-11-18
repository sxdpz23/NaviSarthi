# NaviSarthi 0.7.0 (41)

- Rename the Groups section to Comms within the existing NaviSarthi app.
- Add client-side ride creation, nickname entry, QR invitations/scanning, temporary encrypted credentials, text chat, reconnect/catch-up and ride management.
- Add opt-in location sharing, rider distances in the selected units, Nav rider overlays and markers in the existing Dash renderer.
- Add optional backend-issued group audio with join/leave/mute, participant count and user-started microphone/location foreground-service controls.
- Clear local temporary data on confirmed revocation or ride expiry; keep chat and locations in memory only.

This is the full NaviSarthi release APK, not a separate Comms application. Package: com.navisarthi.app. Version: 0.7.0, code 41. Its signing certificate matches the previous 0.6.9 delivery.

Comms requires deployment/configuration of the shared backend and optional LiveKit service. No server address is embedded in this release, so Comms shows an unavailable state until a configured build is supplied. The localhost development endpoint is not shipped in this release.

Validation: successful release compilation/packaging, 7 client contract tests passed on the final implementation, correct package/version, verified matching signature, ZIP integrity, 16 KiB ZIP alignment and delivered SHA-256. Lint deferred per project preference. Live audio, Bluetooth, two-phone flows, screen-off sharing and Dash/Pod hardware operation still require device/ride testing.
