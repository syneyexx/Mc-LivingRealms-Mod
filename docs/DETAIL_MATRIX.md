# LivingRealms Detail Matrix

CURRENT PINS: schema 21 / minSchema 1 / protocol 20 / network 16 / contentRevision 18 / starterSettlements 204-300 / perRealm 17-25 / capitalSpacing 3000-4500 / roleAwareSpacing

Source wins over docs. Status values for current work use maturity levels:
FOUNDATION · CANONICAL · PLAYABLE · PHYSICALIZED · DEEP · POLISHED (see `COMPLETION_MATRIX.md`).

| Detail | Authority | Maturity | Notes |
|---|---|---|---|
| Dual world (canonical ↔ MC) | SimulationState + materializers | DEEP | Projection budgets enforced |
| LOD PHYSICAL/REGIONAL/ABSTRACT | planners + budgets | DEEP | ProjectionStressTest |
| City streets + sidewalks | SettlementPlanner + morphology | PHYSICALIZED | Geography-derived layouts |
| Intercity terrain corridor | TerrainCorridorPlanner | PHYSICALIZED | No straight-road fallback |
| Real doors + entrance access | FactionBlockPalette + EntranceAccessPlanner | PHYSICALIZED | Stairs ±3 / switchback ±8 |
| Physical catch-up /setday | PhysicalDevelopmentReconciler | PHYSICALIZED | Backlog roads/houses first; ≤2 sim-days/tick spread |
| Spawn capital | densifier + planner | DEEP | Same planner/economy as authored towns |
| Waystone 1/settlement + provenance | WaystoneSettlementRuntime | PHYSICALIZED | LR-authored only |
| Locate city/mine/kingdom/market/port/wizardtrees/ruin | LocateQuery | PLAYABLE | Port after discovery |
| NPC dialogue | interpreter/knowledge/planner/style/realizer | DEEP | Word-boundary parse; profession knowledge |
| UI blur removed | clearBackground overrides | POLISHED | M / dialogue / dashboard |
| M-map terrain | RealmWorldMapScreen + ClientTerrainMapCache | POLISHED | |
| Named roster | SocialPopulationEngine | DEEP | hamlet6…capital64; no projection required |
| Goods chains | ResourceType + SettlementEconomyEngine | DEEP | schema 21; no FOOD×mill fountain |
| Farms/pastures/hinterland keys | completion keys + receipts | CANONICAL | No seeder phantoms |
| Settlement transfer | SettlementTransfer | DEEP | Capture + rebellion |
| War capital targets | DiplomacyEngine | DEEP | Typed goals |
| Density | SettlementDensitySeeder + SettlementSpacingPolicy | POLISHED | 17–25/realm, 204–300 surface starters, role-aware spacing |
| Runtime scheduler | RuntimeBudgetController | DEEP | Soft/hard deferral + starvation |
| Domain persistence codecs | sim/persistence/codec/* | DEEP | Schema 21 envelope |

## Geschiedenis
- Older detail rows cited schema 16–17, 32/realm (~380+), FOOD mill proxies, COMPLETE (core), and EXTERNAL GATE. Historical only.
- Wave 23/40 replaced blanket COMPLETE status with maturity levels.
