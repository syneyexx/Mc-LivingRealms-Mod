# Living Realms project state

**Checkpoint:** v3.0.0-rc4 A–Z deepen (geography morphologies + plazas + cause summaries + ContentRevision 10)

## Current production state
- Starter density target is **32** settlements per surface realm (~380+ with Wizard Trees) including fertile rural hamlets; physical entities/blocks remain player-local and budgeted.
- Local settlement economy (schema **16**): barn/granary/stockpile, seasonal farms/pastures, agrarian crop mix, faith calendars, route upkeep, typed bandits, trader price rumors.
- Naval ports are discovered from ship-suitable geography; coastal settlements plan docks; locate-port works after discovery.
- Court projections bind dynasty ruler/heir identities; foreign NPC adoption infers roles; dialogue OPEN_TRADE quotes local/realm markets.
- See `docs/DETAIL_MATRIX.md` for per-detail masterplan status.
- Settlement streets use geography-derived morphologies (`SettlementMorphology`: coastal port, river/valley, hill town, radial capital, organic medieval, market-cross, boulevard, industrial edge) with connected arterials, residential side streets, sidewalk lights/benches and civic plazas; houses use cottage/longhouse/townhouse/porch variants and towns/cities add multi-storey apartment blocks with density-compressed housing capacity.
- Construction completion requires physically acceptable required geometry (`StructureMaterializationReceipt`). Decorative skips are allowed; missing foundation/wall/door/path is not.
- Typed authored-block provenance (`AuthoredOwnerType` + `AuthoredBlockLedger` + `WorldMutationGuard`, ContentRevision **10**) protects player/foreign builds; block entities remain a hard stop; ALREADY_CORRECT never claims unknown identical blocks. ContentRevision 10 also rebuilds settlement morphology completion keys once for geography-derived streets/plazas.
- Building floors are walkable; real faction wood doors are placed; EntranceAccessPlanner repairs door↔street grades including steeper switchbacks. Tier-specific road/keep/wall/gate keys ensure settlements physically expand after tier upgrades.
- Intercity routes use bounded terrain-cost corridor planning **without** destructive straight-road fallback. SettlementGeographyProfile drives water modes (name heuristic is bootstrap/fallback only).
- `/setday`/`advance` catch-up uses PhysicalDevelopmentReconciler. Dashboard key is **F12**; world map **M**; creative catalog **K**.
- Waystones: one Living Realms-authored stone per settlement with outer-save provenance; player stones are never auto-destroyed.
- Release suite is driven by `scripts/core-tests.list` (Linux + Windows parity). Production runners emit `RELEASE_MANIFEST.json`.
- Ledger: `IMPLEMENTATION_LEDGER.md`.

## Verified core gates
- Java 21 core compiles with `-Xlint:all -Werror`.
- 365-day deterministic replay passes.
- Full bundled species-pack audit passes: **134 species** with explicit morphology/locomotion fields and a validated food web.
- Strategic completeness suite passes.
- 3650-day deterministic soak now has explicit persistence/invariant gates at day 30, 365 and 3650, plus a biodiversity floor.
- Save schema is **16**; schemas 1-15 remain readable. Schema 16 persists per-settlement barn/granary capacity and local stockpiles. Dashboard snapshot protocol is **16** (settlement cause summaries + Ops assistance task board). Network registration is **14**. Outer Minecraft `ContentRevision=10` rebuilds morphology completion keys once and keeps typed authored-block ownership + Waystone provenance.
- `ConstructionIntegrityTest`, `TradeLivenessTest` and `OrganicMorphologyAndCauseTest` are mandatory core gates.

## Explicit status claim
**CODE COMPLETE / EXTERNAL GATE UNVERIFIED**

Proven in this pass:
- full core suite green (**36** tests including `OrganicMorphologyAndCauseTest` + 3650-day soak)
- release-audit green (schema16 / protocol16 / net14 / content10 + morphology/cause/task-board authorities)
- geography-derived `SettlementMorphology` street patterns, plazas, sidewalk lights; `WorldCauseExplainer` on F12 Society + dialogue
- night curfew / low-order nightlife; ship class visuals; wildlife mass scaling; terrain cliff/pad rejection
- court/ruler presentation: keep-centered spawn, government titles, distinct skins, dialogue self/ruler awareness
- `INFRASTRUCTURE_REPAIR` assistance tasks from route/industry wear; stone contributions reopen routes and repair sites
- holy-day civic rites + festival decorations + verified assistance + refugee camp enqueue remain intact
- linked NeoForge/Create build must be re-run on this tip (prior tip was green)

