# LivingRealms Wave 0 — System Inventory

CURRENT PINS: schema 18 / minSchema 1 / protocol 19 / network 14 / contentRevision 14 / surfaceSettlements 156 / perRealm 13 / spacing 800

| Subsystem | Authority | Persistence | Runtime | UI | Tests | Status |
|---|---|---|---|---|---|---|
| Clock/history | SimulationState | schema 18 | n/a | History | soak/save | COMPLETE |
| Factions | FactionEngine + SettlementTransfer | schema 18 | citizens/military | Overview | transfer/density | COMPLETE |
| Settlements | DensitySeeder + planner | ContentRevision 14 | construction | map/manage | density/names | COMPLETE |
| Society | SocialPopulationEngine roster | schema 18 | citizen entities | Society/dialogue | roster/dialogue | COMPLETE |
| Economy | SettlementEconomyEngine goods | schema 18 | markets | Economy | GoodsChain | COMPLETE |
| Diplomacy/war | DiplomacyEngine + goals | schema 18 | military | War/Politics | WarGoal | COMPLETE |
| Trade | TradeShipment | schema 18 | caravans | Ops | Trade* | COMPLETE |
| Industry | IndustryEngine | schema 18 | Create yards | Ops | completeness | COMPLETE |
| Ecology | EcologyEngine + 134 species | schema 8+ | wildlife | Ecology | SpeciesPack | COMPLETE |
| Naval/air | NavalEngine / AviationEngine | schema 18 | ships/aircraft | Forces | projection | COMPLETE |
| Dashboard/map | protocol 18 / net 14 | n/a | F12/M/K | screens | codec | COMPLETE |
| Modpack policy | ModCompatibilityPolicy | n/a | Create hard; guns player-only | n/a | audit | COMPLETE |

## Geschiedenis
- Wave 0 inventory previously pinned schema 17 / protocol 17 / ContentRevision 11 and listed deficiencies under EXTERNAL GATE language.
