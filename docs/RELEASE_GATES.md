# Living Realms v3.0 release gates

CURRENT PINS: schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

## Automated headless gate
```bash
./scripts/test-core.sh
python3 ./scripts/release-audit.py
```

Must compile with `--release 21 -Xlint:all -Werror` and pass density, goods chain, migration order, settlement transfer, war goals, dialogue tokens, roster, soak (3650), save migration **1→20**, fuzz, and documentation pins.

Release-state vocabulary (claim only what is proven):

| State | Meaning |
|-------|---------|
| `core-green` | Headless `./scripts/test-core.sh` + release-audit pass |
| `linked-build-green` | NeoForge+Create `clean --no-build-cache build` pass |
| `gametest-green` | GameTest server job discovers and passes Living Realms GameTests |
| `runtime-smoke-green` | Headless runtime-smoke + linked client checklist |
| `release-ready` | All of the above for the exact HEAD |

Do **not** label a build `release-ready` from core tests alone.

## Full linked build
`build-production.sh` / `build-production.ps1`: headless gates + audit + Gradle `clean --no-build-cache build` + `write-release-manifest.py` for exact HEAD.

## GameTests
CI job `gametest` runs NeoForge `gameTestServer` (see `scripts/run-gametests.sh` when present). Failures fail the job. If the runner cannot yet run reliably in CI, document that honestly — do not leave a silent `if: false` claiming coverage.

## Integrated singleplayer smoke
Fresh world + migrated world: boot, save lifecycle, F12/M/K, join/leave/found/locate, market near completed market key, setday spread, conquest/dialogue smoke. See `docs/MANUAL_RUNTIME_TEST_PLAN.md`.

## Geschiedenis
- Older gate docs cited schema 16–17 / ContentRevision 11 and denser settlement targets.
- Schema 19→20 added underworld contracts + stolen-goods ledger.
