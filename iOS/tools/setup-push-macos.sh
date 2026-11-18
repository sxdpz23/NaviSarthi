#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Darwin ]]; then echo "Push SDK setup requires macOS, Swift and Xcode." >&2; exit 1; fi
swift --version
if [[ -z "${GOOGLE_SERVICE_INFO_PLIST:-}" || ! -f "$GOOGLE_SERVICE_INFO_PLIST" ]]; then
  echo "Set GOOGLE_SERVICE_INFO_PLIST to your downloaded iOS app configuration, not a service-account key." >&2
  exit 1
fi
sdk_version="$(curl --fail --silent --show-error https://api.github.com/repos/firebase/firebase-ios-sdk/releases/latest | /usr/bin/python3 -c 'import json,sys; print(json.load(sys.stdin)["tag_name"].removeprefix("v"))')"
if [[ ! "$sdk_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then echo "Could not determine the latest stable Firebase SDK version." >&2; exit 1; fi
destination="$(pwd)/Configuration/GoogleService-Info.plist"
if [[ "$GOOGLE_SERVICE_INFO_PLIST" != "$destination" ]]; then cp "$GOOGLE_SERVICE_INFO_PLIST" "$destination"; fi
swift run --package-path tools/xcode_spm_setup xcode_spm_setup \
  NaviSarthi.xcodeproj https://github.com/firebase/firebase-ios-sdk.git "$sdk_version" \
  --plist Configuration/GoogleService-Info.plist FirebaseCore FirebaseMessaging
swift run --package-path tools/xcode_spm_setup xcode_spm_setup --enable-push NaviSarthi.xcodeproj
echo "APNs and Firebase configuration must match your bundle ID and provisioning profile."
