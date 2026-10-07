# Living Realms v3.0 release gates

CURRENT PINS: schema 21 / minSchema 1 / protocol 20 / network 16 / contentRevision 18 / starterSettlements 204-300 / perRealm 17-25 / capitalSpacing 3000-4500 / roleAwareSpacing

Release states (precise):
- **core-green**: `./scripts/test-core.sh` + `python3 ./scripts/release-audit.py` pass on this pin set.
- **linked-green**: core-green plus Gradle `clean --no-build-cache build` (NeoForge/Create linked jar).
- **gametest-green**: linked-green plus `./scripts/run-gametests.sh` (`gradle runGameTestServer`) exit 0.
- **release-candidate**: linked-green + gametest-green + manual smoke (`docs/MANUAL_RUNTIME_TEST_PLAN.md`).

Feature maturity (FOUNDATION→POLISHED) is tracked separately in `COMPLETION_MATRIX.md` and must not be confused with these release states.

## Automated headless gate
```bash
./scripts/test-core.sh
python3 ./scripts/release-audit.py
```

Must compile with `--release 21 -Xlint:all -Werror` and pass density, goods chain, migration order, settlement transfer, war goals, dialogue tokens, roster, soak (3650), save migration 1→21, fuzz, documentation pins, and deterministic refactor proof.

## Full linked build
`build-production.sh` / `build-production.ps1`: headless gates + audit + Gradle `clean --no-build-cache build` + `write-release-manifest.py` for exact HEAD.

## GameTest CI
The `gametest` GitHub Actions job runs `./scripts/run-gametests.sh` after `linked-build`. It compiles the mod and launches NeoForge `runGameTestServer`; a non-zero GameTestServer exit fails the job.

Honest status: GameTestServer boots are heavier and more environment-sensitive than headless core tests (download/cache, memory, first-time asset prep). Prefer treating a red `gametest` job as a real regression unless logs show infra/bootstrap failure rather than an assertion. Do not casually pin NeoForge/Create/MC versions upward to “fix” flaky boots.

## Integrated singleplayer smoke
Fresh world + migrated world: boot, save lifecycle, F12/M/K, join/leave/found/locate, market near completed market key, setday spread, conquest/dialogue smoke. See `docs/MANUAL_RUNTIME_TEST_PLAN.md`.

## Geschiedenis
- Older gate docs cited schema 16–17 / ContentRevision 11 and denser settlement targets.
- Save migration range was previously documented as 1→18; decoder support is now minSchema 1 through schema 20.