Still unverified externally:
- fresh-world / migrated-world client smoke
- full target modpack coexistence
- Waystones / Create kinetic / chunk-unload stress in a real client session

## Ecology/world integration
- Ecosystem regions now have canonical world centers instead of existing only as abstract biome buckets.
- Compatible species deterministically colonize newly discovered regions and expanded datapack catalogs.
- Predator/prey dynamics use prey refugia and juvenile-survival scaling to prevent deterministic ecosystem collapse.
- Minecraft/modded biome IDs and holder tag paths are normalized into the 25 Living Realms ecology archetypes.
- Overworld exploration discovers bounded 768-block ecology cells without force-loading chunks.
- Incompatible live species reloads are rejected and the previous canonical catalog is restored.

## Species content
- 134 bundled species span terrestrial, flying, freshwater, coastal and open-ocean ecosystems.
- `scripts/generate-species-pack.py` deterministically builds the bundled datapack and derives reverse predator links.
- Numeric biology values are gameplay-tuned simulation defaults; datapacks can refine them without changing Java.


## v2.1 dashboard integration
- Dashboard protocol **v5** adds bounded ecology telemetry: catalog size, discovered regions, total wildlife, plant biomass, population groups and dominant species.
- Species telemetry includes canonical population, health, hunger, thirst, locomotion and morphology.
- Client dashboard now has an **Ecology** tab; all values originate from immutable server snapshots.
- Network registration version is bumped to **3** to prevent incompatible dashboard wire formats from silently mixing.
- Bounds: at most 16 ecology regions and 6 dominant species per region in a dashboard snapshot.

## Production target
Minecraft 1.21.1 + NeoForge 21.1.219 + Create 6.0.10 + Java 21.


## v3.0.0-RC4 buildfix12 Civilization layer
- Adds bounded per-settlement civilization state for sanitation, disease, education, water security, refugee/bandit/resource pressure, cultural cohesion and assimilation.
- Adds bounded per-faction culture/faith/dialect, propaganda, intelligence networks and tributary state.
- Makes `CivilizationEngine` the single demographic authority for integrated worlds: births/deaths, settler attraction and migration/refugees update the existing canonical settlement population rather than a duplicate population model.
- Adds canonical resource claims, raids/banditry, desertion pressure, espionage/propaganda, tribute and emergent legend/monument promotion integrated with existing factions, armies, diplomacy, stockpiles and history.
- Rumor propagation can cross canonical transport links with source preservation and reliability loss.
- Expands no-LLM free-text dialogue across work/family/health, politics/law/tax, resources/technology, war/migration, culture/religion/history and rumors while keeping profession/knowledge restrictions and follow-up context.
- Save schema **12** persists the new bounded state and preserves migrations from schemas 1-11.
- New `CivilizationLayerTest` and `RumorNetworkTest` lock the new behavior; the existing 3650-day deterministic soak remains mandatory.

## v3.0.0-RC4 buildfix11 Living Society / civilization foundation
- Adds bounded persistent `SocialCitizen` identities behind physical citizen projections: needs, personality, age, health, money, memory and relationship state persist in schema 11. Aggregate settlement population remains authoritative.
- Adds bounded local rumor diffusion and profession-aware knowledge. NPCs do not become omniscient simply because an event exists in world history.
- Adds a deterministic no-LLM free-text dialogue engine and client/server dialogue session: intents, synonyms, topics, follow-up context/pronouns, source attribution, personal knowledge and dynamic composition. Gift/trade/guard actions remain server-authoritative.
- Expands the existing settlement planner with wells, taverns, temples, clinics, schools, courthouses, prisons, orphanages, gates, monuments and observatories. Healer/priest/scholar roles use those structures; civic facilities feed back into social need/health targets.
- Seeds **Wizard Trees** alongside the twelve surface kingdoms as a hidden theocracy with three colonies. Minecraft construction routes it through a dedicated underground planner for excavated halls/homes/tunnels and redstone-lit grow chambers instead of surface settlement construction. Existing worlds receive this once through `ContentRevision=5`.
- Guns++ (`mr_guns`) and GamingBarn's Guns are both explicit NPC/guard/military deny-list content. Priests/scholars may visibly carry eligible non-denied magic/staff content; real spell casting remains a linked-runtime adapter gate.
- `docs/LIVING_SOCIETY.md` is the architecture contract for later family/demography, disease, culture/religion, raids/banditry, espionage, propaganda, political marriage, migration/refugees, water/resource claims, piracy, legends and generated state-backed tasks.

