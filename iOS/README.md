# NaviSarthi iOS

Native SwiftUI project prepared in the requested iOS directory. iOS 17+; local
SwiftData storage; Apple MapKit plus configurable Mapbox/Valhalla routing;
Dash/Pod adapters; Garage, ride history, backup and Comms implementation.

**Source implementation is substantially expanded, but this is not a verified
build or complete Android parity.** Windows has no Swift/Xcode toolchain.
See docs/PARITY.md for implemented features, remaining gaps and exact validation.
No IPA has been built or signed.

## Open on a Mac

1. Copy the entire iOS folder and open NaviSarthi.xcodeproj in Xcode.
2. Select the NaviSarthi scheme and an iPhone simulator. The local NaviSarthiCore
   Swift package is part of this folder; the baseline needs no external SDK.
3. Run `bash tools/verify-macos.sh` for Swift core tests and simulator compilation.
4. Optional audio: run `bash tools/setup-audio-macos.sh` on Xcode 26+ to link the
   LiveKit SDK, then run verification again. See docs/DEPENDENCIES.md.
5. For physical devices, select your bundle ID and Apple Developer team. Keep
   Hotspot Configuration, Bluetooth central, location/audio background modes and
   the entitlements attached. Real Dash broadcast requires Apple's approved
   Multicast Networking entitlement in the provisioning profile. A source entry
   is not an entitlement grant. You can omit multicast locally to test other
   features while approval is pending; Dash broadcast then remains unavailable.
6. Exercise Location, Bluetooth, Local Network, Camera and Microphone permissions
   through their respective features. The simulator needs a simulated GPS fix.

## Features and configuration

Nav is the only route/guidance/voice owner. Starting navigation automatically
feeds the selected Dash/Pod. Saved Destinations and offline guidance routes stay
inside Nav. Phone-map viewing is temporary and explicit; no pause or separate
console planner. Settings stays in the taskbar.

Garage includes vehicle identity/odometer, fuel/service edits, expenses, documents,
maintenance rules, expiry/service reminders and deletion. Select a vehicle there
to associate recording and odometer updates. Enable reminder permission explicitly.

Nav route files support GPX import and stored route instructions/geometry.
These do not download a basemap or provide a native offline road router. Provider
settings accept a rider's Mapbox public access token or HTTPS Valhalla route URL.
Mapbox sends driving-traffic requests; Valhalla requests motorcycle costing.
Neither is contacted before a user requests a route. Apple Maps remains the
key-free default. Basemap styles and route traffic overlays have separate limits;
read the parity checklist before relying on road guidance.

Comms uses the existing public Android service origin:
https://navisarthi.onrender.com/. Creating/joining sends real requests when the
app is run. Use an intentional test group. LiveKit must be configured on that
service for audio. Live location sharing is explicit and does not automatically
resume after an app restart. See docs/PARITY.md for scope and lifecycle behaviour.

Settings > Backup and restore exports/imports local JSON including attachments.
It also imports Android DriveSnapshot schema 1 backups. Imports add missing
stable IDs without overwriting current edits. Keep exported files private.

Settings > Cloud account and backup accepts your Firebase project ID and public
Web API key. Email/password authentication must be enabled; existing owner-only
users/{uid}/... Firestore rules are used. No Firebase backend changes, SDK or
service-account key are needed. Google-only accounts need provider linking before
email/password sign-in. Cloud backup is explicit, capped at 4 MB and not automatic
cross-platform collection sync. Local export supports up to 100 MB. Cloud writes
may incur usage charges; prior snapshot chunks remain in your Firebase project.

## Verification and delivery

Windows checks:

- `python tools/validate_project.py`: references/plists/scheme and source hashes.
- `python tools/check_swift_syntax.py`: Swift grammar if validation dependencies
  are installed under .validation. This is not compiler or type checking.
- `python tools/package_source.py`: source ZIP, ZIP integrity and SHA-256.

Core XCTest cases are authored but cannot execute here. Live account/hardware
integration tests have not been run. Do not treat grammar checks as an iPhone build.

The baseline project is deterministic. Do not run generate_project.py after
linking SDK packages; it produces the dependency-free baseline. Validation does
not overwrite an extended project. Use xcodebuild on the Mac after SDK setup.

The source ZIP is delivered under builds and versions/iOS source. Once signed
builds are possible, `APPLE_TEAM_ID=YOURTEAMID bash tools/archive-macos.sh` runs
verification and archives under builds and versions/iOS/0.7.1. Export the IPA
through Xcode Organizer and keep its SHA-256/release notes there.

No Apple signing credentials or backend secrets are embedded. No source push.
See LICENSE and NOTICE for attribution, including the existing dash protocol work.

## References

- [Apple background execution](https://developer.apple.com/documentation/uikit/about-the-background-execution-sequence)
- [Apple broadcast/multicast requirements](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy)
- [Firebase REST authentication](https://firebase.google.com/docs/reference/rest/auth)
- [Firestore REST authorization](https://firebase.google.com/docs/firestore/use-rest-api)
- [LiveKit Swift](https://docs.livekit.io/transport/sdk-platforms/swift/)
- [Mapbox Directions](https://docs.mapbox.com/api/navigation/directions/)
- [Valhalla routing](https://valhalla.github.io/valhalla/api/turn-by-turn/api-reference/)
