#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Darwin ]]; then
  echo "Archiving requires macOS and Xcode." >&2
  exit 1
fi
if [[ -z "${APPLE_TEAM_ID:-}" ]]; then
  echo "Set APPLE_TEAM_ID to your Apple Developer team ID first." >&2
  exit 1
fi
bash tools/verify-macos.sh
delivery="$(pwd)/../builds and versions/iOS/0.7.1"
mkdir -p "$delivery"
echo "Stage: device compilation, packaging and signing"
xcodebuild -project NaviSarthi.xcodeproj -scheme NaviSarthi \
  -configuration Release -destination 'generic/platform=iOS' \
  -archivePath "$delivery/NaviSarthi.xcarchive" \
  DEVELOPMENT_TEAM="$APPLE_TEAM_ID" archive
echo "Stage: signature verification"
codesign --verify --deep --strict "$delivery/NaviSarthi.xcarchive/Products/Applications/NaviSarthi.app"
echo "Archive delivered to $delivery/NaviSarthi.xcarchive"
echo "Export a development/ad-hoc IPA through Xcode Organizer using your provisioning profile."
