# iOS parity and acceptance

Updated 2026-11-19. This inventory describes source, not a compiled/device-verified app.
Concurrent Android/server work was left untouched. No version bump or source push.

| Area | Implemented source | Remaining acceptance or limitation |
| --- | --- | --- |
| Nav | One owner; Apple Maps, Mapbox traffic, Valhalla motorcycle; multi-stop, voice, structured turns, GPX, stored routes and progress matching | Roads/GPS/provider tests. Mapbox uses driving costing; Apple fallback lacks structured glyphs. |
| Offline | Native directed graph/A*, turn restrictions, regional OSM XML/JSON packs, SHA-256 downloads, local roads/search/POIs and CPU Dash rendering | Not Android OBF/PBF, offline DEM/terrain or all specialised overlays. See OFFLINE.md. |
| UI | Common dark glass/taskbar inset, place actions, Nav saved destinations/routes, temporary phone map and vehicle accents | Device-size, accessibility and visual acceptance. |
| Dash | UDP/RSA, route-card order, keepalives/ACKs, H.264/RTP, traffic strokes, zoom/POV and exact navigation exit | Compiler, signing entitlements and firmware tests. |
| Screen off | Opt-in iOS 26 continued processing, finite-trip progress/cancellation and CPU rendering | Experimental: OS may refuse/end it; locked encoder/network endurance unproven. See SCREEN-OFF.md. |
| Pod | BLE discovery/PIN, CRC, clock/guidance, coalesced writes, restoration and bounded reconnect | Physical pairing/reconnect/background tests. |
| Garage/rides | Vehicles, fuel economy, services, expenses, documents/invoices/expiry, recurring reminders, recording/odometer and history/GPX | Migration, permission denial, forms and device tests. |
| Backup | Validated additive iOS snapshots; actual Android DriveSnapshot schema 1 import with stable IDs/attachments | Legacy track timestamps interpolated; missing full-tank flags unknown/false. iOS format cannot restore on Android. |
| Firebase | Email/password and Google adapters, refresh, atomic snapshots, automatic shared Android collection sync with conflicts/deletions | Configure project/providers; live testing pending. Shared-field limits in SYNC.md. |
| Drive | OAuth/PKCE, explicit drive.file consent, list/import/protected upload and optional opportunistic daily backup | Native client/callback needed; Android files need application access grants. See GOOGLE.md. |
| Sharing | Text/Apple/Google parsing, bounded short links, GPX/custom scheme, App Group inbox/extension source | Mac must embed target/configure group; owned-domain universal links absent. See SHARING.md. |
| Comms | Service APIs, realtime tickets/WebSocket/poll fallback, retries, QR/admin, rider map, explicit sharing and conditional LiveKit | Mac SDK linking plus live interoperability/audio tests. |
| Media/calls | Public Music-app control/metadata and generic ringing/active display | No public arbitrary-player notification access, caller history/names or cellular answer/reject parity. |
| Diagnostics | Bounded enum/counter events, export and explicit owner Firebase upload | No automatic Android remote ingestion; no live upload test. |
| Distribution | Optional FCM/APNs callbacks/opt-in, App Store lookup and TestFlight links | SDK/APNs/provisioning/published IDs required; no backend token registry added. |
| Branding | Existing artwork and native AppIcon helper | Helper not executed; asset/privacy/store review pending. |

## Actual verification

Windows checks cover Swift grammar, references, plists, shared scheme and ZIP
integrity. VERIFICATION.json records counts/hashes. Grammar is not type checking.
Core XCTest fixtures are authored but NOT EXECUTED. No Xcode compilation,
simulator, signing, live account integration, hardware acceptance or IPA exists.

## Receiving-Mac acceptance

Run tools/prepare-macos.sh with optional configuration from README, then
verify-macos.sh. Resolve compiler errors before device use. Test migrations,
imports, permission denial, offline restrictions/disconnections, GPS loss/loops,
traffic alignment, recording, media/calls and lifecycle transitions.

Use disposable Firebase data and an intentional temporary Comms group. Test
concurrent edits/deletes, account changes, interrupted writes/uploads, consent
expiry, sharing-stop races, removal/expiry and audio reconnect.

On hardware test RSA/keepalive/IDR/SPS/PPS/FU-A, joystick/exit, Wi-Fi, Pod restoration
and prolonged screen lock including OS refusal/cancellation. Overall and screen-off
parity remain unverified until these pass. Archive/export only afterwards under
builds and versions, with checksum and release notes.
