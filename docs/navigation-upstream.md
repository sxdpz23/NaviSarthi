# Navigation source and dependencies

Navigation source was adapted from [Vela Maps](https://github.com/PimpinPumpkin/Vela), revision `6cab47ae81cf8988b12c02169dae72b22c8b815c`, using the read-only reference checkout `D:/Softwares/Projects/OfflineNav-Vela`.

The former app/core sources now compile directly in NaviSarthi's `:app` source set under `com.sxdpz23.navisarthi.navigation`. Resources, fonts, map assets, pronunciation data and notices are in the main application's resource/asset directories. There is one `NaviSarthiApplication`, one `MainActivity`, and one Settings entry. No `app.vela` packages or separate navigation application module are used.

Local modifications include package/type/resource naming, consolidated Application initialization and Activity/intent/PiP behaviour, host Settings routing, native tab integration and optical material, map viewport/rendering insets, a route adapter for Dash/Pod, single guidance ownership, app-specific onboarding copy, and disabled upstream APK checks. The existing NaviSarthi package ID and version sequence are retained.

The imported source is GPL-3.0. The full license and source provenance are bundled at `assets/licenses/Navigation-GPL-3.0.txt` and `assets/licenses/Navigation-NOTICES.txt`; the font license remains alongside them. The in-app navigation credits page exposes the source and software license. Rebranding does not remove original authorship or third-party rights.

Published upstream data URLs remain provider URLs: they supply region manifests, map/POI tiles, fonts, runtime artifacts and signed calibration data. NaviSarthi does not pretend to host those datasets. App update controls belong to NaviSarthi and never select an upstream Vela APK.

Run `python scripts/fetch-navigation-runtimes.py` before a fresh build. It downloads the pinned OsmAnd jars, XML parser, Cronet and sherpa-onnx AARs and verifies their SHA-256 hashes. `:navigation-runtime` is a Java-only protobuf relocation step for OsmAnd, avoiding a collision with Cronet; it has no application manifest, Activity or resources.

Build with Android SDK `platforms/android-37.0`, a full JDK 17+ (this machine uses Android Studio's JBR 21), and the checked-in Gradle wrapper. Runtime binaries and generated build output are ignored. Feature retention is assessed at source level; see the integration review for the actual verification boundary.
