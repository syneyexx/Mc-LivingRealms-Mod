# Architecture inventory — modular-monolith refactor targets

Inventory of five high-mass classes at `HEAD` `2180af61be07146ba2fca1894d8489eaabe03e92` (`cursor/architecture-depth-pass-f4a7`).  
Purpose: concrete extraction inputs for a modular monolith. Only systems present in source are named.

CURRENT PINS (from `docs/ARCHITECTURE.md`): schema 20 / minSchema 1 / protocol 20 / network 16.

---

## Dependency map (these five)

```
LivingRealmsEvents (NeoForge bridge)
  │  every Minecraft day / mid-day pulse / death / commands
  ▼
SimulationState (canonical bag + day orchestrator)
  │  advanceDays → civilizationEngine.simulateDay(...)
  │  encode/decode via LivingRealmsSavedData
  ▼
CivilizationEngine
  │  owns CivilizationLifecycleEngine
  ▼
CivilizationLifecycleEngine
  └── mutates SimulationState collections / Settlement / Faction / SocialCitizen / history

LivingRealmsSavedData
  └── SimulationStateCodec.encode/decode(SimulationState)

DashboardClientState (client)
  └── opens/refreshes RealmDashboardScreen from RealmDashboardSnapshot
       (actions → DashboardActionPayload → server DashboardActionService; not these five)
```

| From → To | Coupling |
|---|---|
| `CivilizationLifecycleEngine` → `SimulationState` | Heavy write/read of civilization/social/law/naval collections; no ownership of engines |
| `SimulationState` → `CivilizationLifecycleEngine` | Indirect: embeds `CivilizationEngine`, which embeds the lifecycle engine |
| `SimulationStateCodec` → `SimulationState` | Full binary mirror of persisted fields; rebuilds a fresh state + engines on decode |
| `LivingRealmsEvents` → `SimulationState` | Via `SimulationRuntime.data(...).state()`: tick advance, physical-loss feedback, commands |
| `LivingRealmsEvents` → `SimulationStateCodec` | Indirect via `LivingRealmsSavedData` dirty/save path (not imported in Events) |
| `RealmDashboardScreen` → `SimulationState` | **None.** Reads only `RealmDashboardSnapshot` |
| `RealmDashboardScreen` ↔ other four | **None.** Client presentation + action emission only |

---

## 1. `CivilizationLifecycleEngine`

**Path:** `src/main/java/dev/livingrealms/sim/civilization/CivilizationLifecycleEngine.java` (~766 lines / ~100KB dense)  
**Type:** `public final class` — **stateless** (no instance fields beyond `MAX_NAMED_CITIZENS` constant). All work is `static` helpers + two public entry points.

### Responsibilities (domains currently owned)

- **Day orchestration** — `simulateDay` (L25–41): cadence-gated lifecycle pass.
- **Culture / dynasty bootstrap** — `ensureCulturalPoliciesAndDynasties`, `ensureRulerCitizen`, `refreshDynasties`, `evolveCultureAndLaw`.
- **Household graph** — `ensureHouseholds` (create/merge/deactivate/repair), `simulateHouseholdLifecycle`, birth/adoption/`FamilyMaterializer`, wages (`payWages`).
- **Epidemics** — spawn/update/containment, named victims, trade-route spread (`simulateEpidemics`, `spreadDiseaseAlongTrade`).
- **Migration columns & refugee camps** — `considerMigration`, `advanceMigrationGroups`, `establishRefugeeCamp` (creates `Settlement`s + pending construction pressure).
- **Knowledge diffusion** — settlement/route knowledge, tech spillover, apprenticeships.
- **Civic life** — festivals/rituals (`simulateCivicEvents`), holy-order pressure (`applyHolyOrderPresence`).
- **Espionage & propaganda** — operations and campaigns with diplomatic/crime side effects.
- **Justice loop** — NPC crime spawn, case investigation/sentencing/exile/execution (`spawnNpcCrime`, `advanceJusticeCases`).
- **Hidden wealth caches** — bury/recover/raid via intelligence.
- **Political marriages** — court spouse relocation + diplomatic opinion.
- **Piracy** — bands, hideouts, shipment interception, fleet engagements.
- **Ruins** — abandonment/loot/reclaim/weathering.
- **Assistance task pressure board** — opens/updates `AssistanceTask`s from settlement stress.

