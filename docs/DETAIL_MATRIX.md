# LivingRealms Detail Matrix (Claude masterplan + A–Z + correction pass)

Statuses: `EXISTS` · `PARTIAL` · `IMPLEMENTING` · `COMPLETE`  
Source wins over docs. Linked Minecraft playtest remains an external gate.

| Detail | Authority | Status | Notes |
|---|---|---|---|
| Dual world (canonical ↔ MC) | SimulationState + materializers | PARTIAL | Projection present; density/reconciliation improved in buildf14 |
| LOD PHYSICAL/REGIONAL/ABSTRACT | planners + budgets | EXISTS | Keep bounded |
| City streets orthogonal + sidewalks | SettlementPlanner | EXISTS | Checkpoint21+ |
| Intercity terrain corridor | TerrainCorridorPlanner | IMPLEMENTING→PARTIAL | A* cost planner wired; linked terrain proof pending |
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
| Local stockpile/market day (F2) | Market/Faction stockpile | PARTIAL | Existing markets; deeper village stock later |
| Farming seasons (F3) | PrimaryEconomy + calendar | PARTIAL | |
| Goods chains (F4) | ResourceType + industry | PARTIAL | |
| Trade routes (F5) | TradeEngine + TransportRoute | EXISTS | |
| Jobs/wages (F6) | Profession/SocialCitizen | PARTIAL | |
| Households/day rhythm (F7) | HouseholdState + ActivityCycle | PARTIAL | |
| Unlimited city growth (F8) | SettlementPlanner housing | PARTIAL | Cap raised via denser plans; not infinite |
| Rural hierarchy (F9) | Settlement tiers | EXISTS | |
| Terrain worldgen 60% (F10) | Density seeder | PARTIAL | Seeded realms, not fully emergent |
| Infrastructure decay (F11) | Transport quality | PARTIAL | |
| Religion depth (F12) | faith + temples | PARTIAL | |
| Calendar/festivals (F13) | CivilizationCalendar + CivicEvent | PARTIAL | |
| Politics/law (F14) | Government/Justice | PARTIAL | |
| Bandits full (F15) | RaidParty/Pirate* | PARTIAL | |
| War/refugees (F16) | War/MigrationGroup | PARTIAL | |
| Epidemics (F17) | EpidemicRecord | PARTIAL | |
| Justice flow (F18) | Crime/Justice/Custody | PARTIAL | |
| Naval (F19) | NavalEngine | PARTIAL | |
| Education (F20) | KnowledgeDomain | PARTIAL | |
| Ecology↔people (F21) | Ecology + jobs | PARTIAL | |
| Spy/propaganda (F22) | Intelligence/Propaganda | PARTIAL | |
| MC projection polish (F23) | materializers | PARTIAL | |
| UI depth (F24) | Dashboard/Map/Dialogue | PARTIAL | |
| Player roles (F25) | PlayerSettlementFounder + standing | PARTIAL | |
| Balance/soak (F26) | LongRunSoakTest | EXISTS | 3650 green |
| Release docs (F27) | PROJECT_STATE/README | IMPLEMENTING | |

## Version pins
- Save schema **15** (1–14 readable)
- Dashboard protocol **14**
- ContentRevision **7** (Waystone provenance; keeps rev-6 construction rebuild)
