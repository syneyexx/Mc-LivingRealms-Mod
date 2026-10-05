# LivingRealms Wave 0 — System Inventory

CURRENT PINS: schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

Maturity levels: FOUNDATION / CANONICAL / PLAYABLE / PHYSICALIZED / DEEP / POLISHED (see `COMPLETION_MATRIX.md`).

| Subsystem | Authority | Persistence | Runtime | UI | Tests | Maturity |
|---|---|---|---|---|---|---|
| Clock/history | SimulationState | schema 20 | n/a | History | soak/save | POLISHED |
| Factions | FactionEngine + SettlementTransfer | schema 20 | citizens/military | Overview | transfer/density | DEEP |
| Settlements | DensitySeeder + planner | ContentRevision 15 | construction | map/manage | density/names | DEEP |
| Society | SocialPopulationEngine roster | schema 20 | citizen entities | Society/dialogue | roster/dialogue | DEEP |
| Economy | SettlementEconomyEngine goods | schema 20 | markets | Economy | GoodsChain | DEEP |
| Diplomacy/war | DiplomacyEngine + goals | schema 20 | military | War/Politics | WarGoal | DEEP |
| Trade | TradeShipment | schema 20 | caravans | Ops | Trade* | PHYSICALIZED |
| Industry | IndustryEngine | schema 20 | Create yards | Ops | completeness | PHYSICALIZED |
| Ecology | EcologyEngine + 134 species | schema 8+ | wildlife | Ecology | SpeciesPack | DEEP |
| Naval/air | NavalEngine / AviationEngine | schema 20 | ships/aircraft | Forces | projection | PHYSICALIZED |
| Dashboard/map | protocol 20 / net 16 | n/a | F12/M/K | screens | codec | POLISHED |
| Runtime scheduler | RuntimeBudgetController | n/a | tick phases | n/a | RuntimeSchedulerTest | DEEP |
| Persistence codecs | SimulationStateCodec + domain codecs | schema 20 | save/load | n/a | DomainCodec*/Integrity* | DEEP |
| Content loaders | Realm/Culture/Building loaders | datapack JSON | seed/plan | n/a | RealmData*/Building* | CANONICAL |
| Modpack policy | ModCompatibilityPolicy | n/a | Create hard; guns player-only | n/a | audit | POLISHED |

## Geschiedenis
- Wave 0 inventory previously pinned schema 17 / protocol 17 / ContentRevision 11 and listed deficiencies under EXTERNAL GATE language.
- Wave 23/40 replaced blanket COMPLETE cells with maturity levels; ContentRevision pin corrected to 15.
