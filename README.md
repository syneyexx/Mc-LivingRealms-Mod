# Living Realms

**Checkpoint:** v3.0.0-rc4 buildfix14 — A–Z production completion pass

Minecraft 1.21.1 / NeoForge / Create 6.0.10 civilization + ecosystem simulation.

See `IMPLEMENTATION_LEDGER.md` and `docs/DETAIL_MATRIX.md` for the consolidated A–Z / correction / Claude-masterplan status.

### RC4 buildfix14 A–Z / worldgen production note
Builds on checkpoint21. Save schema remains **15** (schemas 1-14 readable), dashboard protocol is **14**, outer `ContentRevision=7` adds Waystone provenance (keeps rev-6 layout rebuild). Production deltas: terrain-cost intercity corridors (`TerrainCorridorPlanner`), real faction doors + entrance access repair, `PhysicalDevelopmentReconciler` catch-up, LR-only Waystone dedupe, blur removed on M/dialogue/dashboard/catalog, cached M-map surface samples, expanded `LocateQuery` commands, civilian adoption allowlist, capital court slots, and mandatory `ProductionQualityTest`.

### RC4 buildfix13 checkpoint21 worldgen/city correction note
This checkpoint targets the physical-world issues found in live play. Save schema remains **15** (schemas 1-14 readable), dashboard protocol is **14**, and outer `ContentRevision=6` performs the one-shot settlement-layout rebuild. Starter density is increased to 26 settlements per surface realm while physical materialization remains chunk-local/LOD bounded.

Settlement planning now uses connected orthogonal street blocks with residential side streets and sidewalks, denser lot-based housing, multi-storey apartment blocks in towns/cities, accessible ground-level floors, tier-aware road/keep completion keys, rectangular city walls/gates and proper capital castle footprints. Strategic intercity corridors are no longer sine-bent; Minecraft projection ignores trees as terrain, clears only natural vegetation and locally seeks lower-grade corridors instead of climbing tree canopies or steep ridges. Large `/livingrealms setday`/`advance` jumps trigger a bounded physical construction catch-up window so canonical growth becomes visible much faster near the player.

The Overworld spawn settlement is promoted/provisioned to city scale and therefore receives the capital castle path. **M** now renders without vanilla background blur and always paints a biome/terrain base instead of a blank discovered-only canvas. The main dashboard key is **F12**. Vanilla and villager-derived NPCs enter the same persistent no-LLM dialogue system on interaction (sneak-interact preserves the native trade interaction). Waystones are deduplicated by nearest canonical settlement so each settlement owns at most one generated stone. `/livingrealms locate mine` resolves actual planned mine structures and `locate city` has a robust largest-settlement fallback for unusual legacy/custom states.

A dedicated `WorldgenQualityTest` now guards capital castle scale, orthogonal arterial + side streets, sidewalks, apartments, accessible floor height, tier-driven physical expansion and straight strategic routes.


### RC4 buildfix12 Civilization layer note
Buildfix12 extends the buildfix11 Living Society foundation without replacing existing canonical systems. Save schema is **12**; schemas 1-11 remain readable. Settlement civilization state now tracks sanitation, disease pressure, education, water security, refugee pressure, bandit pressure, cultural cohesion, assimilation and resource pressure. Faction civilization state adds culture/faith/dialect identity, propaganda, intelligence networks and tributary relationships.

Demography now has a single authority: births, deaths, bounded settler attraction and migration/refugee movement all run through the civilization layer while aggregate settlement population remains canonical. Resource claims, raids/banditry, army desertion pressure, spy networks, tribute and emergent legends/monuments feed the existing economy, diplomacy, military and history systems rather than creating parallel truth. Rumors can travel between settlements through canonical transport links instead of globally teleporting.

The deterministic no-LLM dialogue engine is broadened to cover identity/work/family/health, settlements/factions/politics/law/tax, food/resources/trade/technology, war/migration/culture/religion/history and rumors, while retaining follow-up context and knowledge restrictions. A farmer still cannot answer state-secret questions simply because the fact exists in world history.

