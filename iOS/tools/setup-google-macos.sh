#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Darwin ]]; then
  echo "Google callback configuration requires the native Swift setup helper on a Mac." >&2
  exit 1
fi
if [[ -z "${GOOGLE_IOS_CLIENT_ID:-}" ]]; then
  echo "Set GOOGLE_IOS_CLIENT_ID to the public iOS OAuth client ID from your Firebase/Google Cloud project." >&2
  exit 1
fi
swift --version
swift run --package-path tools/xcode_spm_setup xcode_spm_setup \
  --google-oauth NaviSarthi.xcodeproj "$GOOGLE_IOS_CLIENT_ID"
