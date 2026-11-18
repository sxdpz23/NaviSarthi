#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Darwin ]]; then
  echo "This verification requires macOS, Xcode and its selected command-line tools." >&2
  exit 1
fi
echo "Stage: portable core tests"
swift test
echo "Stage: simulator compilation"
xcodebuild -project NaviSarthi.xcodeproj -scheme NaviSarthi \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath DerivedData CODE_SIGNING_ALLOWED=NO build
echo "Simulator compilation complete. Signing and hardware validation remain separate."