### State owned

- **None** (engine is pure). Constant: `MAX_NAMED_CITIZENS = 8_000`.
- Mutates **caller-owned** `SimulationState` and nested mutable domain objects.

### Dependencies (classes/engines called)

| Dependency | Usage |
|---|---|
| `SimulationState` | Primary read/write surface |
| `SocialPopulationEngine` | `allocateVirtualProjectionSlot`, `rebindAfterMigration` |
| `FamilyMaterializer` | Adult child materialization |
| `HouseholdHomeBinder` | Home key assignment |
| `AssistanceContributionEngine` | Infrastructure pressure |
| `SettlementPlanner` | Pending shelter check for refugee camps |
| `FaithCatalog` / `DiseaseProfile` / `GuildRank` / `CivilizationCalendar` | Catalogs/profiles |
| `AppearanceProfile` | Ruler personification |
| `WizardTreesSeeder.FACTION_NAME` | Arcana knowledge boost |
| Domain types | `Faction`, `Settlement`, `SocialCitizen`, `HouseholdState`, `MigrationGroup`, `EpidemicRecord`, `JusticeCase`, `CrimeLedger`/`reportCrime`, `PirateBand`/`PirateHideout`, `Fleet`, `TradeShipment`, `WorldEvent`, etc. |

Does **not** call Minecraft APIs.

### Mutations performed

Representative writes (not exhaustive):

- **Collections:** `addHousehold`, `addSocialCitizen`, `addMigrationGroup`, `addEpidemic`, `addAssistanceTask`, `addCivicEvent`, `addIntelligenceOperation`, `addPropagandaCampaign`, `addRuinSite`, `addJusticeCase`, `addHiddenCache`, `addPirateBand`/`addPirateHideout`, `addDiplomaticMarriage`, `addCustody`, `ensureDynasty` / dynasty fields.
- **Settlement/Faction:** population, housing, stockpiles, prosperity/unrest/order/employment/foodSecurity, development priority, rename camps→havens, treasury, technology, diplomatic relations, government legitimacy/stability.
- **Civilization overlays:** `SettlementCivilizationState` / `FactionCivilizationState` pressures, knowledge, quarantine, law policy, culture traits, spy networks.
- **Citizens/households:** money, needs, health, death, memories, relationships, migrate, profession skill, household membership/wealth/children.
- **Law:** `state.reportCrime(...)`, custody, executions via `recordPhysicalCitizenDeath`.
- **Trade/naval:** `recordPhysicalShipmentLoss` (piracy), fleet losses/experience.
- **History:** many `WorldEvent` types (`migration_*`, `epidemic_*`, `court_*`, `piracy`, …).

### Callers (ripgrep)

| Caller | How |
|---|---|
| `CivilizationEngine` (L16, L24–26) | Field `lifecycleEngine`; `considerMigration` weekly; `simulateDay` every day |
| `CivicChoreographyPlanner` | Comment-only alignment with day-clock semantics |
| Tests | Indirect via `CivilizationEngine` / soak / civilization layer tests |

`SimulationState.advanceDays` → `civilizationEngine.simulateDay` → this class.

### Persistence coupling

- **None directly.** All mutated records are schema-backed via `SimulationStateCodec` (esp. v12–v14 humanity/civilization blocks).
- Refugee camps create real `Settlement`s that enter the faction graph and thus the save.

### Minecraft coupling

- **None** (pure `sim.*`).

### Client coupling

- **None.** Dashboard may *display* consequences (assistance, epidemics, etc.) via snapshot builders elsewhere.

### Circular / entanglement notes

