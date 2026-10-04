#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"; cd "$ROOT"
java -version 2>&1 | grep -q 'version "21' || { echo 'Java 21 JDK required'; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo 'Python 3 is required for the release audit'; exit 1; }

CORE_SUITE_RESULT=fail
LINKED_BUILD_RESULT=fail
./scripts/test-core.sh
CORE_SUITE_RESULT=pass
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
  if [ ! -f "$ZIP" ]; then
    echo "Downloading Gradle $V..."
    if ! curl -fL "https://services.gradle.org/distributions/gradle-$V-bin.zip" -o "$ZIP"; then
      echo 'BOOTSTRAP FAILURE: could not download Gradle distribution'
      exit 2
    fi
  fi
  if ! verify_gradle_zip; then
    echo 'BOOTSTRAP FAILURE: Gradle checksum verification failed'
    exit 2
  fi
  if ! unzip -qo "$ZIP" -d "$CACHE"; then
    echo 'BOOTSTRAP FAILURE: could not extract Gradle distribution'
    exit 2
  fi
fi

echo "Running full NeoForge/Create linked build (clean, no build-cache evidence)..."
if ! "$HOME_DIR/bin/gradle" --no-daemon --no-build-cache clean build; then
  echo 'COMPILATION/LINKED BUILD FAILURE: Gradle clean build failed'
  exit 1
fi
LINKED_BUILD_RESULT=pass

JAR="$(find "$ROOT/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | sort | head -n1 || true)"
if [ -z "${JAR:-}" ] || [ ! -s "$JAR" ] || [ "$(stat -c%s "$JAR" 2>/dev/null || stat -f%z "$JAR")" -lt 1024 ]; then
  echo 'COMPILATION/LINKED BUILD FAILURE: no valid mod JAR under build/libs'
  exit 1
fi

python3 ./scripts/write-release-manifest.py \
  --core-suite "$CORE_SUITE_RESULT" \
  --linked-build "$LINKED_BUILD_RESULT" \
  --runtime-smoke unverified \
  --jar "$JAR"

echo "Build completed. JAR: $JAR"
echo 'CODE COMPLETE / EXTERNAL GATE UNVERIFIED until runtime smoke is proven.'
