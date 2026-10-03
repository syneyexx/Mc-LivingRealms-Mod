#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"; cd "$ROOT"
java -version 2>&1 | grep -q 'version "21' || { echo 'Java 21 JDK required'; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo 'Python 3 is required for the release audit'; exit 1; }
./scripts/test-core.sh
python3 ./scripts/release-audit.py

V=8.10.2
EXPECTED_SHA256=31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26
CACHE="$ROOT/.gradle-bootstrap"; HOME_DIR="$CACHE/gradle-$V"; ZIP="$CACHE/gradle-$V-bin.zip"
verify_gradle_zip() {
  local actual
  if command -v sha256sum >/dev/null 2>&1; then actual="$(sha256sum "$ZIP" | awk '{print $1}')"
  elif command -v shasum >/dev/null 2>&1; then actual="$(shasum -a 256 "$ZIP" | awk '{print $1}')"
  else echo 'sha256sum or shasum is required to verify the Gradle bootstrap'; exit 1
  fi
  if [ "$actual" != "$EXPECTED_SHA256" ]; then
    echo "Gradle $V checksum mismatch: expected $EXPECTED_SHA256, got $actual"
    rm -f "$ZIP"
    exit 1
  fi
}
if [ ! -x "$HOME_DIR/bin/gradle" ]; then
  mkdir -p "$CACHE"
  if [ ! -f "$ZIP" ]; then curl -fL "https://services.gradle.org/distributions/gradle-$V-bin.zip" -o "$ZIP"; fi
  verify_gradle_zip
  unzip -qo "$ZIP" -d "$CACHE"
fi

echo "Running full NeoForge/Create linked build..."
"$HOME_DIR/bin/gradle" --no-daemon clean build
echo 'Build completed. Check build/libs.'
