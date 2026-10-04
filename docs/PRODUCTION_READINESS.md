# Living Realms v3.0.0-rc4 production readiness

## Status
**Production candidate, not yet release-complete.** Headless core suite is **38** tests green (including `FinalProductSystemsTest`, `EmergentStoryChainsTest`, and the 3650-day soak). Linked NeoForge/Create build passed for schema **17** / dashboard protocol **17** / ContentRevision **11**. Runtime smoke remains unverified. A fresh full-modpack client retest remains mandatory before claiming release-complete.

## Green automated gates
- Java 21 core compilation with `--release 21 -Xlint:all -Werror`.
- Deterministic 365-day replay and exact 3650-day soak with 30/365/3650 persistence checkpoints.
- 134-species bundled-data audit and strategic completeness suite.
- Projection stress/reconciliation for wildlife, caravans, citizens, armies, aircraft and fleets.
- Save migration coverage for schemas 1 through **17**.
- Current-state semantic validation on encode/decode and canonical ID watermark repair.
- Save corruption/truncation/trailing-data rejection, 32 MiB payload ceiling, 64 KiB string ceiling and strict UTF-8.
- SavedData payload checksum plus outer/inner schema consistency; legacy/pre-checksum rewrite path.
- 768-case deterministic one-bit save mutation fuzz gate.
- Runtime input hardening for simulation configuration, world positions and dashboard rate-limit lifecycle.
- Static release audit for side safety, target-mod compatibility policy, required assets, version pins and production-build parity.
- Gradle 8.10.2 bootstrap SHA-256 verification in Linux/Windows production runners.
- Emergent story chain gates for family mobility, trade escort/partial loss, and sovereign debt pressure.

## Linked/runtime verification status
- Linked NeoForge/Create `clean --no-build-cache build` succeeds in the Cloud Agent environment (Gradle 8.10.2 checksum-verified) and emits `RELEASE_MANIFEST.json` + `livingrealms-3.0.0-rc4.jar`.
- External Windows verification has previously proven an RC4 JAR can be loaded in a full Minecraft 1.21.1 modpack using NeoForge 21.1.252 and Create 6.0.10.
- World creation, player login and dashboard opening succeeded.
- A later exploration/progression smoke exposed `minecraft:generic.flying_speed` missing from a Living Realms Common Raven using `FlyingMoveControl`; buildfix9 registers and profiles that attribute.
- A repeat runtime smoke is mandatory before declaring the integrated-world gate green, including Waystone placement, old-structure rebuild, roads/sidewalks, player-founded settlement and NPC equipment behavior.

## Mandatory external verification before calling v3.0 released
1. Dependency-linked `clean build` on Java 21 with NeoForge 21.1.219 and Create 6.0.10-280.
2. Fresh integrated singleplayer client/server boot.
3. Current and migrated Minecraft SavedData save/quit/reopen/reload lifecycle.
4. Real dashboard request/snapshot/action round-trip and spoof/invalid-action rejection.
5. Chunk unload/reload/teleport projection lifecycle with no duplicates or canonical loss.
6. Faction property crime and physical market accounting in-world.
7. Worldgen coexistence across the target biome/worldgen stack.
8. Create projected-industry kinetic-network validation.
9. Full requested modpack smoke test, including Waystones placement/naming and RPG/magic/ranged equipment while verifying that `mr_guns` and GamingBarn gun items are excluded.

See `docs/RELEASE_GATES.md` for the detailed test procedure.

## Current acceptance focus
- Influence unlock actions, court/heraldry presentation, military formations, caravan wagon modes, and settlement ambience are implemented in source; confirm visibly in-world.
- Schema 17 migrations from prior saves must round-trip without duplication.
- Manual runtime acceptance (Wave 249) remains the remaining external gate after automated gates stay green — see `docs/MANUAL_RUNTIME_TEST_PLAN.md`.