## v3.0.0-RC4 buildfix10 integrated living-world expansion
- The canonical starter world now seeds **12 kingdoms / 216 settlements** (24 cities, 89 towns, 87 villages and 16 hamlets in the deterministic density gate). Existing buildfix9 saves expand exactly once through outer SavedData `ContentRevision=4`; the binary save schema remains 10.
- **M** opens a dedicated full-screen Living Realms strategic world map. It renders server-authoritative discovered biome/ecology cells, kingdoms/claims, settlements, routes, armies, war fronts and the player. Snapshot limits were raised but remain hard bounded.
- **K** opens a creative-only searchable target-mod item catalog. Items come from the live item registry and spawn only through a server-authoritative validated packet; the screen does not fabricate client-only stacks.
- The user's listed modpack is treated as the fixed **target content pack**. Stable APIs are used where appropriate; other content is discovered through registries/tags to avoid brittle Java links. Guns++ and GamingBarn's Guns stay explicitly player-only for NPC equipment; eligible RPG/ranged/magic equipment remains available.
- Foreign settlement integration now covers both loaded villager clusters and loaded vanilla/modded structure starts. Adoption preserves the existing village/structure instead of overwriting it, then future Living Realms population/infrastructure growth extends it.
- A spawn-kingdom guard ensures the actual Overworld spawn is covered by a Living Realms city/kingdom; if the normal seeded capital is not close enough, a Crownspawn city is created.
- Civilian physical work expanded beyond routines: lumberjacks harvest natural trees, farmers harvest/replant mature crops, miners work natural ore blocks, fishers generate bounded catches near water, and hunters seek Living Realms wildlife. These actions feed canonical faction resources.
- Citizens periodically commute over completed settlement ROAD intents. Guards/military receive target-pack equipment; the humanoid renderer now includes held-item and armor layers so compatible equipment is visibly worn/held.
- Bundled wildlife expanded from 99 to **134 species**, including additional dogs/cats, hamster, grasshopper, saber-tooth cat, birds of prey/vultures, primates, elephants, snakes, fish and sharks. They use the existing data-driven ecology/behavior system; bespoke per-species 3D art is still a separate visual-content gate.
- Structural safety remains non-negotiable: foreign mod blocks are limited to non-load-bearing decoration/path/light roles unless specifically authored. Terrain-aware foundations, build-site search and old-structure repair from buildfix9 remain intact.
- Network registration is **11** for the new creative-spawn packet; dashboard snapshot protocol remains **12**. Save schema remains **10**.
- Headless Java 21 gates and the 3650-day deterministic soak pass at the new world density. The Minecraft/NeoForge-linked build and full fixed-modpack runtime smoke are still mandatory external gates before release.