### RC4 buildfix11 Living Society note
Buildfix11 adds the first persistent **human society layer** without replacing the aggregate simulation. Named citizens now persist through physical despawn/reprojection with five needs, six personality traits, bounded personal memories and bounded relationships. Save schema is **11**; schemas 1-10 remain readable. A deterministic no-LLM Natural Language Dialogue Engine accepts free text, keeps short follow-up context, grounds answers in NPC knowledge/rumors and executes sensitive actions only through server-authoritative runtime checks.

Visible civic growth now includes wells, taverns, temples, clinics, schools, courthouses, prisons, orphanages, city gates, monuments and observatories. Healers, priests and scholars receive actual destinations/routines. The special **Wizard Trees** faction is seeded as a thirteenth faction alongside the twelve surface kingdoms: a hidden theocracy with three colonies and a dedicated underground construction plan using halls, homes, tunnels and redstone-lit grow chambers. `ContentRevision=5` adds it once to existing RC worlds.

**NPC weapon rule:** Guns++ (`mr_guns`) and GamingBarn's Guns are both hard-denied for citizens, guards and military. They remain available to the player and creative catalog. Eligible non-denied melee/ranged/magic/armor content can still be discovered from the target modpack. See `docs/LIVING_SOCIETY.md` for the civilization-layer contract and phased roadmap.

### RC4 buildfix10 integrated-world note
Buildfix10 expands Living Realms from a settlement simulation into a broader world-integration layer. The deterministic starter network is now **12 kingdoms / 216 settlements** and existing buildfix9 worlds expand once through `ContentRevision=4` without changing binary save schema 10. **M** opens the dedicated strategic world map (discovered biome/ecology cells, kingdoms, settlements, routes, armies and fronts); **K** opens a creative-only searchable catalog of live registered mod items with server-authoritative spawning.

Loaded vanilla/modded villager settlements and qualifying structure starts can now be adopted into Living Realms without bulldozing their existing physical infrastructure. The actual Overworld spawn is guaranteed kingdom coverage. Civilians physically perform bounded work: lumber, mature-crop farming/replanting, natural-ore mining, fishing and hunting, while street/road intents remain routine navigation targets. Guards and military discover compatible equipment from the fixed target modpack; Guns++ and GamingBarn's Guns are explicitly excluded from NPC loadouts. The humanoid citizen renderer includes held-item and armor layers so compatible equipment is visible.

The bundled ecology catalog is now **134 species**. Existing utility-brain/food-web behavior remains authoritative; this does **not** claim 134 bespoke GeckoLib models yet. Modded building content is used conservatively for non-load-bearing visual roles so target-pack aesthetics improve without reintroducing malformed towers/floating structures.

### RC4 buildfix9 living-kingdom note
Living Realms now seeds **8 feudal kingdoms and 88 settlements** (cities, towns, villages and hamlets), with bounded regional road networks instead of isolated points. Settlements use five organic deterministic layout archetypes, terrain-aware construction, wider streets with sidewalks, varied homes/markets and one-shot repair of older RC structures that used unsuitable foreign structural blocks. Existing RC4 worlds migrate once through `ContentRevision=3`.

Civilians now receive stable deterministic names and one of 12 appearance variants, can use settlement streets as routine waypoints, and settlements add housing as population grows. Military representatives can equip compatible registered non-denied mod weapons/armor; Guns++ and GamingBarn gun items are excluded from NPC loadouts. If Waystones is installed, a named global Waystone is materialized for each nearby loaded settlement through a failure-isolated compatibility bridge.

Players can found a canonical realm with `/livingrealms found <name>`. It enters the same population, construction, diplomacy, trade and war state as AI kingdoms; `/livingrealms locate mine` finds the nearest settlement in the player's realm. `/livingrealms setday <day>` and `/livingrealms advance <days>` run the intervening simulation rather than editing only the clock.

### RC4 buildfix7 runtime note
The first full-modpack integrated-world smoke reached gameplay and the Living Realms dashboard, then exposed a fatal modded-biome climate boundary case. Buildfix7 sanitizes external biome temperature/downfall before canonical ecology classification and adds regression coverage. Re-run the same exploration/save smoke before promoting RC4.

