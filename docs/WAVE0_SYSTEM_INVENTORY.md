# LivingRealms Wave 0 — Factual System Inventory

**Resolved HEAD:** `c2b1f5d8a33598fa145590ed509ca97a9207bd26`  
**Pins (source):** schema **16** · dashboard protocol **16** · network **14** · ContentRevision **10** · MC 1.21.1 / NeoForge 21.1.219 / Create 6.0.10 / Java 21  
**Status rule:** nothing marked COMPLETE in this inventory.

| Subsystem | Authority | Persistence | Runtime projection | Assets | UI | Tests | Known deficiency |
|---|---|---|---|---|---|---|---|
| Simulation clock/history | `SimulationState` + `WorldHistory` | schema 16 binary | n/a | n/a | History tab | Save*/soak | Linked SavedData smoke external |
| Factions/kingdoms | `Faction` / DemoSeeder | schema ≥1/4 | citizens+military | 12 citizen tints | Overview/Realms | density/completeness | Heraldry missing; art generic |
| Settlements/morphology | `SettlementPlanner` / `SettlementMorphology` | construction keys + ContentRev 10 | buildings/roads | vanilla palettes | management/map | OrganicMorphology* | Historical growth layers thin |
| Society/citizens | `SocialCitizen` / `HouseholdState` | schema 11/13 | `FactionCitizenEntity` | 12 skins | Society/dialogue | Society* | Appearance hard-capped at 12 |
| Government/dynasty | `GovernmentState` / `DynastyState` | schema 4/13 | court at keep | gold armor + skins | Politics | PlayerRulership* | Court art placeholder |
| Diplomacy | `DiplomacyEngine` / treaties | schema 4 | strategic only | n/a | Politics | codec | Incidents/depth partial |
| War/siege | `WarState` / `SiegeState` / `MilitaryCommandEngine` | schema 4/15 | military units | generic troop tex | War tab | soak/codec | Campaign AI greedy; no siege entities |
| Economy/markets | `MarketEngine` / settlement stores | schema 16 | market bridge | vanilla items | Economy | SettlementEconomy* | Goods depth limited |
| Trade/caravans | `TradeEngine` / `TradeShipment` | schema ≥3 | caravan entity | 1 generic tex | Ops | Trade* | Shipment metadata thin; escort thin |
| Transport | `TransportRoute` / network engine | schema 4 + geography NBT | roads/bridges | vanilla | Ops | geography gates | Street hierarchy incomplete |
| Industry/Create | `IndustryEngine` | schema 7 | Create yards | Create blocks | Ops | completeness | Kinetic unload unverified |
| Crime/law/bounty | Crime/Law/Bounty engines | schema 4/5/13 | hunters/custody | generic hunter | Law | law tests | Hunter art generic |
| Ecology/wildlife | `EcologyEngine` + 134 species | schema 8+ | animal entities | 10 morphology tex | Ecology | SpeciesPack/soak | Species art incomplete |
| Naval | `NavalEngine` | schema 6 | ships | 1 ship tex | Forces | port gates | Class visuals weak |
| Aviation | `AviationEngine` | schema 4 | aircraft | 1 aircraft tex | Forces | projection stress | Generic aircraft |
| Player standing | `PlayerStanding` / ReputationEngine | schema 6 | commands | n/a | dashboard | auth tests | Influence missing; careers thin |
| Assistance | `AssistanceTask` | schema 13 | assist runtime | n/a | Ops | Assistance* | Not full contract layer |
| Causes | `WorldCauseExplainer` | derived | dialogue/dashboard | n/a | protocol 16 | OrganicMorphology* | Needs more domains |
| Wizard Trees | seeder + planner | ContentRev | underground | palette | locate | WizardTreesTest | Flagship depth partial |
| Waystones/foreign | adoption/runtime | sidecar NBT | waystones | external mod | n/a | compat gates | Layout polish |
| Dashboard/map/catalog | snapshot protocol 16 / net 14 | n/a | screens | no GUI tex | F12/M/K | codec bounds | Programmer UI |

## Required new systems (directive §260) — baseline

| System | Status at Wave 0 |
|---|---|
| Player Influence | MISSING |
| Player Career Progression | PARTIAL (FactionRank only) |
| Social Mobility | MISSING |
| Sovereign Debt | MISSING |
| Grand Public Projects | MISSING |
| Hero / Historical Person | PARTIAL (`LegendRecord` only) |
| Strategic Campaign AI | MISSING (greedy target select) |
| Physical Siege Machinery | PARTIAL (sim equipment only) |
| Production Contract System | PARTIAL (`AssistanceTask` foundation) |
| Full Caravan Experience | PARTIAL (generic projection) |
| Expanded Citizen Appearance | MISSING (12-skin hard limit) |
| Species wildlife presentation | PARTIAL (10 family textures) |
| Faction Heraldry | MISSING |

## Doc contradictions noted

- `COMPLETION_MATRIX.md` still cites ContentRevision 9 / protocol 14 in header while source is 10 / 16.
- `RELEASE_MANIFEST.json` stale SHA / protocol pins.
- Older docs claim orthogonal streets; morphology now geography-derived.
- Court presentation marked missing in some matrices despite keep/court projection.
