#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Darwin ]]; then
  echo "Linking the LiveKit package requires a Mac with Swift and Xcode." >&2
  exit 1
fi
swift --version
swift run --package-path tools/xcode_spm_setup xcode_spm_setup \
  NaviSarthi.xcodeproj https://github.com/livekit/client-sdk-swift.git 2.18.0 LiveKit
echo "LiveKit linked. Run bash tools/verify-macos.sh and test a real ride audio room."
echo "Do not run generate_project.py afterward; it creates the dependency-free baseline project."
