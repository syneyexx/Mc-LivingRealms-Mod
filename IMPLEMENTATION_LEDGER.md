# LivingRealms Implementation Ledger — A–Z Production Completion

**Base:** checkpoint21 (`v3.0.0-RC4-buildf13-checkpoint21`)  
**Working branch target:** `v3.0.0-RC4-buildf15` (A–Z production completion pass)  
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
Where linked Minecraft smoke is required, status is **COMPLETE (core) / EXTERNAL GATE PENDING**.

---

## Baseline (source truth)

| Item | Value |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Java | 21 |
| Create | 6.0.10 |
| Save schema | **16** (1–15 readable; settlement stockpiles) |
| Dashboard protocol | **16** (cause summaries + Ops assistance task board) |
| ContentRevision | **10** (morphology completion rebuild; typed authored ownership; Waystone provenance) |
| Dashboard key | F12 |
| Map key | M |

---

## Subsystem ledger

| Subsystem | Authority | Works | Partial / missing | Persist | MC runtime | UI | Tests | Status |
|---|---|---|---|---|---|---|---|---|
| Architecture/authority | `SimulationState` + engines | Dual world + LOD | Linked boot smoke | schema 16 | adapters | dashboard | core suite | COMPLETE (core) / EXTERNAL GATE |
| City streets | `SettlementPlanner` | Orthogonal streets/sidewalks/housing | Linked visual confirm | completion keys | materializer | map markers | WorldgenQuality | COMPLETE (core) / EXTERNAL GATE |
| Intercity roads | Transport + TerrainCorridor | Terrain-cost corridors; no straight fallback | Linked mountain/pass proof | routes | materializer | map routes | Worldgen+Production | COMPLETE (core) / EXTERNAL GATE |
| Doors/detail | `FactionBlockPalette` | Faction wood doors + optional Macaw DoorBlock | Macaw mod present | n/a | applyDoor | visible | ProductionQuality | COMPLETE (core) / EXTERNAL GATE |
| Entrances | `EntranceAccessPlanner` | ±3 stairs; ±8 switchback; >8 reject; down path | — | n/a | building ops | accessible | ProductionQuality | COMPLETE |
| Construction catch-up | reconciler | Multi-intent backlog | Linked day102 proof | completion | queue | growth | ProductionQuality | COMPLETE (core) / EXTERNAL GATE |
| Physical reconciliation | `PhysicalDevelopmentReconciler` | Deficit + prioritized backlog | — | n/a | catch-up | — | ProductionQuality | COMPLETE |
| Spawn capital | density seeder + planner | City-scale + castle + court | Linked visibility | ContentRev 6–8 | materializer | map | WorldgenQuality | COMPLETE (core) / EXTERNAL GATE |
| Waystones | WaystoneSettlementRuntime | LR-only dedupe + provenance | Waystones mod smoke | outer NBT | reflection | — | ProductionQuality | COMPLETE (core) / EXTERNAL GATE |
| Locate | `LocateQuery` | city/mine/kingdom/market/**port**/wizardtrees/ruin | Linked command smoke | n/a | commands | chat | ProductionQuality+CompletionPass | COMPLETE (core) / EXTERNAL GATE |
| NPC adoption | Dialogue + CivilianNpcAdoption | Villagers + allowlist + role inference | Pack smoke | SocialCitizen | interact | dialogue | SocietyDialogue+CompletionPass | COMPLETE (core) / EXTERNAL GATE |
| Dialogue blur | client screens | Blur removed | Visual confirm | n/a | client | sharp UI | release-audit | COMPLETE (core) / EXTERNAL GATE |
| M-map terrain | RealmWorldMapScreen + cache | 24k terrain atlas + ecology fallback | Client confirm | client cache | screen | always-on ground | release-audit | COMPLETE (core) / EXTERNAL GATE |
| Court/ruler visuals | CitizenMaterializationPlanner + FactionCitizenMaterializer | Dynasty ruler/heir/regent at keep; government titles; court skins; dialogue awareness | Bespoke royal meshes | SocialCitizen | citizens | Politics/dialogue | CompletionPass | COMPLETE (core) / EXTERNAL GATE |
| Culture visuals | FactionCivilizationState → palette | Artistic/agrarian/martial style families | Deeper clothing art | schema | palette | dialogue | — | COMPLETE (core) / EXTERNAL GATE |
| Create industry | IndustryEngine | Projection + maintenance | Kinetic under unload | schema | Create | Ops | — | PARTIAL (EXTERNAL GATE) |
| Wildlife visuals | EcologyEngine 134 spp | Sim + LOD strong | Bespoke art | schema | entities | Ecology | soak | PARTIAL (assets) |
| Economy detail (F2–5) | SettlementEconomy + Trade | Stockpile/tithe/market/farms/chains/routes | Full goods enum deferred | schema 16 | markets | Economy/dialogue | SettlementEconomy+Trade* | COMPLETE (core) |
| Farming/seasons (F3) | AgrarianProfile | Named crop/livestock mix + rotation | — | schema 16 | farm/pasture/mill | — | SettlementEconomyTest | COMPLETE |
| Jobs/wages (F6) | payWages + GuildRank | Workplace slots + multipliers | Physical job polish | schema | routines | — | TradeHouseholdEcology | COMPLETE (core) |
| Religion (F12) | FaithCatalog | Deities/holy days/tithe/rites + holy-order patrol | Clergy careers depth | schema | priests | — | FaithAndInfrastructure+CivicHolyDay | COMPLETE (core) |
| Festivals (F13) | CivicEvent + CivicFestival* | Exact-day holy rites + temporary decorations | Client visual confirm | ledger sidecar | CivicFestivalMaterializer | dialogue | CivicHolyDayFestivalTest | COMPLETE (core) / EXTERNAL GATE |
| Assistance aid (F25+) | AssistanceTask + AssistanceContributionEngine | Verified delivery incl. INFRASTRUCTURE_REPAIR (stone→routes/industry/infra) | Linked delivery smoke | schema 16 tasks | `/livingrealms assist` | dialogue OFFER_TASK + Ops board | AssistanceContributionTest | COMPLETE (core) / EXTERNAL GATE |
| Bandits/piracy (F15) | BanditEconomy + Pirate* | Typed bands + extortion | Camp projection smoke | schema | projection | map | BanditAndPriceRumor | COMPLETE (core) / EXTERNAL GATE |
| Refugees (F16) | MigrationGroup | Camp growth + FAMILY/PERSECUTION | Camp building enqueue polish | schema | mobile civ | map | MobileCivilization | COMPLETE (core) |
| Epidemics (F17) | EpidemicRecord + routines | Soft cap + REST/clinic behaviour | — | schema | NPC activity | map/dialogue | CompletionPass | COMPLETE (core) |
| Naval (F19) | NavalEngine | **Port discovery** + docks + fleets | Ship visual kit | schema 6+ | ships | Forces | CompletionPass+SystemCompleteness | COMPLETE (core) / EXTERNAL GATE |
| Education (F20) | KnowledgeDomain | CONSTRUCTION grown from buildings | — | schema | schools | — | CompletionPass | COMPLETE |
| Docs | README/PROJECT_STATE/… | buildf15 notes + DETAIL_MATRIX | — | — | — | — | — | COMPLETE |

