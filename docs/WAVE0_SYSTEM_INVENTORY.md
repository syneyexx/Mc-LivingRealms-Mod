# LivingRealms Wave 0 — Factual System Inventory

**Audited baseline HEAD:** `c2b1f5d8a33598fa145590ed509ca97a9207bd26`  
**Working branch HEAD:** see `git rev-parse HEAD` on `cursor/livingrealms-final-product-0116`  
**Pins (source):** schema **17** · dashboard protocol **17** · network **14** · ContentRevision **11** · MC 1.21.1 / NeoForge 21.1.x / Create 6.0.10 / Java 21  
**Status rule:** nothing marked COMPLETE in this inventory (progress only).

| Subsystem | Authority | Persistence | Runtime projection | Assets | UI | Tests | Known deficiency |
|---|---|---|---|---|---|---|---|
| Simulation clock/history | `SimulationState` + `WorldHistory` | schema 17 binary | n/a | n/a | History tab | Save*/soak | Linked SavedData smoke external |
| Factions/kingdoms | `Faction` / DemoSeeder / `FactionHeraldry` | schema ≥1/4 | citizens+military | banners 0–15 + citizen skins | Overview/Realms | density/completeness | Heraldry application still uneven on all surfaces |
| Settlements/morphology | `SettlementPlanner` / `SettlementMorphology` / growth layers | construction keys + ContentRev 10 | buildings/roads | vanilla palettes | management/map | OrganicMorphology* | Architecture culture families still shallow |
| Society/citizens | `SocialCitizen` / `HouseholdState` / `SocialMobilityEngine` | schema 11/13/17 | `FactionCitizenEntity` | 48 skins | Society/dialogue | Society*/FinalProduct* | Class clothing layers partial |
| Government/dynasty | `GovernmentState` / `DynastyState` / debt+projects | schema 4/13/17 | court at keep | heraldry-dyed leather | Politics | PlayerRulership* | Court clothing still vanilla-item based |
| Diplomacy | `DiplomacyEngine` / treaties | schema 4 | strategic only | n/a | Politics | codec | Incidents/depth partial |
| War/siege | `WarState` / `SiegeState` / `CampaignPlan` / siege entities | schema 4/15/17 | military + siege equipment | troop + siege tex | War tab | soak/FinalProduct* | Formations/siege camps blockwork partial |
| Economy/markets | `MarketEngine` / settlement stores | schema 16/17 | market bridge | vanilla items | Economy | SettlementEconomy* | Goods depth limited |
| Trade/caravans | `TradeEngine` / `TradeShipment` logistics | schema ≥3/17 | caravan entity | caravan tex | Ops | Trade*/FinalProduct* | Physical escort NPCs optional |
| Transport | `TransportRoute` / street hierarchy | schema 4 + geography NBT | roads/bridges | vanilla | Ops | geography gates | Geometry QA automation incomplete |
| Industry/Create | `IndustryEngine` | schema 7 | Create yards | Create blocks | Ops | completeness | Kinetic unload unverified |
| Crime/law/bounty | Crime/Law/Bounty engines | schema 4/5/13 | hunters/custody | hunter tex | Law | law tests | Camp physicalization partial |
| Ecology/wildlife | `EcologyEngine` + 134 species | schema 8+ | animal entities | species textures | Ecology | SpeciesPack/soak | Animation profiles incomplete |
| Naval | `NavalEngine` | schema 6 | ships | class tex | Forces | port gates | Wake/VFX partial |
| Aviation | `AviationEngine` | schema 4 | aircraft | class tex | Forces | projection stress | Airfield polish partial |
| Player standing | `PlayerStanding` influence+careers | schema 6/17 | commands | n/a | dashboard | FinalProduct* | Influence unlock actions partial |
| Assistance/contracts | `AssistanceTask` / `ProductionContract` | schema 13 | assist runtime | n/a | Ops/dialogue | Assistance*/FinalProduct* | Notice-board physical contracts partial |
| Causes | `WorldCauseExplainer` | derived | dialogue/dashboard | n/a | protocol 17 | OrganicMorphology* | More domains still useful |
| Wizard Trees | seeder + planner | ContentRev | underground | palette | locate | WizardTreesTest | Flagship depth partial |
| Waystones/foreign | adoption/runtime | sidecar NBT | waystones | external mod | n/a | compat gates | Layout polish |
| Dashboard/map/catalog | snapshot protocol 17 / net 14 | n/a | screens | panel+icons | F12/M/K | codec bounds | Full art language still evolving |
| Onboarding | `PlayerOnboardingRuntime` | session | chat | lang strings | chat | manual | First-run only |

## Required new systems (directive §260) — progress

| System | Status now |
|---|---|
| Player Influence | IMPLEMENTED (authority + dashboard; unlock actions deepening) |
| Player Career Progression | IMPLEMENTED (tracks/ranks + service) |
| Social Mobility | IMPLEMENTED (`SocialMobilityEngine`) |
| Sovereign Debt | IMPLEMENTED |
| Grand Public Projects | IMPLEMENTED (sim+history; physical build stages deepening) |
| Hero / Historical Person | IMPLEMENTED (`HeroEngine` + legends/monuments) |
| Strategic Campaign AI | IMPLEMENTED (`CampaignPlan` + command rewrite) |
| Physical Siege Machinery | IMPLEMENTED (entities from SiegeState) |
| Production Contract System | IMPLEMENTED (expanded types + ProductionContract facade) |
| Full Caravan Experience | DEEPENED (logistics+escort+partial loss; escort NPCs optional) |
| Expanded Citizen Appearance | IMPLEMENTED (AppearanceProfile / 48 skins) |
| Species wildlife presentation | DEEPENED (species textures; animation QA remaining) |
| Faction Heraldry | IMPLEMENTED (deterministic banners + court dye) |

## Doc contradictions noted (still open for Wave 226–227)

- Production docs may still cite schema/protocol 16 in places.
- `RELEASE_MANIFEST.json` must be regenerated at final HEAD after linked build.
- Court/generic language in older matrices needs rebuild from reality.