- **God-day pipeline:** one class owns disease, migration, justice, espionage, propaganda, piracy, dynasties, wages, knowledge — domains already partly covered by `CrimeEngine`, `NavalEngine`, `SocialPopulationEngine`, `CivilizationEngine.updateSettlements`.
- **Overlaps with `CivilizationEngine`:** claims/bandits/demography vs lifecycle epidemics/migration/households; weekly migration is split (`considerMigration` here, attraction elsewhere).
- **Overlaps with law engines:** `spawnNpcCrime` + `advanceJusticeCases` vs `CrimeEngine` / `LawEnforcementEngine` used from `SimulationState.reportCrime` / day tick.
- **Construction entanglement:** `establishRefugeeCamp` knows `SettlementPlanner` / `StructureRole` to force physical pending builds.
- Class comment (L17–20) claims no duplicate population/economy authority, yet it adjusts population, stockpiles, food security, wages, and tech.

### Suggested extraction targets (justified by existing method clusters)

| Module | Existing methods |
|---|---|
| `HouseholdLifecycleEngine` | `ensureHouseholds`, `simulateHouseholdLifecycle`, `materializeAdultChild`, `adoptOrphans`, `payWages` |
| `EpidemicEngine` | `simulateEpidemics`, `markNamedEpidemicVictims`, `spreadDiseaseAlongTrade` |
| `MigrationEngine` | `considerMigration`, `advanceMigrationGroups`, `establishRefugeeCamp`, attach/move households |
| `KnowledgeEngine` | `diffuseKnowledge`, `applyKnowledgeBenefits`, `trainApprentices` |
| `CivicEventEngine` | `simulateCivicEvents`, `applyHolyOrderPresence` |
| `IntelligencePropagandaEngine` | `simulateIntelligenceOperations`, `simulatePropagandaCampaigns` |
| `CourtJusticeEngine` | `spawnNpcCrime`, `advanceJusticeCases`, sentence/exile helpers |
| `PiracyEngine` | `simulatePiracy`, fleet engagement helpers |
| `DynastySuccessionEngine` | `ensureCulturalPoliciesAndDynasties`, `refreshDynasties`, `simulatePoliticalMarriages`, `evolveCultureAndLaw` |
| `AssistanceBoardEngine` | `simulateAssistanceTasks` |
| `RuinEngine` | `simulateRuins` |
| `HiddenCacheEngine` | `simulateHiddenCaches` |

Keep a thin `CivilizationLifecycleEngine.simulateDay` as a cadence façade, or fold cadence into `CivilizationEngine`.

---

## 2. `SimulationStateCodec`

**Path:** `src/main/java/dev/livingrealms/sim/persistence/SimulationStateCodec.java` (~743 lines / ~91KB)  
**Type:** `public final` utility; private ctor; all static.

### Responsibilities

- **Versioned binary persistence** for the entire canonical world (`MAGIC=0x4C52534D` “LRSM”).
- **Schema gate:** `MIN_SUPPORTED_SCHEMA=1`, `SCHEMA_VERSION=20`, size caps `MAX_STATE_BYTES` / `MAX_STRING_BYTES`.
- **Integrity:** `integrityToken` = CRC32+1 (0 reserved for legacy SavedData).
- **Encode path:** validate → write header (seed, ticks, nextId) → layered writers → history.
- **Decode path:** header → version-conditional readers → migrate helpers for pre-v16/v17/v18/v19 → `repairNextIdWatermark` → validate if current schema.
- **Layered schema writers/readers:** regions, factions/gov/armies/relations, shipments, v4 strategic, v5 custody, v6 naval/players, v7 industry, v9 config, v11 social, v12 civilization, v13 humanity, v14 pirate hideouts, v15 siege equipment, v16 settlement economy, v17 final product, v18 goods/origins, v19 provenance/sites/underworld profiles/player structures, v20 underworld contracts + stolen goods.

### State owned

- **None** (stateless codec). Schema constants only.

### Dependencies