## Product scope

Living Realms targets **offline singleplayer only**. Minecraft still runs an integrated logical server in singleplayer, so the internal client/server boundary remains for save safety and authoritative simulation state. Internet multiplayer, dedicated-server support and external LLM integration are intentionally out of scope.




### Useful Living Realms commands
- `/livingrealms locate settlement` — nearest settlement of any tier.
- `/livingrealms locate city` / `town` / `village` / `hamlet` — nearest matching tier.
- `/livingrealms locate mine` — nearest settlement belonging to your current realm.
- `/livingrealms found <name>` — found your own canonical settlement/realm at your current position (distance and membership rules apply).
- `/livingrealms setday 50` — simulate every missing canonical day until absolute day 50. It refuses rewind.
- `/livingrealms advance 50` — simulate 50 additional days from the current day.
- `/livingrealms status` — canonical simulation summary.

## Current RC4 verification

The dependency-free Java 21 gate currently verifies deterministic simulation, the 134-species pack, strategic completeness, projection budgets/identity, all supported save-schema migrations, strict save-frame integrity, deterministic save-mutation fuzzing, production hardening invariants, and the exact 3650-day soak. `scripts/release-audit.py` additionally guards version pins, side safety, optional-mod isolation, bounded save resources and high-risk Minecraft 1.21.1 API assumptions.

The remaining release blockers are **linked/runtime** blockers: a full NeoForge/Create Gradle compile, integrated-server boot, client boot, real packet/action round-trip, Create kinetic-network verification and a full target-modpack smoke run. Offline singleplayer remains the only product target.

## v2.5 Society diagnostics
- Civilian-need calculations now have one canonical source (`SocietyDiagnostics`) shared by simulation and UI.
- Settlement telemetry exposes housing satisfaction, goods access, overall satisfaction, dominant pressure and pressure severity.
- The **Society** tab explains why each settlement is stable, strained or critical instead of showing unrest as an unexplained number.
- Dashboard protocol is **v8** and the integrated-client registration version is **5**.


## v2.6 Military command completion
- Strategic `MilitaryObjective` records now directly drive army movement instead of being passive metadata.
- Capture/siege objectives validate target ownership and hostility; defensive objectives work during peacetime; stale offensive orders are cancelled on peace/ownership changes.
- Dashboard protocol **v9** adds bounded active objectives and siege progress/blockade telemetry.
- War tab shows army orders and active siege state; strategic map continues to show spatial fronts.


## v2.7 Singleplayer settings
- Save schema **9** persists the active deterministic simulation configuration.
- Four curated profiles: Performance, Balanced, Immersive and Cinematic.
- Dashboard protocol **v10** exposes current LOD/entity/build budgets.
- Settings actions are validated and applied by the integrated server, then persisted with the world.
- Dashboard tabs now wrap responsively across rows instead of overflowing the panel as more systems are added.

## v2.8 Faction interaction
- Overview now exposes contextual **Join realm / Leave realm** actions.
- Join is server-authoritative and rechecks current jurisdiction, requested faction ID, reputation, bounty and custody status.
- Leaving works anywhere; spoofed join targets are rejected.
- Dashboard action errors are now generic/correct instead of incorrectly labelling every failure as a bounty error.

## Current foundation

- Deterministic off-screen simulation core (no Minecraft classes in `dev.livingrealms.sim`).
- Data-oriented species model with diet, lifecycle, habitat, group behaviour, prey/predator links and human aggression.
- Aggregate predator/prey/plant biomass ecology with births, mortality, starvation and habitat pressure.
- 99 bundled species across terrestrial, aerial, freshwater, coastal and marine ecosystems, with a 20-species Java fallback for headless/migration safety.
- 25 ecological biome archetypes.
- Factions, settlements, population, stockpiles, economy, diplomacy, armies and technology progression.
- Three-level simulation LOD: ABSTRACT / REGIONAL / PHYSICAL.
- Deterministic slot-level physical projection reconciliation: duplicate/orphan cleanup, spawn slots and LOD-safe dematerialization.
- Physical animal death accounting back into canonical aggregate populations.
- NeoForge server tick and admin commands.
- Create 6.0.10 dependency wired for Minecraft 1.21.1.
- Standalone core regression test that can run with only JDK 21.