## v3.0.0-RC4 buildfix9 living-kingdom upgrade
- Starter canonical world now seeds **8 FEUDAL_MONARCHY realms / 88 settlements**, with capital cities plus towns, villages and hamlets. Generated settlement centers are deliberately spaced and each realm builds bounded connections to nearby same-realm settlements.
- Existing older RC4 worlds migrate exactly once through outer SavedData `ContentRevision=3`: missing dense-world content is added and prior completed-construction markers are reset so old malformed/foreign-block RC structures can be rebuilt by the safer terrain-aware materializer. Binary save schema remains 10.
- Settlement roads are wider and include sidewalks; regional ROAD routes physically materialize carriageways + sidewalks and civilians periodically commute via canonical street intents.
- Settlement construction is terrain-aware: low-slope/dry build-site search, terrain-following roads/walls and foundation support reduce floating/buried buildings. Structural palette roles no longer accept arbitrary foreign mod blocks; compatibility content is limited to non-load-bearing decoration/path/light roles.
- Population growth is bounded, supports deterministic immigration when food/housing allow it, and automatically expands housing/infrastructure. Physical buildings therefore appear as a consequence of canonical growth.
- Player command `/livingrealms found <name>` creates a canonical player realm/settlement at the player's position and initializes membership, ruler identity, resources, army and bilateral relations. `/livingrealms locate mine` finds the player's nearest settlement.
- Waystones compatibility is reflection-isolated: when Waystones is installed, nearby loaded settlements receive one named global Waystone without making Waystones a hard compile dependency.
- Citizen representatives have stable deterministic names and 12 appearance variants. Military/faction equipment discovery recognizes eligible ranged/melee/magic item families while explicitly excluding `mr_guns` and GamingBarn gun namespaces.
- Latest full-modpack crash regression is fixed: flying Living Realms wildlife now registers `minecraft:generic.flying_speed`, preventing `FlyingMoveControl` from crashing on species such as Common Raven. Military projections also self-rescue from solid-block collision before vanilla tick damage.
- New mandatory `LivingWorldDensityTest` locks 8 kingdoms, 88+ settlements, tier mix, route density, layout diversity, player-founded realm semantics, stable NPC identity and setday progression.

## v3.0.0-RC4 buildfix7 runtime smoke findings
- External Windows full-modpack smoke has now proven the RC4 JAR compiles, loads under Minecraft 1.21.1 + NeoForge 21.1.252 + Create 6.0.10, reaches an integrated world, and opens the dashboard.
- The first multi-minute exploration smoke exposed a fatal Living Realms biome-boundary bug: a modded biome supplied non-finite or out-of-range climate data and `BiomeObservation` rejected it with `IllegalArgumentException: climate`.
- `BiomeSignalNormalizer` now treats Minecraft/modded biome climate as untrusted adapter input: non-finite temperature/downfall use neutral fallbacks and downfall is clamped into the canonical 0..1.5 envelope before strict simulation state construction.
- `ProductionHardeningTest` now regression-tests NaN, extreme positive and negative climate signals.
- Runtime smoke must be repeated before the integrated-world gate can be called green; tick-lag and physical-unit collision placement remain observations for follow-up profiling/hardening.

## v3.0.0-RC4 production hardening
- Dashboard request/action limiters are reset-safe across logout, server stop and backwards tick clocks.
- Canonical ID allocation now observes imported IDs and repairs stale persisted `nextId` watermarks.
- Current-schema encode/decode runs semantic validation; payloads reject trailing bytes, malformed UTF-8, oversized strings and states above 32 MiB.
- SavedData verifies payload checksum plus outer/inner schema agreement and rewrites legacy/pre-checksum payloads on the next save.
- Simulation config/world-position inputs have finite production bounds.
- Settlement construction uses the persisted per-tick operation budget.
- Save migration coverage spans schemas 1-11 and the deterministic corruption fuzz gate mutates 768 payload bytes/bit positions per run.
- Production bootstrap verifies the official Gradle 8.10.2 SHA-256.
- Local Java 21 core suite, 3650-day soak and release audit are green. Linked NeoForge/Create compilation is still blocked only by sandbox DNS/download access.

## Remaining release gates
- Full dependency-linked NeoForge/Create Gradle compile.
- Integrated singleplayer-server and client boot smoke tests.
- Real integrated-server dashboard/action round-trip test.
- Verified Create kinetic networks.
- No custom biome-source replacement by design: Biomes O’ Plenty/Terralith/TerraBlender/Lithostitched remain terrain authorities; Living Realms overlays ecology on their biomes.
- Bespoke wildlife meshes/textures/animations beyond the current morphology families.
- Remaining management/presentation rows in `COMPLETION_MATRIX.md`; bespoke wildlife art/animation remains incomplete.


