# Living Realms delivery roadmap

This roadmap reflects the current **v3.0.0-rc4** codebase. The older prototype checklist was retired because most of its core simulation items are already implemented and tested.

## Product scope
- [x] Offline singleplayer only.
- [x] Integrated logical server remains authoritative for canonical state.
- [x] Dedicated-server/internet multiplayer is not a release target.
- [x] External LLM integration is not a release target.
- [x] Minecraft 1.21.1 + NeoForge 21.1.219 + Create 6.0.10 + Java 21 is the pinned production stack.

## v3.0 — feature-complete singleplayer candidate
- [x] Deterministic civilization/ecology core.
- [x] Versioned persistent world state through schema 10.
- [x] Save migration support for schemas 1-10.
- [x] Strict save corruption/truncation/trailing-data regression gates.
- [x] 134-species bundled ecology pack and spatial biome overlay.
- [x] Factions, settlements, government, society, diplomacy, war, sieges and conquest.
- [x] Markets, trade, physical caravans, transport networks and primary economy.
- [x] Persistent industry plus bounded Create machinery projection.
- [x] Crime, notoriety, property theft, bounty hunting and custody.
- [x] Player faction membership, rank/service, tax controls and settlement development policies.
- [x] Aviation, naval systems, military physical projection and loss reconciliation.
- [x] Server-authoritative 14-tab dashboard with bounded protocol v12 snapshots.
- [x] Runtime performance profiles persisted in the world.
- [x] Fixed target-modpack integration policy, including Iron's Lib, registry/API compatible content integration, Create Deep Seas/Aeronautics exclusions, explicit Guns++ and GamingBarn's Guns NPC deny-lists.
- [x] Exact 3650-day deterministic soak with 30/365/3650 persistence/invariant checkpoints.
- [x] Projection stress gates for wildlife, caravans, citizens, military, aircraft and ships.
- [x] Production persistence hardening: semantic encode/decode validation, checksum/schema consistency, bounded payload/string sizes, strict UTF-8 and deterministic mutation fuzzing.
- [x] Reset-safe dashboard rate limiting and canonical ID high-watermark repair.
- [x] Dense living-world baseline: 12 surface kingdoms / 216 surface settlements, plus the hidden Wizard Trees faction/colonies with cities, towns, villages, hamlets, regional routes, local streets and sidewalks.
- [x] Player-founded realms, settlement growth construction and foreign vanilla/modded settlement adoption.
- [x] Dedicated **M** strategic world map and creative-only **K** registered mod-item catalog.
- [x] Physical civilian work loops for lumberjacks, farmers, miners, fishers and hunters.
- [x] Deterministic civilian names/appearance variants plus visible guard/military armor and held equipment.
- [x] Flying-wildlife `generic.flying_speed` regression hardening.

## v3.0 release blockers
- [ ] Full dependency-linked NeoForge/Create Gradle compile.
- [ ] Clean integrated singleplayer-server boot smoke test.
- [ ] Clean client boot smoke test.
- [ ] Real Minecraft SavedData create/save/close/reopen/reload test.
- [ ] Real integrated-server dashboard request/response/action round-trip.
- [ ] Create 6.0.10 kinetic-network verification for projected industry yards.
- [ ] Full requested modpack smoke test in a normal play world.
- [ ] Confirm no projection duplication/orphans during real chunk load/unload and teleport stress.

## v3.x — integration/content hardening
- [ ] Fix every issue found by the linked build and in-game smoke matrix before adding new strategic systems.
- [ ] Expand bespoke faction architecture/material palettes where generic fallback visuals remain.
- [ ] Improve wildlife morphology-specific meshes, textures and animations beyond shared families.
- [ ] Improve aircraft, ship, military, caravan and bounty-hunter presentation.
- [ ] Add court/ruler presentation and stronger faction heraldry.
- [ ] Polish Create industrial yards after kinetic validation without moving canonical production authority into loaded chunks.
- [ ] Performance profiling in a long-running modded singleplayer world; tune presets from measurements rather than guesses.

## v4.0 promotion criteria
v4.0 is a production milestone, not a feature-number target. Promotion requires all v3.0 release blockers to be closed plus:
- [ ] Long-world playtest with repeated save/reload, exploration, war, markets, industry, ecology and modded-biome traversal.
- [ ] No known save corruption or migration defects across the supported schema range.
- [ ] No canonical-state loss or duplication from physical projection lifecycle.
- [ ] Stable frame/tick performance under the intended modpack on the target singleplayer hardware profile.
- [ ] Player-facing feedback for every gameplay system retained in the Definition of Done matrix.
- [ ] Remaining presentation/content debt explicitly accepted or completed; no hidden core-system TODOs.

See `COMPLETION_MATRIX.md` for subsystem-level status and `docs/RELEASE_GATES.md` for the exact release verification sequence.
