#!/usr/bin/env bash
# Living Realms runtime smoke.
# Headless path (always runnable with JDK 21): found / save-load / day advance / escort identity / dashboard.
# Linked Minecraft path (client boot, F12, M map) remains an external gate — see docs/RELEASE_GATES.md.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/runtime-smoke"
rm -rf "$OUT" && mkdir -p "$OUT"

echo "== Headless runtime smoke (RuntimeSmokeTest) =="
find "$ROOT/src/main/java/dev/livingrealms/sim" \
     "$ROOT/src/testCore/java/dev/livingrealms/RuntimeSmokeTest.java" \
     -name '*.java' -print0 | xargs -0 javac --release 21 -Xlint:all -Werror -d "$OUT"
java -cp "$OUT" dev.livingrealms.RuntimeSmokeTest

echo
echo "== Linked Minecraft smoke checklist (manual / CI with NeoForge) =="
cat <<'EOF'
1. Boot client to title; start a fresh singleplayer world (no Living Realms classloading errors).
2. Press F12 — dashboard opens; press M — world map opens.
3. Run: /livingrealms found Smokehaven  (or Found button with custom name on Overview).
4. Save & quit, reopen world — membership/rulership/day persist.
5. Run: /livingrealms advance_day  — canonical day increments; construction catch-up may fire.
6. Near a high-value caravan, escorts spawn without crash (negative army-id escort namespace).
EOF

echo
echo "PASS scripts/runtime-smoke.sh (headless). Mark RELEASE_MANIFEST runtimeSmoke=pass only after linked checklist also passes."