## Scope decision — singleplayer
- Multiplayer and dedicated-server support are outside product scope.
- The client/integrated-server authority boundary stays because Minecraft singleplayer still uses a logical server and because it protects canonical simulation state.
- Offline singleplayer is the sole product target. No external LLM bridge is planned.
- A clean `scripts/test-core.sh` rebuild on this checkpoint passes all four core gates; the earlier transient `WarState` class-loading failure was stale/interrupted build output, not a missing source class.

## v2.2 Forces dashboard
- Adds a dedicated **Forces** tab using the already bounded canonical force snapshot.
- Air wings show model/role, aircraft count, mission, fuel, readiness, experience and distance.
- Fleets show composition, mission, combat power, fuel, supply, readiness, embarked personnel and distance.
- Ports show level, condition, security, operational status and distance.
- No new mutable client state or protocol expansion was required; all data was already covered by bounded snapshot/codec tests.

## v2.3 Politics dashboard
- Dashboard protocol **v7** adds bounded realm relations and active treaty detail.
- Politics view exposes government type, succession law, ruler, stability, legitimacy, corruption and tax rate.
- Foreign relations expose status, opinion and trade-agreement state without mutating canonical diplomacy.
- Active treaties expose counterparty, type and start/end day.
- Bounds: at most 24 foreign relations and 24 active treaties per snapshot.
- Internal dashboard network registration version is bumped to **4**.
- Codec overflow, protocol mismatch and exact round-trip tests cover the new political data.


## v2.4.0 checkpoint
- Primary economy + citizen routines are implemented and core-tested.
- Compatibility policy covers the requested 1.21.1 NeoForge modpack.
- Create remains the only hard gameplay dependency.
- Worldgen coexistence rule: consume/classify existing BOP/Terralith/TerraBlender/Lithostitched biomes; do not replace their biome source.
- Runtime linked build still remains a release gate until executed in a dependency-capable environment.

## v2.5 society feedback
- `SocietyDiagnostics` is now the single source of truth for food, housing, safety, employment and goods needs.
- Dashboard protocol v8 exposes satisfaction and dominant pressure per settlement.
- Dedicated Society tab makes unrest/prosperity causes visible to the player.
- Integrated-client dashboard network registration version is 5.

## v2.6 military command completion
- Military objectives are authoritative commands, not decorative records.
- Capture/siege/defend/retreat/patrol/raid/escort movement paths are handled by FactionEngine.
- Stale offensive orders are invalidated after peace or ownership changes.
- Dashboard protocol v9 adds bounded objective/siege telemetry; War tab renders it.
- Integrated-client dashboard network registration version is 6.

## v2.7 settings/release ergonomics
- SimulationConfig is now world-persistent under schema 9.
- Performance/Balanced/Immersive/Cinematic profiles are canonical presets, not client-only toggles.
- Dashboard protocol v10 includes settings telemetry; network registration version is 7.
- Settings actions work outside faction jurisdiction and immediately dirty the integrated-server save.
- Dashboard tab buttons wrap into bounded rows for smaller windows.

## v2.8 faction interaction
- Singleplayer dashboard can join the current uncontested realm or leave membership.
- Canonical authorization prevents spoofed remote-faction joins and reuses reputation/wanted/custody gates.
- Internal payload registration version is 8.

## v2.9 realm management
- Save schema **10** adds persistent per-settlement development policy.
- Policies: Balanced, Food, Housing, Industry, Defense; they change real construction ordering rather than only UI labels.
- Faction members can raise/lower their own realm tax rate by 1 percentage point per action; remote-faction spoofing is rejected.
- Settlement policy actions are ownership-validated against canonical membership state.
- Dashboard protocol **v11** exposes development policy; integrated-client payload registration version is **9**.


## v2.9.1 runtime-profile hardening
- Persisted SimulationConfig now drives wildlife physical/regional radii and entity budget at runtime.
- Caravan projection now uses the tested CaravanMaterializationPlanner and configured physical radius/max caravan cap; hardcoded 320/420 radii were removed.
- Aircraft projection radius/budget and naval visual horizon are derived through a shared RuntimeProjectionPolicy.
- Citizen projection budget is derived consistently from the selected singleplayer performance profile.
- `strategicDaysPerStep` now actually controls aggregate days advanced by the integrated-server daily tick.
- Release audit fails if these runtime settings silently regress to defaults/hardcoded constants.
- Headless core suite and release audit green after changes.


