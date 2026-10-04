# Living Realms v3.0.0-rc4 production readiness

## Status
**Production candidate, not yet release-complete.** Headless core suite is **34** tests green, including holy-day civic rites, festival decoration planning, and verified assistance contributions. The dependency-linked build and first real full-modpack boot/world-generation smoke have succeeded externally on Windows for prior RC4 tips. A fresh full-modpack retest and a linked rebuild of this exact commit remain mandatory before claiming release-complete.

## Green automated gates
- Java 21 core compilation with `--release 21 -Xlint:all -Werror`.
- Deterministic 365-day replay and exact 3650-day soak with 30/365/3650 persistence checkpoints.
- 134-species bundled-data audit and strategic completeness suite.
- Projection stress/reconciliation for wildlife, caravans, citizens, armies, aircraft and fleets.
- Save migration coverage for schemas 1 through 16.
- Current-state semantic validation on encode/decode and canonical ID watermark repair.
- Save corruption/truncation/trailing-data rejection, 32 MiB payload ceiling, 64 KiB string ceiling and strict UTF-8.
- SavedData payload checksum plus outer/inner schema consistency; legacy/pre-checksum rewrite path.
- 768-case deterministic one-bit save mutation fuzz gate.
- Runtime input hardening for simulation configuration, world positions and dashboard rate-limit lifecycle.
- Static release audit for side safety, target-mod compatibility policy, required assets, version pins and production-build parity.
- Gradle 8.10.2 bootstrap SHA-256 verification in Linux/Windows production runners.

## Linked/runtime verification status
- Linked NeoForge/Create `clean --no-build-cache build` now succeeds in the Cloud Agent environment (Gradle 8.10.2 checksum-verified) and emits `RELEASE_MANIFEST.json` + `livingrealms-3.0.0-rc4.jar`.
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
- Existing RC4 saves upgrade once through `ContentRevision=9` and schema **16**. Starter density is twelve kingdoms + Wizard Trees with **380+** settlements. Construction completion requires physically acceptable required geometry + continuity rules; typed provenance via `WorldMutationGuard` protects player/foreign builds; physical workers do not mutate canonical stockpiles.
- M opens the world map and visibly reports discovered ecology/biomes, kingdoms, settlements, routes, armies and war fronts without client-authoritative state.
- K in creative opens the searchable live-registry item catalog; item spawning must be rejected server-side for non-creative players or invalid item IDs.
- `/livingrealms locate city` and `/livingrealms locate mine` return canonical coordinates; `/livingrealms found Newhaven` creates a player-controlled realm only when location/membership rules allow it.
- Loaded vanilla/modded villages and qualifying structure starts are adopted without immediate replacement of their existing buildings, then participate in future Living Realms growth.
- Actual world spawn is covered by a Living Realms kingdom/city.
- Nearby settlements visibly contain roads with sidewalks, varied buildings, named/visually varied civilians and a Waystone when Waystones is installed.
- Civilian lumber/farm/mine/fish/hunt loops visibly operate and feed canonical resources without indiscriminately destroying player builds.
- Guards/soldiers visibly hold/wear discovered compatible target-mod equipment; Guns++ and GamingBarn's Guns must never be chosen for NPCs.
- Flying wildlife, especially Common Raven, must survive multi-minute ticking without `generic.flying_speed` exceptions.
- Population growth must create additional housing/infrastructure over time rather than keeping the physical settlement frozen.
