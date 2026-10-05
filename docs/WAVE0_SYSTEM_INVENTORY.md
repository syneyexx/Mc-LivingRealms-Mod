# LivingRealms Wave 0 — System Inventory

CURRENT PINS: schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

| Subsystem | Authority | Persistence | Runtime | UI | Tests | Status |
|---|---|---|---|---|---|---|
| Clock/history | SimulationState | schema 20 | n/a | History | soak/save | COMPLETE |
| Factions | FactionEngine + SettlementTransfer | schema 20 | citizens/military | Overview | transfer/density | COMPLETE |
| Settlements | DensitySeeder + planner | ContentRevision 14 | construction | map/manage | density/names | COMPLETE |
| Society | SocialPopulationEngine roster | schema 20 | citizen entities | Society/dialogue | roster/dialogue | COMPLETE |
| Economy | SettlementEconomyEngine goods | schema 20 | markets | Economy | GoodsChain | COMPLETE |
| Diplomacy/war | DiplomacyEngine + goals | schema 20 | military | War/Politics | WarGoal | COMPLETE |
| Trade | TradeShipment | schema 20 | caravans | Ops | Trade* | COMPLETE |
| Industry | IndustryEngine | schema 20 | Create yards | Ops | completeness | COMPLETE |
| Ecology | EcologyEngine + 134 species | schema 8+ | wildlife | Ecology | SpeciesPack | COMPLETE |
| Naval/air | NavalEngine / AviationEngine | schema 20 | ships/aircraft | Forces | projection | COMPLETE |
| Dashboard/map | protocol 20 / net 16 | n/a | F12/M/K | screens | codec | COMPLETE |
| Modpack policy | ModCompatibilityPolicy | n/a | Create hard; guns player-only | n/a | audit | COMPLETE |

## Geschiedenis
- Wave 0 inventory previously pinned schema 17 / protocol 17 / ContentRevision 11 and listed deficiencies under EXTERNAL GATE language.
