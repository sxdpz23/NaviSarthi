# SDK setup on the Mac

The base project compiles its local core and Apple framework adapters without a
third-party dependency. Firebase account/cloud backup uses Firebase's HTTPS REST
APIs; no Firebase SDK or service-account credentials are required.

Ride audio is implemented behind `#if canImport(LiveKit)`. To enable that code,
link the official LiveKit Swift package/product on a Mac:

```bash
cd iOS
bash tools/setup-audio-macos.sh
bash tools/verify-macos.sh
```

Use Xcode 26+ for the recent LiveKit SDK. The setup script specifies a 2.18.0
minimum within major version 2 and uses the native Swift XcodeProj helper bundled
with the Firebase Xcode project setup skill. Xcode resolves the actual package
version; retain the resulting Package.resolved and test before distribution.
The source-only Windows delivery has not resolved, downloaded or linked it.

The helper's `Package.swift`, `Package.resolved` and `Sources/main.swift` are
included under `tools/xcode_spm_setup` so the receiving Mac does not need the
Codex plugin installed. Its own helper packages are fetched on first execution.

Do **not** run `generate_project.py` after installing packages; it generates the
dependency-free baseline project. `validate_project.py` is read-only with respect
to project configuration and reports extended projects instead of overwriting them.
Use `xcodebuild` for an extended project's verification.

The [Xcode setup skill](C:/Users/Asus/.codex/plugins/cache/openai-curated-remote/firebase/1.2.0/skills/xcode-project-setup/SKILL.md)
requires: “If the Swift command is not found, you must stop”. `swift --version`
failed on this Windows machine, so dependency installation has been deferred,
while implementation in existing source files continued. The user has already
specified preparation for Xcode without a Mac; no new approval is required to
run the provided setup on their eventual Mac.

Audio needs a server with LiveKit configured, Microphone permission and the
background audio capability already declared in Info.plist. No camera track or
recording is published. The current server grants microphone-only publication.
Test mute, Bluetooth headsets, interruption handling, network reconnection,
membership removal and group expiry on real devices.

References:

- [LiveKit Swift SDK](https://docs.livekit.io/transport/sdk-platforms/swift/)
- [Official SDK source](https://github.com/livekit/client-sdk-swift)
- [Firebase Auth REST](https://firebase.google.com/docs/reference/rest/auth)
- [Firestore REST authentication and owner rules](https://firebase.google.com/docs/firestore/use-rest-api)

Additional optional Mac helpers: setup-sharing-macos.sh embeds the Share Extension;
setup-google-macos.sh configures native OAuth; setup-push-macos.sh links FirebaseCore
and FirebaseMessaging using the latest stable release reported at execution time,
adds the public iOS configuration plist and declares Push capability. Retain the
resolved SDK versions and repeat verification. APNs/private signing keys belong
outside source. No SDK or native helper has been resolved/executed on Windows.
prepare-macos.sh composes these based on supplied environment variables.
