#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/core-test"
LIST="$ROOT/scripts/core-tests.list"
rm -rf "$OUT" && mkdir -p "$OUT"
find "$ROOT/src/main/java/dev/livingrealms/sim" "$ROOT/src/main/java/dev/livingrealms/api" "$ROOT/src/testCore/java" -name '*.java' -print0 | xargs -0 javac --release 21 -Xlint:all -Werror -d "$OUT"
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    ''|\#*) continue ;;
  esac
  java -cp "$OUT" "$line"
done < "$LIST"
