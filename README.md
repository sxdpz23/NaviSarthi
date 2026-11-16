# NaviSarthi

Private Android navigation companion built on [Northstar](https://github.com/adityadasika21/NorthStar).

Targets Android 14+. Meteor 350 selects Tripper Pod over Bluetooth; Himalayan 450, Guerrilla 450, Bear 650 and Himalayan 750 select Tripper Dash over Wi-Fi. Each model/firmware requires hardware qualification; Pod support is pending physical testing. No console software changes or installation.

Kotlin namespace/source packages: `com.sxdpz23.navisarthi`. Installed Android application ID: `com.navisarthi.app`.

## Current status

Version 0.6.0 introduces the liquid-glass Dash/Pod, Nav, optional Garage, Groups and Settings shell. Navigation source, services, resources and assets compile directly into NaviSarthi's application; there is one Application, Activity and Settings entry. Navigation includes offline maps/routing, search/places, traffic/transit, voices, saved places and Android Auto, adapted from Vela. See [integration and verification](docs/0.6.0-ui-integration-review.md). Physical Pod/Dash qualification is still required; see [Pod evidence and acceptance](docs/tripper-pod.md). Upstream APK update checks are disabled. NaviSarthi installs as `com.navisarthi.app`; existing Northstar data is not automatically migrated. Optional Firebase configuration must belong to this application ID.

## Build

Install Android SDK platform `android-37.0` and use a full JDK 17+ (Android Studio's JBR 21 works). Run `python scripts/fetch-navigation-runtimes.py` to fetch and verify the pinned navigation dependencies, then `./gradlew.bat :app:testDebugUnitTest :app:assembleRelease --max-workers=1` on Windows. The checked-in wrapper supplies Gradle 9.8. Optional Firebase and release signing use your own configuration; do not distribute upstream credentials. Without release signing configuration, the release build uses the debug key.

## Roadmap

See [version plans](<version plans/README.md>) for requirements, version milestones, and acceptance criteria. Plans are updated with every feature addition.

## Attribution

Derived from Northstar, copyright 2026 Aditya Dasika, under Apache-2.0. Navigation also includes adapted Vela source under GPL-3.0; see [navigation provenance and modifications](docs/navigation-upstream.md) and the bundled license/notices. See LICENSE and NOTICE for preserved upstream and third-party attribution. Independent project, not affiliated with Royal Enfield or Google.
