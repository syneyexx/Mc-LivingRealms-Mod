#!/usr/bin/env bash
# Run NeoForge GameTestServer via a pinned Gradle bootstrap (same pattern as CI linked-build).
# Exit code is non-zero when any required GameTest fails (or when the server crashes).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

V=8.10.2
EXPECTED_SHA256=31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26
CACHE="$ROOT/.gradle-bootstrap"
HOME_DIR="$CACHE/gradle-$V"
ZIP="$CACHE/gradle-$V-bin.zip"
mkdir -p "$CACHE"
if [ ! -x "$HOME_DIR/bin/gradle" ]; then
  curl -fL "https://services.gradle.org/distributions/gradle-$V-bin.zip" -o "$ZIP"
  echo "$EXPECTED_SHA256  $ZIP" | sha256sum -c -
  unzip -qo "$ZIP" -d "$CACHE"
fi

echo "==> Compiling mod (classes needed by GameTestServer)"
"$HOME_DIR/bin/gradle" --no-daemon --no-build-cache classes

echo "==> Launching NeoForge GameTestServer (runGameTestServer)"
# ModDevGradle's RunGameTask extends JavaExec: Minecraft GameTestServer exit code is the
# number of required failed tests. Propagate that as this script's exit status.
set +e
"$HOME_DIR/bin/gradle" --no-daemon --no-build-cache runGameTestServer
STATUS=$?
set -e

if [ "$STATUS" -ne 0 ]; then
  echo "FAIL GameTests: gradle runGameTestServer exited $STATUS" >&2
  exit "$STATUS"
fi
echo "PASS GameTests: runGameTestServer completed with exit 0"