## Test the simulation core

```bash
./scripts/test-core.sh
```

## Full mod development

Install JDK 21 and Gradle 8.10+ (or restore a Gradle wrapper) and run:

```bash
gradle runClient
gradle build
```

Create is a required runtime dependency in this initial branch.

## Commands

Player-facing:
- `/livingrealms wanted`
- `/livingrealms faction status`
- `/livingrealms faction join_here`
- `/livingrealms faction leave`

Admin:
- `/livingrealms status`
- `/livingrealms advance_day`

## Architecture direction

The project deliberately does **not** create one Minecraft Entity for every simulated citizen/animal. Distant populations are aggregate cohorts. Near a player, a materialization subsystem will instantiate physical entities and later merge them back into aggregate state. This is required for the intended scale.

## Release path from RC4

1. Run the full dependency-linked NeoForge 21.1.219 / Create 6.0.10 Gradle build.
2. Boot a clean singleplayer integrated server and client, then verify save/create/load/reload.
3. Exercise dashboard request/response plus mutating actions against the real integrated server.
4. Verify projected Create industrial yards form valid kinetic networks and remain projection-only rather than canonical production authority.
5. Smoke-test the complete requested modpack with Living Realms worldgen coexistence, Waystones, storage/property crime, compatible faction equipment/palettes and mod-weapon equipment, verifying both gun-mod deny-lists.
6. Treat v3.0 as feature-complete only after all release gates pass; use v3.x for integration/content/visual polish and bug fixes. Promote to v4.0 only after the final linked build, boot tests, long-world test and performance pass are all clean.

## v0.2 additions

The project now contains a headless animal decision layer and a materialization planner. Predators can choose to hunt, prey can flee, dangerous species can defend against humans, social animals regroup, and unsuitable habitat can trigger migration intent. These decisions are species-parameterized rather than hardcoded per animal.

The materialization planner applies a hard entity budget around players. A herd of 2,000 zebra therefore remains one aggregate population in distant simulation while only a representative local subset becomes real Minecraft entities near a player.


## v0.3 additions

The materialization layer now has stable per-cohort projection slots. Re-running reconciliation is idempotent: an already satisfied slot does not spawn another entity, duplicate slot occupants are explicitly removed, orphaned entities are removed, and moving a cohort out of PHYSICAL LOD produces dematerialization actions without reducing the aggregate animal population.

`SimulationState.recordPhysicalAnimalDeath(...)` is the only path introduced in this checkpoint for a real physical death to reduce canonical population. This separation is intentional: unloading a chunk or reducing LOD must never be mistaken for ecological mortality. Spawn placement is deterministic per world seed, cohort and projection slot.


## v0.4 additions — terrestrial physical wildlife

- `livingrealms:wildlife` NeoForge entity type with persistent species/cohort/projection-slot identity.
- Server projection index driven by join/leave events.
- One-second materialization reconciliation around Overworld players without force-loading chunks.
- Dematerialization explicitly bypasses ecological death accounting.
- Real entity death feeds exactly one animal back into canonical population loss.
- Species parameters drive health, attack damage, movement speed, armor and knockback resistance.
- Physical utility-AI translates the pure `AnimalBrain` intents into hunting, defending, fleeing, feeding/resting and navigation actions.
- Client model-layer + renderer registration and a temporary generic quadruped asset are included so the entity has a complete rendering path.

The generic quadruped is deliberately temporary. Final animal presentation requires morphology families (canid/felid/ungulate/bear/rodent/reptile/bird/fish/cetacean/etc.) rather than scaling one mesh into every species.


## v0.5 additions — autonomous trade and warfare

- Bilateral trade agreements and resource exchange based on actual surplus/deficit and buyer treasury.
- Daily production expanded with coal and copper.
- Hostile diplomatic relations can deterministically escalate into war.
- Armies choose enemy settlements, move strategically, consume supply and resupply near friendly settlements.
- Opposing armies fight with power, morale, supply and deterministic battle variance.
- Destroyed armies are removed.
- Undefended settlements can be captured and transferred between factions.
- Dedicated regression tests verify trade and conquest while the 365-day deterministic replay and save/load continuation still pass.


