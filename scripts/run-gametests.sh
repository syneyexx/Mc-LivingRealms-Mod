#!/usr/bin/env bash
# Living Realms NeoForge GameTest runner.
# Compiles the mod and launches gameTestServer; non-zero exit fails the suite.
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
G="$HOME_DIR/bin/gradle"

echo "== Living Realms GameTests (NeoForge gameTestServer) =="
# --no-build-cache keeps the run honest for release gates.
"$G" --no-daemon --no-build-cache runGameTestServer
echo "PASS scripts/run-gametests.sh"