- Nearly every `sim.*` domain type that is persisted.
- `SimulationValidator` on encode and on current-schema decode.
- `SpeciesCatalog` / caller-supplied species map on decode (live species must exist in catalog).
- **No** Minecraft types (comment L37: “No Minecraft classes required”).

### Mutations performed

- **Encode:** read-only of `SimulationState` (after validate).
- **Decode:** constructs new `SimulationState`, `restore*` / `add*` for all collections, migrates stockpiles/appearance/provenance when upgrading old schemas.

### Callers (ripgrep)

| Caller | Role |
|---|---|
| `LivingRealmsSavedData` | Production load/save + integrity check |
| Many `src/testCore` tests | Round-trip, migration matrix, fuzz, integrity |
| `LivingRealmsReleaseGameTests` | GameTest encode/decode |
| `scripts/release-audit.py`, `write-release-manifest.py` | Pin `SCHEMA_VERSION` |
| `DocumentationPinTest` | Const pin |
| `AuthoredBlockLedger` | Documents that block ledger is **outside** this payload |

### Persistence coupling

- **This class is the persistence boundary** for canonical sim state.
- Schema bumps are coordinated with `LivingRealmsSavedData` outer NBT keys and release pins.
- Explicit non-coverage: authored block ledger, ephemeral queues (`playerStructureRevalidation*`, `presentationScope`, engine instances).

### Minecraft coupling

- **None in source.** Minecraft wraps bytes in NBT via `LivingRealmsSavedData`.

### Client coupling

- **None.**

### Circular / entanglement notes

- **Monolithic schema surface:** one class knows field layouts for every domain → any module split must either (a) keep a single codec with section delegates, or (b) introduce per-module codecs composed under one envelope.
- Encode order is fixed; decode branches on version — migration logic is co-located with I/O (hard to move without dual-maintenance).
- `ResourceType.values()` ordinals are baked into multiple sections → enum evolution is a schema event.

### Suggested extraction targets

| Module | Existing methods |
|---|---|
| `CodecHeader` / integrity helpers | `inspectSchema`, `integrityToken`, string/position/count helpers |
| `EcologyCodec` | `writeRegions` / `readRegions` |
| `FactionSettlementCodec` | `writeFactions`, `writeGovernment`, settlement economy/origins/provenance slices |
| `StrategicCodec` | `writeV4Strategic`, treaties/wars/routes/sieges/air/bounties/crime ledger |
| `SocialCodec` | `writeV11Social` + citizen extensions in v13/v17 |
| `CivilizationHumanityCodec` | `writeV12Civilization`, `writeV13Humanity`, v14 hideouts |
| `WorldSitesCodec` | outlying/journey/roadside/player structures (v19) |
| `UnderworldCodec` | profiles (v19) + contracts/stolen goods (v20) |
| `SaveMigration` | `migratePreV16*`, `migratePreV17*`, `migratePreV18*`, `migratePreV19*` |

Keep a single public `encode`/`decode` façade so `LivingRealmsSavedData` and tests stay stable.

---

## 3. `RealmDashboardScreen`

**Path:** `src/main/java/dev/livingrealms/minecraft/client/ui/RealmDashboardScreen.java` (~975 lines / ~61KB)  
**Type:** `public final class extends Screen` (Minecraft client GUI).

### Responsibilities

- **Tabbed strategic UI** over an immutable `RealmDashboardSnapshot` (class comment L18).
- **Widget rebuild** per tab: law, diplomacy, war room, army orders, faction join/leave/abdicate/found, influence petitions, economy tax/market, settlement development priorities/modes/register house, settings presets, map open.
- **Read-only line renderers** for overview/kingdoms/politics/settlements/society/wars/economy/ops/forces/ecology/history.
- **Strategic map paint** (`renderStrategicMap`, claims/armies/shipments).
- **Accessibility scaling** via `DashboardAccessibility`.
- **Action emission only** — never mutates sim state locally; uses `DashboardClientState.sendAction` / `requestRefresh` / `requestOpenMap`.