## v0.6.0 — settlement construction planning

- Deterministic append-only settlement blueprints for keeps, housing, farms, roads, markets, warehouses, workshops, barracks, city walls, factories and airfields.
- Construction completion state is persisted per settlement.
- Simulation save schema upgraded to 2 with backward-compatible schema-1 loading.
- A real schema-1 regression fixture guards save migration.


## v0.7.0 — physical settlement construction

- Semantic structure blueprints resolve strategic settlement intents into deterministic block operations.
- Resumable, fair construction scheduler enforces a hard per-tick operation budget.
- All strategic roles have physical layouts, including houses, farms, roads, keep, market, warehouse, workshop, barracks, walls, factory, airfield and dock.
- Minecraft adapter only discovers construction near players and only edits loaded chunks.
- Four deterministic faction architecture palettes use robust vanilla blocks; Create machinery is kept as a separate integration layer.
- Construction completion is written back to canonical settlement state.


## v0.8.0 — industry and Create bridge

- Deterministic strategic industry with per-process inputs, outputs, technology gates and mechanical stress cost.
- Town/city/metropolis industrial capacity scales independently from physical chunk loading.
- Fuel, tools, machinery, ammunition and textiles are now producible from actual strategic stockpiles.
- Faction economy invokes industry every simulated day.
- Physical factories resolve safe Create casing blocks through the runtime registry with vanilla fallbacks.
- Strategic production remains authoritative; powered kinetic contraptions are intentionally a later projection layer rather than a requirement for off-screen simulation.


## v0.9.1 — persistent trade logistics checkpoint

- Strategic trade no longer teleports cargo directly between faction stockpiles.
- Trade agreements dispatch persistent `TradeShipment` objects between real settlement coordinates.
- Shipments advance at bounded strategic speed, can be intercepted near hostile territory, and deliver only on arrival.
- Shipment identity, cargo, value, endpoints and route progress are persisted in save schema 3.
- Schema 1 and 2 saves remain readable.
- `SimulationState` owns shipment lifecycle and deterministic daily logistics scheduling.
- Core regression tests cover dispatch, non-teleportation, persistence, delivery and deterministic continuation.


## v0.10.0 — physical caravan projection planner

- Nearby in-flight trade shipments are selected for physical representation around players.
- A hard global caravan budget prevents entity explosions.
- Selection is deterministic: nearest shipment first, stable shipment-id tie breaking.
- Reconciliation guarantees at most one physical caravan per canonical shipment.
- Duplicate caravan projections are removed without affecting strategic cargo.
- Leaving physical radius dematerializes a caravan without destroying its shipment.
- Finished/intercepted shipments cause their stale physical projections to be removed.
- The planner is pure Java and regression-tested independently of Minecraft.


## v1.0.3 reliability and law/ecology expansion

This checkpoint adds schema-5 custody persistence, complete strategic law escalation, treasury-backed single-claim bounty contracts, faction property claims, witnessed property-crime classification, state invariant validation, a deterministic 3650-day soak test, data-driven morphology/locomotion fields for species, and a Minecraft-style biome-to-ecology classifier. See `COMPLETION_MATRIX.md` for the strict definition of done.


## v1.1.0 — naval + player standing

- Persistent ports and fleets with ship classes, shipbuilding, fuel, supply, readiness and experience.
- Naval missions, fleet movement, naval battles, blockades and trade-shipment interdiction.
- Landing-ship personnel capacity and bounded physical ship projection near players.
- Physical ship destruction reconciles back into the canonical fleet once; LOD dematerialization is not counted as a loss.
- Persistent per-faction player reputation, membership, service, ranks, promotion and expulsion.
- Runtime commands expose current standing and allow joining the uncontested jurisdiction the player is physically standing in.
- Save schema 6 covers naval state and player standings.

This checkpoint is not labelled a final release: `COMPLETION_MATRIX.md` is the authoritative definition of done.


## v1.1.0 — naval + player standing checkpoint