## v2.9.3 — target-pack refresh + real container theft
- Target-pack compatibility catalog expanded to the full requested singleplayer pack.
- Iron's Lib is included in the target pack.
- Create Deep Seas and Create Aeronautics are explicitly excluded.
- Guns++/`mr_guns` registry items are explicitly excluded from faction/military equipment discovery.
- Faction-owned block containers now use server-side open/close inventory deltas. Looking is not theft; only net removed items count.
- Traveler's Backpack blocks are excluded from faction-property theft tracking.
- Theft still requires a valid witness before bounty/notoriety is registered.
- Own-faction property access is authorized and cannot produce a theft crime.

## v2.9.3 target-pack + property crime hardening
- Iron's Lib is part of the target pack; Create Deep Seas and Create Aeronautics are explicitly excluded.
- Guns++/`mr_guns` items are denied by the registry equipment bridge for all NPC/guard/military loadouts.
- Registry-only compatible content integration lets faction guards visibly use eligible RPG/armor equipment and faction decoration use eligible building-mod blocks without linking foreign API internals.
- Keeps, warehouses, markets and barracks contain authored `STORAGE` slots materialized as faction barrels.
- Faction storage is stocked by converting bounded canonical stockpile resources into persistent physical items; successful insertion removes the same amount from canonical stock.
- Container theft tracks only authored Living Realms storage positions. Player-placed chests, portable inventories and Traveler's Backpack are not silently claimed.
- Witnessed removal from foreign faction storage flows through property crime -> jurisdiction wanted state -> bounty/heat. Unwitnessed theft remains unofficial.
- Full core suite, exact 3650-day soak, projection stress test and release audit are green after these changes.


## v2.9.4 release-gate hardening
- Long-run deterministic soak is exactly **3650 days**, with mandatory persistence/invariant checkpoints on day 30, 365 and 3650.
- New `ProjectionStressTest` floods wildlife, caravan, citizen, military, aircraft and naval planners and proves their global caps plus unique projection identities.
- Wildlife reconciliation additionally proves duplicate, over-budget and orphan entities are removed deterministically.
- Release audit now fails if either the 3650-day gate or projection-stress gate is removed from `test-core.sh`.


## v2.9.5 species/save startup integrity
- Canonical SavedData cannot be opened until the server datapack species catalog has completed its reload/install phase.
- The binary save decoder now rejects any live population whose species definition is missing from the supplied catalog instead of creating a latent null-species state.
- Species pack regression explicitly proves a 99-species save cannot be reopened using only the 20-species starter fallback.


## v2.9.6 Create projection consolidation
- Removed the second overlapping industry block writer; only `IndustrialSiteMaterializer` may project canonical industrial sites.
- One bounded layout now carries the machine, casing, shaft, cogwheel, gearbox, optional basin/secondary machine and operational status lamp.
- Industry projection radius follows the persisted singleplayer runtime profile instead of a separate hardcoded activation constant.
- Canonical production remains off-screen in `IndustryEngine`; Create blocks remain a loaded-chunk physical projection, so chunk loading never controls the economy.


## v2.9.7 visible target-pack integration
- Compatible building/content mods can now supply deterministic faction FOUNDATION/FLOOR/WALL/BEAM/ROOF/GLASS/FENCE/PATH/LIGHT palette blocks when their registered blocks are placement-safe.
- Foreign block entities remain excluded from automatic palette discovery.
- RPG/ranged/magic equipment discovery recognizes a broader weapon family while `mr_guns` and GamingBarn gun namespaces remain denied.
- Integration remains registry-only: no renderer/performance/worldgen internals are linked or overwritten.
- Release audit now fails if structural pack palettes or RPG equipment discovery silently regress.


## v2.9.9 physical player market
- Player market quotes are scarcity-driven and state-bound to stockpile + treasury snapshots, preventing replay/double-commit even when rounded prices stay unchanged.
- Dashboard protocol v12 exposes server-authoritative unit prices plus exact buy cost / sell payout for fixed 8-unit packages.
- Market BUY/SELL actions are validated on the integrated server and require proximity to a completed Living Realms market.
- Physical trades use emeralds plus vanilla commodity items; successful buys remove canonical realm stock and credit treasury, while sells add canonical stock and debit treasury.
- Custody, contested territory, insufficient stock/treasury, insufficient items/emeralds and inventory capacity are all explicit rejection paths.
- Network registration version is 10.

