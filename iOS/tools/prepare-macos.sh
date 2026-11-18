#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Darwin ]]; then
  echo "Preparation requires macOS, Swift and Xcode. No project changes made." >&2
  exit 1
fi
swift --version
echo "Stage: native branding setup"
swift run --package-path tools/xcode_spm_setup xcode_spm_setup --brand-assets NaviSarthi.xcodeproj
if [[ -n "${APPLE_APP_GROUP:-}" ]]; then bash tools/setup-sharing-macos.sh; fi
if [[ -n "${GOOGLE_IOS_CLIENT_ID:-}" ]]; then bash tools/setup-google-macos.sh; fi
if [[ "${ENABLE_RIDE_AUDIO:-0}" == 1 ]]; then bash tools/setup-audio-macos.sh; fi
if [[ -n "${GOOGLE_SERVICE_INFO_PLIST:-}" ]]; then bash tools/setup-push-macos.sh; fi
bash tools/verify-macos.sh