- Canonical ports, fleets, ship classes, fuel/supply/readiness, patrol/intercept/raid/blockade/amphibious missions and deterministic naval combat.
- Port ownership follows conquered settlements; fleets recover from loss of a home port by selecting a friendly operational harbor.
- Blockades affect both port condition and the associated settlement economy.
- Landing ships carry canonical personnel.
- Bounded physical naval projection with one fleet-slot identity per ship entity; real destruction reconciles into canonical fleet composition, LOD dematerialization does not.
- Per-player faction reputation, membership, service points and rank progression with crime-driven reputation penalties/expulsion.
- Schema 6 persistence covers naval state and player standing.
- Core regression, strategic completeness and 3650-day soak/invariant tests all pass.
- Full NeoForge/Create Gradle compile remains an explicit unresolved release gate because this execution environment currently lacks Gradle/dependency access.


## v1.1.1 — runtime faction service hooks

- Player members now earn faction service when they personally destroy hostile physical military units, aircraft or ships during an actual WAR relation.
- Service is awarded only after canonical physical-loss reconciliation succeeds, preventing LOD unloads or duplicate death events from farming rank progression.
- Rank promotion feedback is sent immediately to the player.
- Neutral/non-member kills do not grant faction service.


## Bounty hunting

Living Realms now has persistent jurisdiction-local bounty contracts, player bounty assignments, bounded NPC bounty-hunter projection and persistent live-capture custody. Runtime commands: `/livingrealms bounty board`, `/livingrealms bounty accept`, `/livingrealms bounty active`, `/livingrealms bounty abandon`, plus `/livingrealms wanted`.


## v1.5.0 — persistent industrial sites

- Save schema 7 adds canonical industrial-site state: owner, settlement, kind, level, condition, status, starvation, downtime, cycles and utilization.
- Industry now has maintenance, breakdown, repair and recovery semantics instead of an inferred site count.
- Industrial ownership follows settlement conquest.
- Nearby completed workshops/factories receive bounded Create 6.0.10 machinery projections without making loaded chunks authoritative.
- Breaking blocks in a projected industrial yard damages the canonical site and can create a witnessed sabotage crime.
- `/livingrealms industry` reports live site health and utilization.
- Full core, strategic and 3650-day soak/invariant suites remain green.


## v1.6.0 — server-authoritative strategic dashboard

- Adds a versioned, immutable `RealmDashboardSnapshot` built only from canonical server simulation state.
- Adds bounded NeoForge request/response payloads for dashboard synchronization; the client never receives mutable simulation objects.
- Snapshot bounds: 32 factions, 24 settlements, 16 active wars, 40 history entries and a hard 65,536-character JSON ceiling.
- Press **J** to open the read-only strategic dashboard; tabs cover overview, realms, cities, wars, economy and history.
- The dashboard exposes local jurisdiction, faction membership/rank, reputation, infamy, wanted status, bounty and custody state.
- Server request throttling prevents packet spam; malformed/oversized snapshots are rejected.
- Common networking code contains no `net.minecraft.client` dependency; client receiver installation occurs only on the physical client.
- Server snapshot construction is exception-bounded and reports a localized failure instead of allowing the packet handler to fail outward.
- Core dashboard codec/bounds/immutability tests run as part of the standard Java 21 `-Xlint:all -Werror` suite.
- **Still not claimed as release-complete:** a dependency-linked NeoForge/Create compile plus real client/server packet smoke test remain mandatory release gates.


## v1.7.0 — strategic world map

- Dashboard protocol v2 adds bounded spatial map data generated exclusively from canonical server state.
- Map data includes settlement markers, derived territorial claim radii, ROAD/RAIL links, army positions and active-war front lines.
- The client Map tab renders those layers plus the requesting player's position.
- Overflow tests exercise maximum map collection sizes while enforcing the existing 65,536-character packet ceiling.


## v1.8.0 — interactive law and bounty office

- Dashboard protocol v3 adds a bounded jurisdiction-local bounty board.
- The Law tab exposes wanted state, bounty, infamy and available/assigned contracts.
- Players can accept or abandon contracts directly in the GUI.
- Mutating actions are revalidated server-side by a pure canonical authorization service; wilderness, contested and wrong-board actions are rejected.
- Network actions have a dedicated per-player anti-spam limiter and trigger an immediate authoritative snapshot refresh after success.