## v2.9.9 runtime API hardening
- Corrected the NeoForge 1.21.1 ClientTickEvent import to `net.neoforged.neoforge.client.event.ClientTickEvent`; release audit now rejects the later/wrong package.
- Replaced the later-version `DeferredRegister.Entities/createEntities` helper with the NeoForge 1.21.1-safe generic `DeferredRegister<EntityType<?>>` targeting `Registries.ENTITY_TYPE`.
- Verified NeoForge 1.21.1 reload listener and payload registrar patterns against version-specific API documentation.
- Fixed all seven EntityType.Builder registrations to use the 1.21.1 `build(String)` signature; the unavailable ResourceKey overload is now forbidden by release audit.
- Re-ran core deterministic, species, strategic, projection-stress, 3650-day soak and release-audit gates successfully.

## v3.0.0-RC2 promotion
- Promoted from v2.9.9 after network-interruption recovery.
- Includes visible target-pack palette/equipment integration, physical player-market, and NeoForge 1.21.1 API hardening from v2.9.7-v2.9.9.
- Offline singleplayer remains the sole target; no LLM bridge or dedicated multiplayer support.

## v3.0.0-RC3 API hardening
- Replaced illegal direct `Slot.container` access with public `Slot.isSameInventory(...)` for Minecraft 1.21.1.
- Corrected three construction runtime imports to `net.minecraft.world.level.levelgen.Heightmap`.
- Release audit now rejects both regressions.
- RC3 remains a release candidate until a dependency-linked NeoForge/Create build and in-game boot smoke test succeed.

## v3.0.0-RC3 save/release hardening checkpoint
- Added a mandatory save-migration matrix covering every supported binary schema. Schemas 1-2 remain fixed historical fixtures; schemas 3-10 are independently versioned compatibility fixtures with field-level assertions.
- Migration coverage now exercises legacy ecology-center derivation, v4 physiology/government/society, shipments, treaties, custody, player standing, ports, industry and persisted simulation config.
- Added save-integrity regression coverage for deterministic byte encoding, bad magic, unsupported schemas, bounded-count corruption, truncation and trailing payload data.
- `SimulationStateCodec` now rejects trailing bytes after a complete save frame instead of silently accepting appended/corrupt data.
- Linux and Windows production build paths both include the migration/integrity gates; Linux now runs all headless gates and the release source audit before attempting the network-dependent Gradle bootstrap.
- Full headless core suite and release audit are green after these changes.
- The full NeoForge/Create linked build is still unresolved in this sandbox because external DNS/download access for the Gradle bootstrap is unavailable; this is an environment block, not a reproduced source compile failure.

## RC4 buildfix4 — container-theft bridge compatibility
- Reworked faction-storage theft/stock bridging to operate on the authored barrel's real `Container` block entity rather than inspecting `AbstractContainerMenu` slot backing fields.
- Removes direct `Slot.container` access and avoids assumptions about player-slot layout in vanilla/modded menus.
- Uses the public Minecraft 1.21.1 `Container` API for bounded insertion, counting and theft snapshots.
- Local release audit and the full core/3650-day suite pass after the change.
- Full NeoForge/Create linked compile remains the next external gate.

## RC4 buildfix5 — dashboard lambda capture compatibility
- Fixed two dashboard button loops that captured the mutable loop index inside Java lambdas.
- Each button now captures immutable per-iteration action/label values, preserving the intended server action and settlement target.
- Scanned the main source tree for the same same-line loop/lambda capture pattern; no additional matches remain.
- Full local core/3650-day suite and release source audit remain green after the change.
- The dependency-linked NeoForge/Create compile is the next gate to re-run on the Windows build host.

### Buildfix 6
- Fixed non-effectively-final `nearest` capture in `LivingRealmsEvents.onBlockBreak`; captures immutable `nearestSite` before stream filtering.
- Local core gates and release audit pass. Full linked NeoForge/Create JAR build still requires an environment with dependency-network access.
