# LivingRealms Detail Matrix (Claude masterplan + A–Z + correction pass)

Statuses: `EXISTS` · `PARTIAL` · `IMPLEMENTING` · `COMPLETE`  
Source wins over docs. Linked Minecraft playtest remains an external gate.

| Detail | Authority | Status | Notes |
|---|---|---|---|
| Dual world (canonical ↔ MC) | SimulationState + materializers | PARTIAL | Projection present; density/reconciliation improved in buildf14 |
| LOD PHYSICAL/REGIONAL/ABSTRACT | planners + budgets | EXISTS | Keep bounded |
| City streets orthogonal + sidewalks | SettlementPlanner | EXISTS | Checkpoint21+ |
| Intercity terrain corridor | TerrainCorridorPlanner | PARTIAL | A* cost planner wired; linked terrain proof pending |
| Real doors | FactionBlockPalette + applyDoor | PARTIAL | Vanilla wood doors by faction style |
| Entrance access repair | EntranceAccessPlanner | PARTIAL | Stairs/landing for ±3 grade |
| Physical catch-up /setday | PhysicalDevelopmentReconciler | PARTIAL | Multi-intent backlog during catch-up |
| Spawn capital city/castle | Density + planner | PARTIAL | Plan/tests green; linked visibility pending |
| Waystone 1/settlement + provenance | WaystoneSettlementRuntime + outer NBT | PARTIAL | Only LR-authored stones destroyed |
| Locate city/mine/kingdom/… | LocateQuery | PARTIAL | Core-tested; linked command smoke pending |
| NPC dialogue all villagers | DialogueSessionRuntime + CivilianNpcAdoption | PARTIAL | AbstractVillager + allowlist |
| UI blur removed (M/dialogue/dashboard) | client screens | PARTIAL | No renderBackground |
| M-map terrain always on | RealmWorldMapScreen + ClientTerrainMapCache | PARTIAL | Cached surface when loaded; ecology fallback |
| Court at capitals | CitizenMaterializationPlanner | PARTIAL | Official/heir/court-guard slots |
| Local stockpile/market day (F2) | SettlementEconomyEngine + LocalMarketEngine | COMPLETE | Per-settlement stockpile, barn/granary, weekday market prices, tithe (no free pop* mint) |
| Farming seasons (F3) | SettlementEconomyEngine + calendar | PARTIAL | Seasonal farms, pastures, weather stress, mills/bakeries/breweries; crop enum still FOOD proxy |
| Goods chains (F4) | ResourceType + industry | PARTIAL | Mill→bakery→brewery chain on FOOD proxy; full goods enum deferred (codec-safe) |
| Trade routes (F5) | TradeEngine + TransportRoute | PARTIAL | Price-differential dispatch; local+faction draw; split delivery |
| Jobs/wages (F6) | SocietyDiagnostics + payWages | PARTIAL | Workplace-slot jobCapacity + monthly skill-scaled wages |
| Households/day rhythm (F7) | HouseholdState + ActivityCycle | PARTIAL | |
| Unlimited city growth (F8) | SettlementPlanner housing | PARTIAL | Soft tier caps (to 900); hard 132 ceiling removed |
| Rural hierarchy (F9) | Settlement tiers + pastures/mills | PARTIAL | Pastures/mills/bakeries planned |
| Terrain worldgen 60% (F10) | Density seeder | PARTIAL | Seeded realms, not fully emergent |
| Infrastructure decay (F11) | Transport quality | PARTIAL | |
| Religion depth (F12) | faith + temples | PARTIAL | |
| Calendar/festivals (F13) | CivilizationCalendar + CivicEvent | PARTIAL | |
| Politics/law (F14) | Government/Justice | PARTIAL | |
| Bandits full (F15) | RaidParty/Pirate* | PARTIAL | Deserter→band+hideout; loot to hideout/origin; soft raid cap |
| War/refugees (F16) | MigrationGroup + camps | PARTIAL | Camp absorb/cap, farm/well seed, REFUGEE_SUPPORT tasks, persecution reason |
| Epidemics (F17) | EpidemicRecord | PARTIAL | Soft epidemic cap under plague stress |
| Justice flow (F18) | Crime/Justice/Custody | PARTIAL | |
| Naval (F19) | NavalEngine | PARTIAL | |
| Education (F20) | KnowledgeDomain | PARTIAL | |
| Ecology↔people (F21) | Ecology + hinterland hunt/forestry | PARTIAL | |
| Spy/propaganda (F22) | Intelligence/Propaganda | PARTIAL | |
| MC projection polish (F23) | materializers | PARTIAL | New mill/bakery/brewery/pasture blueprints |
| UI depth (F24) | Dashboard/Map/Dialogue | PARTIAL | Dialogue uses local prices/granary |
| Player roles (F25) | PlayerSettlementFounder + standing | PARTIAL | |
| Balance/soak (F26) | LongRunSoakTest | EXISTS | 3650 green after local-economy rebalance |
| Release docs (F27) | PROJECT_STATE/README | IMPLEMENTING | |

## Version pins
- Save schema **16** (1–15 readable; settlement barn/granary/stockpile)
- Dashboard protocol **14**
- ContentRevision **7** (Waystone provenance; keeps rev-6 construction rebuild)

## Phase 1 baseline (seed `123456789`, DemoSeeder, after local-economy wiring)
| Gate | day | people | industry | animals≈ | wars | raids | shipments |
|---|---|---|---|---|---|---|---|
| year start | 0 | 256667 | 0 | 4295 | 0 | 0 | 0 |
| year end | 365 | 312390 | 1471 | 1844 | 0 | 2 | 0 |
| soak 3650 | 3650 | 1105350 | 2868 | 2437 | 1 | 8 | 0 |