## v1.9.0 — operations dashboard

- Dashboard protocol v4 adds bounded server-authoritative operations data.
- The Ops tab displays active trade shipments, transport routes and persistent industrial sites.
- Shipments expose resource, amount, parties, value, progress and route distance.
- Routes expose mode, endpoints, quality, security, throughput and operational status.
- Industry exposes type, settlement, level, condition, status, utilization, cycles and downtime.


## v2.0.0 — spatial ecology and species-pack expansion

- Save schema 8 adds canonical ecosystem-region centers while keeping schemas 1-7 readable.
- A deterministic habitat colonizer seeds compatible species into explored ecological regions and after safe catalog expansion.
- The bundled datapack contains 99 strict species definitions with explicit morphology, locomotion, swim/flight factors and validated predator/prey references.
- `scripts/generate-species-pack.py` is the reproducible content pipeline for the bundled species pack.
- Long-run ecology now has a biodiversity regression gate; predator functional response/prey refugia and juvenile-survival scaling prevent the prior deterministic mass-extinction failure.
- Minecraft/NeoForge biome facts are normalized from biome IDs, holder tags, temperature and downfall into the 25 Living Realms ecology archetypes. NeoForge 1.21.1 exposes modified climate settings for this purpose.
- Exploration discovers ecology cells only around actual Overworld players and never force-loads chunks.

The bundled 99-species set is **not** claimed to be every animal on Earth. The engine/data pipeline is now structured so the catalog can scale into hundreds or thousands without adding per-species Java code; bespoke models and high-fidelity biological tuning remain separate content work.


## v2.1.0 — ecology dashboard

- Dashboard protocol v5 adds bounded server-authoritative ecology telemetry.
- The Ecology tab shows discovered ecosystem regions, distance, area, plant biomass, total wildlife and dominant population cohorts.
- Cohort rows expose health, hunger, thirst, locomotion and morphology without giving the client mutable simulation state.
- Snapshot limits are 16 regions and 6 dominant species per region; codec overflow tests continue to enforce the 65,536-character transport ceiling.
- Network registration protocol is bumped to 3 so older dashboard wire formats cannot silently interoperate.


## v2.2.0 — Forces dashboard

- Adds a dedicated Forces tab to the singleplayer strategic dashboard.
- Air wings expose mission, role, aircraft strength, fuel, readiness and experience.
- Fleets expose ship composition, combat power, mission, fuel, supply, readiness and embarked personnel.
- Ports expose level, condition, security and operational state.
- The tab consumes the existing bounded server-authoritative Forces snapshot; no direct client mutation path was introduced.


## v2.3.0 — politics and diplomacy dashboard

- Dashboard protocol v7 adds bounded foreign-relation and treaty telemetry.
- A new Politics tab shows ruler/government/succession, stability, legitimacy, corruption and tax rate.
- Foreign realms show relation status, numeric opinion and trade-agreement state.
- Active treaties show type, counterparty and duration.
- The builder never creates missing diplomatic relations while reading UI state; absent relations are presented as neutral.
- Snapshot limits are 24 relations and 24 treaties, with codec bounds and protocol-mismatch regression tests.

## v2.4.0 — primary economy, citizen routines, modpack compatibility

- Added terrain/ecology-aware **mines, lumber camps and fisheries** as real construction roles and blueprints.
- Completed primary-economy sites now produce wood, food, stone, iron, coal, copper and small gold output.
- Added deterministic citizen routines: farmers, miners, lumberjacks, fishers, artisans, traders, builders, officials and guards move toward role-appropriate workplaces.
- Added an explicit compatibility policy for Create, GeckoLib, Curios, FerriteCore, 3D Skin Layers, JEI, Clumps, Shulker Box Tooltip, Biomes O' Plenty, GlitchCore, TerraBlender, Waystones, Balm, Terralith, Lithostitched and Traveler's Backpack.
- Living Realms **never replaces the active biome source and never overwrites existing block entities**, protecting the requested worldgen/travel/storage stack.
- Added BOP/Terralith-friendly biome-id normalization and a Windows/Linux production build bootstrapper.

