# LivingRealms Detail Matrix (Claude masterplan + A–Z + correction pass)

Statuses: `EXISTS` · `PARTIAL` · `IMPLEMENTING` · `COMPLETE`  
Source wins over docs. Linked Minecraft playtest remains an external gate.

| Detail | Authority | Status | Notes |
|---|---|---|---|
| Dual world (canonical ↔ MC) | SimulationState + materializers | PARTIAL | Projection present; EXTERNAL GATE: fresh-world client smoke |
| LOD PHYSICAL/REGIONAL/ABSTRACT | planners + budgets | COMPLETE | Budgets enforced; ProjectionStressTest green |
| City streets orthogonal + sidewalks | SettlementPlanner | COMPLETE | Orthogonal arterials/side streets/sidewalks; WorldgenQuality green |
| Intercity terrain corridor | TerrainCorridorPlanner | COMPLETE (core) | A* cost planner; no straight-road fallback; EXTERNAL GATE: mountain/pass linked proof |
| Real doors | FactionBlockPalette + applyDoor | COMPLETE (core) | Faction wood doors + optional Macaw DoorBlock polish; EXTERNAL GATE: Macaw mod present |
| Entrance access repair | EntranceAccessPlanner | COMPLETE | Stairs/landing ±3; switchbacks ±8; extreme >8 reject; down-grade path landing |
| Physical catch-up /setday | PhysicalDevelopmentReconciler | COMPLETE (core) | Multi-intent backlog; EXTERNAL GATE: day102 city visibility |
| Spawn capital city/castle | Density + planner | COMPLETE (core) | Plan/tests green; EXTERNAL GATE: linked visibility |
| Waystone 1/settlement + provenance | WaystoneSettlementRuntime + outer NBT | COMPLETE (core) | Only LR-authored stones destroyed; EXTERNAL GATE: Waystones mod smoke |
| Locate city/mine/kingdom/… | LocateQuery | COMPLETE (core) | city/mine/kingdom/market/port/wizardtrees/ruin; port works after naval discovery |
| NPC dialogue all villagers | DialogueSessionRuntime + CivilianNpcAdoption | COMPLETE (core) | AbstractVillager + allowlist; path/name role inference; EXTERNAL GATE: pack NPC smoke |
| UI blur removed (M/dialogue/dashboard) | client screens | COMPLETE (core) | No renderBackground; EXTERNAL GATE: visual confirm |
| M-map terrain always on | RealmWorldMapScreen + ClientTerrainMapCache | COMPLETE (core) | Cached surface (24k atlas) + ecology fallback; EXTERNAL GATE: client confirm |
| Court at capitals | CitizenMaterializationPlanner | COMPLETE (core) | Dynasty ruler/heir slots bound into capital projections |
| Local stockpile/market day (F2) | SettlementEconomyEngine + LocalMarketEngine | COMPLETE | Per-settlement stockpile, barn/granary, weekday market prices, tithe |
| Farming seasons (F3) | SettlementEconomyEngine + AgrarianProfile | COMPLETE (core) | Crop/livestock mix + three-field rotation; FOOD/TEXTILES proxy storage (schema-safe) |
| Goods chains (F4) | ResourceType + industry | COMPLETE (core) | Mill→bakery→brewery on FOOD proxy; full goods enum deferred (would need schema bump) |
| Trade routes (F5) | TradeEngine + TransportRoute + RumorEngine | COMPLETE (core) | Route speed/security/capacity; intercept insurance; price rumors; TradeLivenessTest |
| Jobs/wages (F6) | SocietyDiagnostics + payWages + GuildRank | COMPLETE (core) | Workplace slots; guild multipliers; apprenticeship milestones |
| Households/day rhythm (F7) | HouseholdState + wages/consumption | COMPLETE (core) | Wages → sharedWealth; famine spends wealth + granary |
| Unlimited city growth (F8) | SettlementPlanner housing | COMPLETE | Soft tier caps to 900; hard 132 ceiling removed |
| Rural hierarchy (F9) | SettlementDensitySeeder + tiers | COMPLETE | 4 rural hamlets/realm with farm/pasture/well seed |
| Terrain worldgen 60% (F10) | Density seeder | COMPLETE (core) | 32 settlements/realm (~380+); fertile density bias; authored+densifier (not fully emergent) |
| Infrastructure decay (F11) | TransportNetworkEngine | COMPLETE | Daily wear, treasury neglect, washouts, repair; water-mode classes |
| Religion depth (F12) | FaithCatalog + FaithEconomyHooks + holy-order patrol | COMPLETE (core) | Concrete deities/dogma/holy days/tithe; monthly holy-order presence reduces bandit pressure; EXTERNAL GATE: priest presentation |
| Calendar/festivals (F13) | CivilizationCalendar + CivicEvent + CivicFestivalMaterializer | COMPLETE (core) | Holy rites fire on actual holy days (day/day+1 vs clock); temporary CIVIC_FESTIVAL decorations + cleanup; siege/epidemic suppress festive types; EXTERNAL GATE: client visual confirm |
| Politics/law (F14) | Government/Justice/Dynasty | COMPLETE (core) | Succession crisis → legitimacy/stability/unrest; EXTERNAL GATE: court art |
| Bandits full (F15) | BanditArchetype + BanditEconomyEngine + Pirate* | COMPLETE (core) | Typed causes; route extortion; hideouts; EXTERNAL GATE: camp projection smoke |
| War/refugees (F16) | MigrationGroup + camps | COMPLETE (core) | Camp absorb/cap; well seed + pending house/farm/road materialization; REFUGEE_SUPPORT; FAMILY/PERSECUTION; EXTERNAL GATE: camp visual smoke |
| Epidemics (F17) | EpidemicRecord + CitizenRoutinePlanner | COMPLETE (core) | Soft cap + physical REST/clinic routines under plague stress |
| Justice flow (F18) | Crime/Justice/Custody | COMPLETE | INVESTIGATING dwell; restitution; HERESY/SMUGGLING |
| Naval (F19) | NavalEngine | COMPLETE (core) | Port discovery from geography + dock intents + fleets/combat; EXTERNAL GATE: ship visuals |
| Education (F20) | KnowledgeDomain | COMPLETE | Building-driven domains including CONSTRUCTION from keep/workshop/housing |
| Ecology↔people (F21) | SettlementEconomyEngine hinterland | COMPLETE | Hunt/lumber consume biomass/wildlife; scarcity lowers yields |
| Spy/propaganda (F22) | Intelligence/Propaganda | COMPLETE | COUNTERINTELLIGENCE + RELIGION/RECONSTRUCTION theme starts |
| MC projection polish (F23) | materializers | PARTIAL | Mill/bakery/brewery/pasture/dock; culture-aware palette; EXTERNAL GATE: linked smoke |
| UI depth (F24) | Dashboard/Map/Dialogue | COMPLETE (core) | DialogueTradeBridge quotes local prices; F12 Economy commits |
| Player roles (F25) | PlayerSettlementFounder + standing | COMPLETE (core) | found/locate/join/leave; EXTERNAL GATE: playtest |
| Balance/soak (F26) | LongRunSoakTest | COMPLETE | 3650 green |
| Release docs (F27) | PROJECT_STATE/README | COMPLETE | Aligned to source; external gates listed |

## Version pins
- Save schema **16** (1–15 readable; settlement barn/granary/stockpile)
- Dashboard protocol **14**
- ContentRevision **9** (typed authored-block ownership + geography discovery runtime; Waystone provenance; rev-6 construction rebuild)

## Phase 1 baseline (seed `123456789`, DemoSeeder; denser rural network TARGET=32)
| Gate | day | people | industry | animals≈ | wars | raids | shipments |
|---|---|---|---|---|---|---|---|
| year start | 0 | 219923 | 0 | 4295 | 0 | 0 | 0 |
| year end | 365 | ~278k | ~1550+ | ~1844 | ≥1 | ≥4 | 0 |
| soak 3650 | 3650 | ~1.12M | ~3300 | ~2437 | ≥1 | ≥12 | 0 |