### State owned (UI-only)

| Field | Role |
|---|---|
| `snapshot` | Current `RealmDashboardSnapshot` |
| `tab` | Active `Tab` enum |
| `page` | Pagination (bounties / settlements / market / text) |
| `contentScroll` | Mouse-wheel scroll in content pane |
| `foundNameBox` / `foundNameDraft` | Found-settlement name editor |

Constants: `BOUNTIES_PER_PAGE = 4`. Nested: `Line` record, `Tab` enum (14 tabs).

### Dependencies

- Minecraft client: `Screen`, `GuiGraphics`, `Button`, `EditBox`, `Tooltip`, `Component`.
- `RealmDashboardSnapshot`, `DashboardActionCommand`, `MarketTransactionEngine.PACKAGE_UNITS` (label sizing only), `ImportantNotifications`, `DashboardAccessibility`, `DashboardClientState`.

### Mutations performed

- **Local UI state only** (tab/page/scroll/draft/snapshot replace).
- **Canonical state:** none on client. Intended mutations go as `DashboardActionCommand` packets (server applies via action service — outside this class).

Actions emitted (by tab builders):  
`SURRENDER`, `PAY_FINE`, `BOUNTY_ACCEPT`/`ABANDON`, `PETITION_PEACE`, `PROPOSE_TRADE_PACT`, army defend/rally/stand-down/capture/siege/raid/escort/patrol, `DECLARE_WAR`, `ABDICATE`, `FACTION_LEAVE`, `FACTION_JOIN_LOCAL`, `FOUND_SETTLEMENT`, influence (`REQUEST_AUDIENCE`, `PROPOSE_PROJECT`, …), `TAX_LOWER`/`RAISE`, `MARKET_BUY`/`SELL`, settlement priority actions, `SET_DEVELOPMENT_MODE`, `REGISTER_BUILDING`, config presets.

### Callers (ripgrep)

| Caller | How |
|---|---|
| `DashboardClientState.receive` | `new RealmDashboardScreen(decoded)` or `replaceSnapshot` if already open |
| `scripts/release-audit.py` | Static content pins |

### Persistence coupling

- **None.** Snapshot is network JSON (`RealmDashboardCodec`), not `SimulationStateCodec`.

### Minecraft coupling

- **Full client GUI** (NeoForge/Minecraft Screen stack). Must stay in `minecraft.client` module.

### Client coupling

- **This is the client dashboard.** Paired with `RealmWorldMapScreen` via shared `DashboardClientState`.

### Circular / entanglement notes

- **UI + action wiring + rendering in one file:** tab button factories, line composition, and map drawing are co-located (~975 lines).
- Soft coupling to server action vocabulary (`DashboardActionCommand.Action`) — screen knows command shapes (e.g. market resource ordinal encoding).
- Does not touch the other four inventory classes directly.

### Suggested extraction targets

| Module | Existing methods |
|---|---|
| `DashboardTabBar` / layout helpers | `rebuildDashboardWidgets`, `tabColumns`/`tabRows`/`contentTop` |
| `DashboardActionPanels` | `rebuildLawButtons`, `rebuildDiplomacyButtons`, `rebuildWarRoomButtons`, `rebuildArmyOrderButtons`, `rebuildFactionButton`, `rebuildInfluenceButtons`, `rebuildEconomyButtons`, `rebuildSettlementButtons`, `rebuildSettingsButtons` |
| `DashboardTabContent` | `*Lines()` methods |
| `StrategicMapRenderer` | `renderStrategicMap`, `drawWorldLine`/`drawClaim`/`factionColor` |
| Keep thin `RealmDashboardScreen` | lifecycle: `init`, `render`, input, `replaceSnapshot` |

---

## 4. `LivingRealmsEvents`

**Path:** `src/main/java/dev/livingrealms/minecraft/LivingRealmsEvents.java` (~678 lines / ~56KB)  
**Type:** `public final` NeoForge event subscriber (registered from `LivingRealms`).