---

## Phase tracker

| Phase | Focus | Status |
|---|---|---|
| 0 Source audit + ledger | This file + DETAIL_MATRIX | COMPLETE |
| 1 Physical world quality | Roads, doors, entrances, docks | COMPLETE (core) |
| 2 Waystones + locate + ports | Provenance, commands, naval discovery | COMPLETE (core) |
| 3 NPC + dialogue + blur | Role inference, trade bridge, sharpness | COMPLETE (core) |
| 4 Map | Terrain atlas 24k + ecology fallback | COMPLETE (core) |
| 5–9 Economy/society/naval/etc. | F2–F22 deepenings | COMPLETE (core) |
| 10 War/military/aviation | Existing objectives/projection | COMPLETE (core) / EXTERNAL GATE |
| 11 Wildlife | Sim+LOD complete; art pending | PARTIAL (assets) |
| 12 Culture/court | Dynasty bind + culture palette | COMPLETE (core) |
| 13 Player agency | found/locate/standing | COMPLETE (core) |
| 14 Modpack safety | Deny-lists + foreign doors optional | COMPLETE (core) / EXTERNAL GATE |
| 15 LOD/projection stress | Budgets enforced | COMPLETE |
| 16 Docs + release truth | Aligned; external gates listed | COMPLETE |

---

## External verification (cannot claim full COMPLETE without)

1. Fresh-world client boot + create world + explore + save/reload
2. Migrated-world reload (schema 1–16 + ContentRevision ≤8)
3. Waystones mod present for provenance destroy path
4. Create kinetic network under chunk unload/reload
5. Full target modpack coexistence (Better Villages, SecurityCraft, Macaw, RU/BOP/Terralith, etc.)
6. Linked NeoForge/Create `clean build` where Gradle wrapper/deps are available (this environment may lack `gradlew`)
