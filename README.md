# NaviSarthi

Private Android navigation companion built on [Northstar](https://github.com/adityadasika21/NorthStar).

Targets Android 14+. Meteor 350 selects Tripper Pod over Bluetooth; Himalayan 450, Guerrilla 450, Bear 650 and Himalayan 750 select Tripper Dash over Wi-Fi. Each model/firmware requires hardware qualification; Pod support is pending physical testing. No console software changes or installation.

Kotlin namespace/source packages: `com.sxdpz23.navisarthi`. Installed Android application ID: `com.navisarthi.app`.

## Current status

Version 0.5.0 adds vehicle-specific Pod/Dash flows, Meteor's black-and-rose theme and the `com.sxdpz23.navisarthi` source namespace. Pod BLE interoperability and vehicle switching have JVM regression coverage; physical Pod/Dash qualification is still required. See [Pod evidence and acceptance](docs/tripper-pod.md). Upstream APK update checks are disabled. NaviSarthi installs as `com.navisarthi.app`; existing Northstar data is not automatically migrated. Optional Firebase configuration must belong to this application ID.

## Build

On Windows run `./gradlew.bat :app:assembleDebug`. Android SDK and a compatible JDK are required. Optional Firebase and release signing use your own configuration; do not distribute upstream credentials.

## Roadmap

See [version plans](<version plans/README.md>) for requirements, version milestones, and acceptance criteria. Plans are updated with every feature addition.

## Attribution

Derived from Northstar, copyright 2026 Aditya Dasika, under Apache-2.0. See LICENSE and NOTICE for preserved upstream and third-party attribution. Independent project, not affiliated with Royal Enfield or Google.