### Responsibilities

- **Server tick orchestration** (`onServerTick`): species catalog hot-reload; discovery runtimes; staggered materializers (wildlife, citizens, caravans, military, mobile civ, air/naval, bounty hunters, siege, roadside, festivals, geography, player structures, seasonal farms); regional impostors / far presence; day-advance scheduler drain; construction materializer phases; **once per Minecraft day** `state.advanceDays(strategicDaysPerStep)`; mid-day `advancePresentationPulse(0.5)`.
- **Projection index join/leave** for all Living Realms entity kinds.
- **Physical → canonical loss feedback** on leave/death (animals, citizens, journeys, armies, migrations/pirates, aircraft, ships, caravans).
- **Player interaction bridges:** dialogue open, hidden caches, container theft prep, industrial sabotage crime on block break, structure revalidation dirtying.
- **Logout / server stop cleanup** of indexes, limiters, materializer caches, day scheduler.
- **Species reload listener** registration.
- **Combat/law reactions** on `LivingDeathEvent` (bounty claims, murder crime, combat service awards).
- **Command surface** `/livingrealms …` (wanted, faction, industry, bounty, assist, found, locate, status, setday, advance, advance_day).

### State owned

| Field | Role |
|---|---|
| `tickCounter` | Schedules phased work; reset on server stop |
| `appliedSpeciesRevision` | Last applied `SpeciesDataRegistry` revision |

No canonical world state stored here.

### Dependencies (representative)

- **Runtime glue:** `SimulationRuntime`, `LivingRealmsSavedData`, `SpeciesDataRegistry`.
- **Many materializers/indexes/runtimes:** wildlife, citizens, caravans, military, mobile civilization, aircraft, ships, bounty hunters, siege, regional impostors, construction/urban/industry/roadside/festival, ambience, choreography, crime/custody/theft, assistance, hidden cache, pirate hideout, historical sites, player structures, onboarding, waystones, foreign discovery, ecosystem discovery, spawn kingdom.
- **Sim APIs:** `SimulationState.advanceDays` / `advancePresentationPulse` / `recordPhysical*` / crime & bounty / join-leave faction / `PlayerSettlementFounder` / `LocateQuery` / `ManualDayAdvanceScheduler` / `TerritoryEngine` / `IndustrySitePlanner`.
- **NeoForge/Minecraft events & commands.**

### Mutations performed

- Marks `LivingRealmsSavedData` dirty after sim writes.
- Mutates canonical state through `SimulationState` methods listed above (day advance, physical losses, crimes, bounties, service, settlement founding via commands).
- Clears ephemeral projection indexes on stop.
- Does **not** call `SimulationStateCodec` directly.

### Callers (ripgrep)

| Caller | How |
|---|---|
| `LivingRealms` | `NeoForge.EVENT_BUS.register(new LivingRealmsEvents())` |
| NeoForge event bus | All `@SubscribeEvent` methods |
| `scripts/release-audit.py`, `SingleProductionAuthorityTest` | Static guards on source text |

### Persistence coupling

- Indirect: `data.setDirty()` after mutations; save uses codec in `LivingRealmsSavedData`.
- Day advance and physical losses are the main dirty sources from this class.

### Minecraft coupling

- **Primary Minecraft↔sim bridge.** Entire class is server-side NeoForge integration.

### Client coupling

- **None directly** (server events). Clears dashboard/dialogue **limiters** on logout (server-side rate limit state), not client screens.

### Circular / entanglement notes

- **God-event hub:** tick schedule + entity lifecycle + crime + commands + discovery in one type.
- Death handling duplicated between `onEntityLeave` and `onLivingDeath` for several projection kinds (must stay consistent).
- Tick phasing encodes performance policy that could live in a dedicated scheduler module.
- Commands duplicate some dashboard actions (found, bounty, faction join/leave, assist).

### Suggested extraction targets

