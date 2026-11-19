# NaviSarthi iOS

Native SwiftUI/SwiftData iOS 17+ source: Nav, regional offline routing, Dash/Pod,
Garage/rides, Comms, Android collection sync, Drive and sharing. Experimental
continued projection requires iOS 26/Xcode 26.

**No IPA has been compiled or signed.** Windows grammar/structural checks cannot
establish compiler/device correctness. See [parity and acceptance](docs/PARITY.md).

## On a Mac

Copy the entire iOS folder, install Xcode/command-line tools, then:

```bash
cd iOS
bash tools/prepare-macos.sh
```

This prepares the existing artwork as AppIcon, runs Swift core tests and compiles
an unsigned simulator app. The baseline needs no third-party SDK. Optional setup:

```bash
APPLE_APP_GROUP=group.your.registered.identifier \
GOOGLE_IOS_CLIENT_ID=your-client.apps.googleusercontent.com \
ENABLE_RIDE_AUDIO=1 \
GOOGLE_SERVICE_INFO_PLIST=/absolute/path/GoogleService-Info.plist \
bash tools/prepare-macos.sh
```

Omit unconfigured integrations. Register App Group for app AND extension. Google
must be an iOS client for your bundle/project; push needs APNs configuration.
Helpers have NOT run on Windows. See [dependencies](docs/DEPENDENCIES.md),
[sharing](docs/SHARING.md) and [Google](docs/GOOGLE.md).

Open NaviSarthi.xcodeproj, select the scheme and your team/bundle IDs. Real Dash
broadcast requires Apple's approved Multicast Networking entitlement in the signed
profile plus Hotspot Configuration. Source declarations are not an Apple grant.
Exercise feature permissions on a device.

## Behaviour and data

Nav alone owns routing, guidance, voice and map style. Starting guidance feeds the
selected Dash/Pod. Destinations/routes stay in Nav; phone viewing is temporary and
explicit. No pause or duplicate console planner. Settings belongs in the taskbar.

Apple Maps is online default; Mapbox provides driving-traffic; Valhalla requests
motorcycle costing. Credentials are rider supplied. Regional OSM XML/JSON packs
calculate routes and render locally, without OBF/PBF or offline terrain runtime.
Read [offline limits](docs/OFFLINE.md).

Garage covers fuel/services/expenses/documents/reminders. Select the recording
vehicle for odometer association. Backup adds missing IDs and imports Android
DriveSnapshot schema 1. Firebase auth, snapshots and opt-in collection sync use
existing owner-only users/{uid}/... rules, with no service-account key.
[Sync coverage](docs/SYNC.md) distinguishes Android fields from iOS-only snapshot
data. Drive adds explicit backup/restore.

Comms uses https://navisarthi.onrender.com/ for requested group actions. Use an
intentional test group; audio needs server LiveKit and Mac SDK setup. Location
sharing is explicit. Music controls cover Apple's Music app; calls show generic state.

## Verification and delivery

```bash
python tools/validate_project.py
python tools/check_swift_syntax.py
python tools/package_source.py
```

Grammar needs tree-sitter under .validation. Python checks are not Swift compilation.
ZIP/checksum/notes go under builds and versions/iOS source. VERIFICATION.json records
results. Do not run generate_project.py after SDK/extension setup: it generates the
baseline and would discard setup. Validation never regenerates it; use Xcode and
verify-macos.sh for extended projects.

After compiler/simulator/hardware acceptance, APPLE_TEAM_ID=YOURTEAMID bash
tools/archive-macos.sh archives under builds and versions/iOS/0.7.1. Export IPA
through Organizer; keep checksum/notes there. No signing credentials/source push
or version bump. See LICENSE/NOTICE.
