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
| Farming seasons (F3) | SettlementEconomyEngine + AgrarianProfile | PARTIAL | Crop/livestock mix (grain/rye/barley/oats/veg/flax/grapes/hops + herds) + three-field rotation; still FOOD/TEXTILES proxy storage |
| Goods chains (F4) | ResourceType + industry | PARTIAL | Mill→bakery→brewery chain on FOOD proxy; full goods enum deferred (codec-safe) |
| Trade routes (F5) | TradeEngine + TransportRoute + RumorEngine | PARTIAL | Shipments use route speed/security/capacity; intercept insurance refund; price rumors |
| Jobs/wages (F6) | SocietyDiagnostics + payWages + GuildRank | PARTIAL | Workplace-slot jobCapacity; apprentice/journeyman/master wage multipliers; apprenticeship milestones |
| Households/day rhythm (F7) | HouseholdState + wages/consumption | PARTIAL | Wages → sharedWealth; famine spends wealth + granary food; needs track budget |
| Unlimited city growth (F8) | SettlementPlanner housing | PARTIAL | Soft tier caps (to 900); hard 132 ceiling removed |
| Rural hierarchy (F9) | SettlementDensitySeeder + tiers | PARTIAL | 4 rural hamlets/realm (Croft/Thorp/…) with farm/pasture/well seed; hierarchy cities/towns/villages/hamlets gated |
| Terrain worldgen 60% (F10) | Density seeder | PARTIAL | 32 settlements/realm (~387 total); rural placement biased to fertile biome centers; still authored realm list + densifier, not fully emergent from terrain |
| Infrastructure decay (F11) | TransportNetworkEngine | PARTIAL | Daily wear, treasury neglect, seasonal washouts, stone/coin repair; river/ship/caravan mode classes |
| Religion depth (F12) | FaithCatalog + FaithEconomyHooks | PARTIAL | Concrete deities/dogma/scripture/symbols/holy orders; holy/fast days; church tithe; dialogue + civic rites |
| Calendar/festivals (F13) | CivilizationCalendar + CivicEvent | PARTIAL | Holy-day rituals use faith deity titles |
| Politics/law (F14) | Government/Justice/Dynasty | PARTIAL | Ongoing succession crisis erodes legitimacy/stability/unrest; feeds rebellion chance |
| Bandits full (F15) | BanditArchetype + BanditEconomyEngine + Pirate* | PARTIAL | Typed causes (hunger/tax/deserter/smuggler/…); road extortion/ambush; outlaw legends; loot/hideouts |
| War/refugees (F16) | MigrationGroup + camps | PARTIAL | Camp absorb/cap, farm/well seed, REFUGEE_SUPPORT tasks, persecution reason |
| Epidemics (F17) | EpidemicRecord | PARTIAL | Soft epidemic cap under plague stress |
| Justice flow (F18) | Crime/Justice/Custody | PARTIAL | INVESTIGATING dwell before charge; restitution restores settlement; HERESY/SMUGGLING |
| Naval (F19) | NavalEngine | PARTIAL | |
| Education (F20) | KnowledgeDomain | PARTIAL | |
| Ecology↔people (F21) | SettlementEconomyEngine hinterland | PARTIAL | Hunt/lumber consume plant biomass + wildlife cohorts; scarcity lowers yields |
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

## Phase 1 baseline (seed `123456789`, DemoSeeder; denser rural network TARGET=32)
| Gate | day | people | industry | animals≈ | wars | raids | shipments |
|---|---|---|---|---|---|---|---|
| year start | 0 | 219923 | 0 | 4295 | 0 | 0 | 0 |
| year end | 365 | 278703 | 1559 | 1844 | 1 | 4 | 0 |
| soak 3650 | 3650 | 1117660 | 3297 | 2437 | 2 | 12 | 0 |