| Module | Existing methods / blocks |
|---|---|
| `SimulationTickScheduler` | `onServerTick` body (phases, day advance, presentation pulse) |
| `ProjectionIndexLifecycle` | `onEntityJoin` / `onEntityLeave` |
| `PhysicalLossBridge` | leave + death canonical feedback + `awardCombatService` |
| `PlayerWorldInteractionBridge` | interact / container / block break-place |
| `LivingRealmsCommands` | `onCommands` + locate helpers |
| Keep thin `LivingRealmsEvents` | `@SubscribeEvent` forwarding only |

---

## 5. `SimulationState`

**Path:** `src/main/java/dev/livingrealms/sim/world/SimulationState.java` (~509 lines / ~55KB)  
**Type:** `public final` — **canonical authoritative state** (class comment L34).

### Responsibilities

- **Own all persisted (and some ephemeral) world collections** plus ID watermark.
- **Embed and sequence day engines** in `advanceDays` (L470–506).
- **Lookup / ensure / add / soft-cap** APIs for every major collection.
- **Physical-loss adapters** (`recordPhysical*`) for Minecraft projection feedback.
- **Law façade:** `reportCrime`, arrest/bounty/custody helpers delegating to crime/law/bounty/reputation engines + underworld observe.
- **Player standing / underworld / dynasties** accessors.
- **Species catalog replace** with live-population safety + habitat reseeding.
- **Ecosystem region discovery** (`ensureEcosystemRegion`).
- **Manual day jump** (`advanceToDay`) and **presentation pulse** (shipments/migrations/siege nudge without full engines).
- **Summary string** for logging/commands.

### State owned

**Identity / clock / config**

- `seed`, `SimClock clock`, `SimulationConfig config`, `Map<String,SpeciesDefinition> species`, `Map<String,EcoBiome> biomes`, `nextId`

**Canonical collections (persisted via codec)**

- Ecology: `regions`
- Politics/economy: `factions`, `shipments`, `treaties`, `wars`, `routes`, `objectives`, `sieges`, `airWings`, `ports`, `fleets`, `industrialSites`
- Law: `bounties`, `custody`, `crimeLedger`
- Social/civ: `socialCitizens`, `settlementCivilizations`, `factionCivilizations`, `households`, `resourceClaims`, `raids`, `legends`, `epidemics`, `migrationGroups`, `justiceCases`, `hiddenCaches`, `pirateBands`, `pirateHideouts`, `diplomaticMarriages`, `civicEvents`, `intelligenceOperations`, `propagandaCampaigns`, `ruinSites`, `assistanceTasks`, `dynasties`
- Sites/mobility: `outlyingSites`, `citizenJourneys`, `roadsideSites`, `registeredPlayerStructures`
- Government extras: `debts`, `grandProjects`, `campaignPlans`
- Players/underworld: `playerStandings`, `underworldProfiles`, `underworldContracts`, `stolenGoodsLedger`
- Meta: `history`, `liveness`

**Ephemeral (not in codec)**

- `playerStructureRevalidationQueue` / `playerStructureRevalidationSet`
- `SettlementPresentationScope presentationScope`
- `pendingConstructionCatchupDays`
- Embedded **engine instances** (recreated on `new SimulationState` / decode)

**Embedded engines (fields L95–118)**

`EcologyEngine`, `FactionEngine`, `TradeEngine`, `SocietyEngine`, `GovernmentEngine`, `SovereignDebtEngine`, `GrandProjectEngine`, `HeroEngine`, `RebellionEngine`, `DiplomacyEngine`, `TransportNetworkEngine`, `MilitaryCommandEngine`, `AviationEngine`, `CrimeEngine`, `BountyOfficeEngine`, `LawEnforcementEngine`, `NavalEngine`, `ReputationEngine`, `IndustryEngine`, `PrimaryEconomyEngine`, `SettlementEconomyEngine`, `SocialPopulationEngine`, `SocialMobilityEngine`, `CivilizationEngine` (→ lifecycle).  
Also calls static `RoadLifeEngine`, `SettlementExpansionEngine`, `UnderworldActions`, `HabitatPopulationSeeder` from methods.

