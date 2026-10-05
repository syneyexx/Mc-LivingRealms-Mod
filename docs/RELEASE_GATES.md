# Living Realms v3.0 release gates

CURRENT PINS: schema 19 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

## Automated headless gate
```bash
./scripts/test-core.sh
python3 ./scripts/release-audit.py
```

Must compile with `--release 21 -Xlint:all -Werror` and pass density, goods chain, migration order, settlement transfer, war goals, dialogue tokens, roster, soak (3650), save migration 1→18, fuzz, and documentation pins.

## Full linked build
`build-production.sh` / `build-production.ps1`: headless gates + audit + Gradle `clean --no-build-cache build` + `write-release-manifest.py` for exact HEAD.

## Integrated singleplayer smoke
Fresh world + migrated world: boot, save lifecycle, F12/M/K, join/leave/found/locate, market near completed market key, setday spread, conquest/dialogue smoke. See `docs/MANUAL_RUNTIME_TEST_PLAN.md`.

## Geschiedenis
- Older gate docs cited schema 16–17 / ContentRevision 11 and denser settlement targets.
