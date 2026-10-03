# LivingRealms Implementation Ledger — A–Z Production Completion

**Base:** checkpoint21 (`v3.0.0-RC4-buildfix13-checkpoint21`)  
**Working branch target:** `v3.0.0-RC4-buildfix14`  
**Authority rule:** source wins over docs. No parallel engines.

## Consolidated prompt (deduplicated)

Three overlapping prompts collapsed into one execution contract:

1. **A–Z master vision** — deepen existing authorities; Minecraft must show the living world.
2. **Worldgen/city/map/NPC correction pass** — roads, urbanism, doors/entrances, spawn capital, NPCs, map, F12, catch-up, Waystones, locate, density.
3. **Claude masterplan fases 1–27** — detail medieval living world on existing state (`PirateBand`, `SocialCitizen`, etc.); sim-first, no rewrites.

**Hard invariants (all prompts):** offline SP + integrated server authoritative; LOD; no duplicate systems; Guns++/GamingBarn NPC deny-list; schema/migration discipline; no blur on M/dialogue; terrain map always visible; one LR Waystone/settlement with provenance; day-jump physical reconciliation.

**Skip:** folder `Mc Livingrealms Mod` (not part of this repo).

### Status legend
`EXISTS` · `PARTIAL` · `IMPLEMENTING` · `COMPLETE`

COMPLETE only when: model + sim + save + runtime + feedback + integration + tests.

---

## Baseline (source truth)

| Item | Value |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Java | 21 |
| Create | 6.0.10 |
| Save schema | **15** (1–14 readable) |
| Dashboard protocol | **14** |
| ContentRevision | **7** (Waystone provenance; keeps rev-6 construction rebuild) |
| Dashboard key | F12 |
| Map key | M |

---

## Subsystem ledger

| Subsystem | Authority | Works | Partial / missing | Persist | MC runtime | UI | Tests | Status |
|---|---|---|---|---|---|---|---|---|
| Architecture/authority | `SimulationState` + engines | Dual world + LOD | Cross-system wiring gaps | schema 15 | adapters | dashboard | core suite | PARTIAL |
| City streets | `SettlementPlanner` | Orthogonal streets/sidewalks/housing | Lot→street polish | completion keys | materializer | map markers | WorldgenQuality | PARTIAL |
| Intercity roads | `TransportNetworkEngine` + `RouteProjectionPlanner` + `TerrainCorridorPlanner` | Terrain-cost corridors + bridges | Linked mountain/pass proof | routes | materializer | map routes | Worldgen+Production | PARTIAL |
| Doors/detail | `FactionBlockPalette` / blueprints | Real faction wood doors | Macaw optional polish | n/a | applyDoor | visible | ProductionQuality | PARTIAL |
| Entrances | `EntranceAccessPlanner` | ±3 grade stairs/landing | Extreme-site rejection polish | n/a | building ops | accessible | ProductionQuality | PARTIAL |
| Construction catch-up | `requestCatchup` + reconciler | Multi-intent backlog | Linked day102 city proof | completion | queue | growth visible | ProductionQuality | PARTIAL |
| Physical reconciliation | `PhysicalDevelopmentReconciler` | Deficit + prioritized backlog | Block-vs-completion validation | n/a | catch-up enqueue | — | ProductionQuality | PARTIAL |
| Spawn capital | density seeder + planner | City-scale + castle plan + court slots | Linked runtime proof | ContentRev 6/7 | materializer | map | WorldgenQuality | PARTIAL |
| Waystones | `WaystoneSettlementRuntime` + provenance | LR-only dedupe + outer NBT | Linked Waystones mod smoke | outer NBT | reflection | — | ProductionQuality | PARTIAL |
| Locate | `LocateQuery` + commands | city/mine/kingdom/market/port/wizardtrees/ruin | Linked command smoke | n/a | commands | chat | ProductionQuality | PARTIAL |
| NPC adoption | `DialogueSessionRuntime` + `CivilianNpcAdoption` | Villagers + allowlist | More pack adapters | SocialCitizen | interact | dialogue | SocietyDialogue | PARTIAL |
| Dialogue blur | client screens | Blur removed on M/dialogue/dashboard/catalog | Linked visual confirm | n/a | client | sharp UI | release-audit | PARTIAL |
| M-map terrain | `RealmWorldMapScreen` + `ClientTerrainMapCache` | Cached surface + ecology fallback | Full offline tile atlas | client cache | screen | always-on ground | release-audit | PARTIAL |
| Court/ruler visuals | `CitizenMaterializationPlanner` | Capital court official/heir/guard slots | Named dynasty binding | SocialCitizen | citizens | Politics | density | PARTIAL |
| Culture visuals | FactionCivilizationState | State | Architecture/clothing identity | schema | palette | dialogue | — | PARTIAL |
| Create industry | IndustryEngine | Projection | Kinetic verification (linked env) | schema | Create | Ops | — | PARTIAL |
| Wildlife visuals | EcologyEngine 134 spp | Sim strong | Morphology art | schema | entities | Ecology | soak | PARTIAL |
| Economy detail (Claude F2–5) | Market/Trade/PrimaryEconomy | Exists | Deeper stockpile/market day | may need schema | — | Economy | — | PARTIAL |
| Farming/seasons (F3) | PrimaryEconomy + calendar | Partial | Crop/livestock depth | maybe | farm loop | — | — | PARTIAL |
| Jobs/wages (F6) | Profession / SocialCitizen | Roles | Workplace slots/wages | maybe | routines | — | — | PARTIAL |
| Religion (F12) | faith state + temples | Partial | Hierarchy/rites | maybe | priests | — | — | PARTIAL |
| Bandits/piracy (F15) | PirateBand/Hideout/RaidParty | Exists | Full camp causality | schema | projection | map | — | PARTIAL |
| Refugees (F16) | MigrationGroup | Exists | Camp growth visibility | schema | mobile civ | map | — | PARTIAL |
| Epidemics (F17) | EpidemicRecord | Exists | Physical behaviour | schema | NPC activity | map/dialogue | — | PARTIAL |
| Docs | README/PROJECT_STATE/… | buildfix14 notes + DETAIL_MATRIX | Historical sections still mention older builds | — | — | — | — | PARTIAL |

---

## Phase tracker

| Phase | Focus | Status |
|---|---|---|
| 1 Source audit + ledger | This file + DETAIL_MATRIX | COMPLETE |
| 2 Physical world quality | Roads, doors, entrances, sites | PARTIAL (core done; linked proof pending) |
| 3 City reconciliation | Deficit + catch-up | PARTIAL |
| 4 NPC + blur | Dialogue sharpness, adoption | PARTIAL |
| 5 Map | Terrain base + cache | PARTIAL |
| 6 Waystones + locate + spawn | Provenance, commands | PARTIAL |
| 7–8 Visual civ + mods | Culture/court/adapters | PARTIAL |
| 9 Compile/runtime | `test-core` green; NeoForge Gradle needs wrapper/deps | PARTIAL |
| 10 Docs + ZIP | Final package | IMPLEMENTING |

---

## External verification (cannot claim COMPLETE without)

- Full NeoForge/Create linked client boot + create world + save/reload
- In-game Waystones mod present for provenance destroy path
- Kinetic Create network under chunk reload
- Better Villages / SecurityCraft / Macaw linked smoke
- This coding environment has no `gradlew`; linked `clean build` must be run where NeoForge/Create deps are available.