See `docs/MODPACK_COMPATIBILITY.md`.


## v2.9 realm management
Faction members can now change their own realm tax rate and set each owned settlement to Balanced, Food, Housing, Industry, or Defense. Settlement policy is persisted in save schema 10 and directly changes canonical construction priority. The Settlements tab manages one settlement per page so policy actions have an unambiguous target.


## v2.9.3 — target-pack refresh + real container theft
- Target-pack compatibility catalog expanded to the full requested singleplayer pack.
- Iron's Lib is included in the target pack.
- Create Deep Seas and Create Aeronautics are explicitly excluded.
- Guns++ registry items are player-only and excluded from faction/military equipment discovery.
- Faction-owned block containers now use server-side open/close inventory deltas. Looking is not theft; only net removed items count.
- Traveler's Backpack blocks are excluded from faction-property theft tracking.
- Theft still requires a valid witness before bounty/notoriety is registered.
- Own-faction property access is authorized and cannot produce a theft crime.


## v2.9.4 release hardening
- Exact 30/365/3650-day deterministic persistence/invariant soak gates.
- Dedicated projection stress test for wildlife, caravans, citizens, armies, aircraft and fleets.
- Projection identity/duplicate/orphan cleanup is now a non-negotiable release invariant.


## v2.9.5 save integrity
Living Realms now refuses to decode a world against an incomplete species catalog. The integrated server waits for datapack species reload readiness, and removed live species definitions fail fast with a clear save-load error instead of corrupting ecology state.


## v2.9.9 Create industry projection
Industrial sites now have a single canonical physical writer. The former overlapping machinery materializer was removed, eliminating competing block placement at the same site. Projection distance follows the active singleplayer profile while production remains canonical/off-screen.


### v2.9.9 target-pack visuals
Faction decorative architecture can draw non-load-bearing materials from compatible content mods while foundations/walls/beams/roofs stay on robust structural palettes. Faction equipment discovery covers broader RPG/ranged/magic item families while denied gun mods remain player-only.


### v2.9.9 local markets
Completed settlement markets now support physical 8-unit buy/sell transactions using emeralds. Quotes and final accounting remain integrated-server authoritative and scarcity-driven.

### RC3 note
The 3.0.0-rc3 source includes additional Minecraft/NeoForge 1.21.1 API hardening for faction-container inventory identification and Heightmap imports. It is still a release candidate until a full linked NeoForge/Create build and Minecraft boot test pass.


## v3.0.0-RC4 production hardening
- Added reset-safe dashboard request/action rate limiting so switching integrated worlds in one client process cannot inherit a stale tick window.
- Canonical IDs now maintain and repair a high-water mark across imported and migrated state, preventing post-load ID reuse.
- Current-schema saves are semantically validated before write and after decode; stale ID watermarks are repaired during migration.
- Minecraft SavedData now stores an outer/inner schema consistency check plus a CRC32 integrity token. Legacy/pre-checksum saves remain readable and are marked dirty for rewrite.
- Canonical payloads are capped at 32 MiB, individual strings at 64 KiB, malformed UTF-8 is rejected, and deterministic mutation fuzzing is part of both production build paths.
- Simulation config and world positions reject non-finite/runaway values. Settlement construction now uses the persisted `constructionBlockOpsPerTick` profile instead of a hardcoded budget.
- Production Gradle bootstrap is pinned to Gradle 8.10.2 and verifies the official binary SHA-256 before use; Windows and shell runners execute equivalent hardening gates before linked compilation.
- All locally executable gates are green. The dependency-linked build remains externally blocked in this sandbox because `services.gradle.org` cannot be resolved; no Living Realms source compiler failure was reproduced.

### RC4 buildfix5
Two dashboard action-button loops were made Java-compiler-safe by capturing immutable per-iteration action/label values rather than the mutable loop index. Local core, soak and release-audit gates remain green; re-run `build-production.ps1` to continue the linked NeoForge/Create compile.
