#!/usr/bin/env bash
set -euo pipefail
VERSION=8.10.2
ROOT="$(cd "$(dirname "$0")" && pwd)"
CACHE="$ROOT/.gradle-bootstrap"
ZIP="$CACHE/gradle-$VERSION-bin.zip"
HOME_DIR="$CACHE/gradle-$VERSION"
mkdir -p "$CACHE"
if [[ ! -d "$HOME_DIR" ]]; then
  if [[ ! -f "$ZIP" ]]; then
    curl -fL "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
  fi
  unzip -q -o "$ZIP" -d "$CACHE"
fi
"$HOME_DIR/bin/gradle" --no-daemon clean build
printf 'Built JAR(s) are in build/libs\n'
