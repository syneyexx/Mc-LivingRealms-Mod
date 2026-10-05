# LivingRealms Detail Matrix

CURRENT PINS: schema 19 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

Source wins over docs. Status values for current work: EXISTS · IMPLEMENTING · COMPLETE.

| Detail | Authority | Status | Notes |
|---|---|---|---|
| Dual world (canonical ↔ MC) | SimulationState + materializers | COMPLETE | Projection budgets enforced |
| LOD PHYSICAL/REGIONAL/ABSTRACT | planners + budgets | COMPLETE | ProjectionStressTest |
| City streets + sidewalks | SettlementPlanner + morphology | COMPLETE | Geography-derived layouts |
| Intercity terrain corridor | TerrainCorridorPlanner | COMPLETE | No straight-road fallback |
| Real doors + entrance access | FactionBlockPalette + EntranceAccessPlanner | COMPLETE | Stairs ±3 / switchback ±8 |
| Physical catch-up /setday | PhysicalDevelopmentReconciler | COMPLETE | Backlog roads/houses first; ≤2 sim-days/tick spread |
| Spawn capital | densifier + planner | COMPLETE | Same planner/economy as authored towns |
| Waystone 1/settlement + provenance | WaystoneSettlementRuntime | COMPLETE | LR-authored only |
| Locate city/mine/kingdom/market/port/wizardtrees/ruin | LocateQuery | COMPLETE | Port after discovery |
| NPC dialogue | NaturalLanguageDialogueEngine + token phrases | COMPLETE | Word-boundary parse; profession knowledge |
| UI blur removed | clearBackground overrides | COMPLETE | M / dialogue / dashboard |
| M-map terrain | RealmWorldMapScreen + ClientTerrainMapCache | COMPLETE | |
| Named roster | SocialPopulationEngine | COMPLETE | hamlet6…capital64; no projection required |
| Goods chains | ResourceType + SettlementEconomyEngine | COMPLETE | schema 18; no FOOD×mill fountain |
| Farms/pastures/hinterland keys | completion keys + receipts | COMPLETE | No seeder phantoms |
| Settlement transfer | SettlementTransfer | COMPLETE | Capture + rebellion |
| War capital targets | DiplomacyEngine | COMPLETE | Typed goals |
| Density | SettlementDensitySeeder | COMPLETE | 13/realm, 36 surface, spacing 2000 |

## Geschiedenis
- Older detail rows cited schema 16–17, 32/realm (~380+), FOOD mill proxies, COMPLETE (core), and EXTERNAL GATE. Historical only.
