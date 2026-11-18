#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Darwin ]]; then
  echo "Share target setup requires macOS, Swift and Xcode." >&2
  exit 1
fi
if [[ -z "${APPLE_APP_GROUP:-}" ]]; then
  echo "Set APPLE_APP_GROUP to your registered App Group, e.g. group.com.yourname.navisarthi" >&2
  exit 1
fi
swift --version
swift run --package-path tools/xcode_spm_setup xcode_spm_setup \
  --share-extension NaviSarthi.xcodeproj "$APPLE_APP_GROUP"
echo "Run bash tools/verify-macos.sh, then test Save to NaviSarthi from Maps and Files."