### Dependencies

- Broad `sim.*` packages (see imports L3–31).
- Orchestrates engines listed above inside `advanceDays`.
- **No** Minecraft imports.

### Mutations performed

- All collection mutations via `add*` / `ensure*` / `restore*` / remove helpers.
- `advanceDays`: runs full engine pipeline then `clock.advance(TICKS_PER_DAY)`; monthly snapshot history.
- `advancePresentationPulse`: trade presentation + migration crawl + siege army nudge.
- Physical loss methods mutate groups/citizens/armies/fleets/etc. + history.
- Law/reputation/underworld side effects through façades.

### Callers (ripgrep)

Extremely wide. Production hotspots:

- `LivingRealmsEvents` / `LivingRealmsSavedData` / materializers / runtimes under `minecraft.*`
- All day engines under `sim.*`
- `SimulationStateCodec`
- `RealmDashboardBuilder`, `DashboardActionService`, `PlayerAgencyActions`, dialogue, validators, seeders
- Most `src/testCore` tests

Among the five inventory classes: used by **LifecycleEngine**, **Codec**, **Events**; **not** by `RealmDashboardScreen`.

### Persistence coupling

- **Primary codec subject.** Soft-cap constants (`MAX_*`) must stay aligned with codec `checkedCount` limits.
- Engines and ephemeral queues are intentionally non-persisted.

### Minecraft coupling

- **None in type.** Designed so Minecraft only projects/feeds back via adapters.

### Client coupling

- **None.** Client sees snapshots built elsewhere from this state.

### Circular / entanglement notes

- **God-object bag + orchestrator + law façade:** storage and day scheduling and multi-engine wiring live together.
- Soft-cap pruning logic duplicated per collection type inside this class.
- `CivilizationEngine` (and thus lifecycle) is a field here while lifecycle mutates sibling collections — fine compositionally, but hard module boundary (state module vs civilization module).
- Presentation pulse embeds trade/migration/siege knowledge that could live in those engines (trade already has `presentationPulse`).

### Suggested extraction targets

| Module | Justification in existing code |
|---|---|
| `SimulationCatalog` / stores | Collection fields + `add*`/`find*`/`ensure*`/`MAX_*` |
| `SimulationDayLoop` | `advanceDays`, `advanceToDay`, `advancePresentationPulse` |
| `PhysicalLossService` | `recordPhysical*` methods |
| `LawFacade` (or keep on law module) | `reportCrime`, bounty/arrest/custody/join/leave |
| Keep `SimulationState` as façade | Thin delegates so codec/call sites stay stable during split |

Do **not** invent new engines not already present; extractions should move existing private/public method clusters.

---

## Cross-cutting refactor notes (from these five only)

1. **Authority line is already documented and mostly honored:** `SimulationState` is canonical; Events/materializers project; Codec persists; Dashboard is snapshot-only.
2. **Largest modularity wins with least invention:**
   - Split `CivilizationLifecycleEngine` by method clusters (table in §1).
   - Split `LivingRealmsEvents` tick vs commands vs physical-loss (§4).
   - Split `RealmDashboardScreen` panels vs map vs lines (§3).
   - Treat `SimulationStateCodec` as composed section codecs under one schema envelope (§2).
   - Peel `SimulationState` day-loop and physical-loss from the collection bag last (§5), after callers can depend on smaller façades.
3. **Do not couple client UI to codec or lifecycle** — that boundary is already clean; preserve it.
4. **Schema 20 is the persistence contract** — any store split must preserve encode order / migration behavior or bump schema deliberately.

---

## File sizes at inventory HEAD

| Class | Lines (`wc -l`) |
|---|---|
| `CivilizationLifecycleEngine` | 766 |
| `SimulationStateCodec` | 743 |
| `RealmDashboardScreen` | 975 |
| `LivingRealmsEvents` | 678 |
| `SimulationState` | 509 |
