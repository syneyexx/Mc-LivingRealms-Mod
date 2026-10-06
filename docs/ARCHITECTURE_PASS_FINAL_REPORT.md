# Architecture depth pass — final report

Historical baseline branch: `cursor/architecture-depth-pass-f4a7`  
Status: **historical architecture-pass report**. Current production pins and world-fabric architecture are maintained on `codex/civilization-world-fabric`.

CURRENT PINS: schema 21 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / starterSettlements 204-300 / perRealm 17-25 / capitalSpacing 3000-4500 / roleAwareSpacing

---

## A Repository

| Item | Value |
|------|--------|
| Current branch | `codex/civilization-world-fabric` |
| Baseline HEAD (inventory start) | `2180af61be07146ba2fca1894d8489eaabe03e92` |
| Report HEAD | post-finalize commit on this branch (release-audit retarget + docs) |
| Working tree at evidence capture | clean after that commit |
| Dirty policy | schema 21/current world-fabric pins; no test disables |

---

## B Architecture

### Extracted domains

| Domain | Modules | Role |
|--------|---------|------|
| Simulation | `SimulationEngine`, lifecycle engines (household / demography / epidemic / migration / knowledge / dynasty), `FactionEngine`, civilization sub-engines, `PhysicalLossService`, `PlayerLawService` | Day pipeline orchestration; aggregate demography/named mortality are isolated; physical feedback and law/bounty orchestration are no longer implemented inline in `SimulationState` |
| Runtime scheduler | `LivingRealmsRuntimeScheduler`, `LivingRealmsRuntimeTaskCatalog`, `RuntimeTask`, `RuntimeDomain` / `RuntimePriority` / `RuntimeTaskClass` | Server tick catalog; `LivingRealmsEvents` only drives `runtimeScheduler.tick` |
| Budgets / pressure | `RuntimeBudgetController`, `ProjectionBudget`, `RuntimePressureBridge` | Lane caps + HEALTHY/SOFT/HARD degradation for physical projection |
| Failure isolation | `RuntimeFailureIsolator`, `StructuredErrorReporter` (waves 34/35) | Recoverable tasks disable; critical tasks do not swallow |
| Persistence | `SimulationStateCodec` envelope + `sim/persistence/codec/*` (Ecology, Faction, Society, …, `CodecIO`) | Schema 21 section delegates; strict UTF-8 in `CodecIO` |
| Dialogue | interpreter / knowledge / planner / style / realizer + `DialogueAnswerCatalog` | No-LLM grounded answers; engine façade ~121 LOC |
| Content | realm / culture / building / architecture loaders + registries | Data under `data/livingrealms/{realms,cultures,buildings,architecture}` |
| Public API | `dev.livingrealms.api` providers + lifecycle events (wave 21) | Immutable events from clean mutation sites |
| Compat adapters | Create / Waystones / Civilian NPC adapters (wave 22) | Hard Create dep preserved; optional mod isolation |

### Scheduler

`LivingRealmsEvents` registers NeoForge server tick → `LivingRealmsRuntimeScheduler.tick(server)`.  
Catalog schedules discovery, projection, construction, maintenance, and `advanceDays(config.strategicDaysPerStep())` with species-readiness gate in the scheduler.

### Persistence

Envelope owns MAGIC / schema gate / CRC / encode order. Domain codecs own section layouts. Species-missing rejection lives in `EcologyCodec`. Malformed UTF-8 uses `CodingErrorAction.REPORT` in `CodecIO`.

### Dialogue

Pipeline: `DialogueInterpreter` → knowledge/planner/style/realizer; answer bodies in `DialogueAnswerCatalog`. Context + `ASK_SOURCE` + `latestMemory` retained across engine + catalog.

### Content

12 realm JSON + hierarchical starter seeder → 204–300 surface starters / 17–25 per realm with role-aware spacing; culture packs; 8+ building archetypes; 134 species pack.

---

## C Large class reduction

Approximate LOC (baseline `2180af6` → current):

| Class | Old (baseline) | New | Responsibility now |
|-------|----------------|-----|--------------------|
| `LivingRealmsEvents` | ~678 | thin event router | Tick delegates to scheduler; commands moved to `LivingRealmsCommands`; entity join/leave + physical-loss forwarding moved to `ProjectionEntityLifecycleBridge` |
| `NaturalLanguageDialogueEngine` | ~215 | ~121 | Orchestrates pipeline; answers extracted |
| `SimulationStateCodec` | ~743 | ~118 | Envelope only; domains in `codec/` |
| `RealmDashboardScreen` | ~975 | thinner façade | Tab panels (e.g. `EconomyPanel`) own section UI |
| `SimulationState` | ~509 | reduced façade/store | Physical-loss and player-law orchestration extracted; canonical stores + engine ownership remain |
| `SettlementPlanner` | ~674 baseline / ~713 pre-Final+ | reduced orchestrator | Street topology moved to `SettlementRoadPlanner`; parcel housing moved to `SettlementHousingPlanner` |
| `SettlementConstructionMaterializer` | ~621 baseline / ~699 pre-Final+ | reduced runtime orchestrator | Provenance-aware block mutation, doors/beds, compatibility replacement moved to `ConstructionBlockApplier`; discovery/terrain/queue remain |

Net: god-tick and god-codec split; Final+ also splits planner roads/housing, physical/law state bridges, event commands/projection lifecycle, and construction block mutation. Remaining density is bounded orchestration/store code rather than the original mixed-responsibility god paths.

---

## D Performance

| Mechanism | Behavior |
|-----------|----------|
| Telemetry | `LivingRealmsRuntimePerf`, structured error reporter, save-size auditor soft/hard caps |
| Budgets | `ProjectionBudget` lanes (wildlife, caravans, citizens, military, ships, aircraft, journeys) from persisted `SimulationConfig` + player count |
| Degradation | `RuntimeBudgetController.Pressure` HEALTHY → SOFT → HARD scales lane caps; construction ops via `ProjectionBudget.constructionBlockOpsPerTick()` |
| Recoverable tasks | Waves 34/35: isolator disables flaky projection/maintenance without killing the tick |
| Retention | State retention compactor + raised day-3650 soft advisory (save-size still under hard cap in core audit) |

---

## E Data-driven content

- Realms: `RealmDefinitionLoader` + hierarchical seeder pins (204–300 / 17–25 / role-aware spacing)
- Cultures: `CultureDefinitionLoader` / registry → naming, architecture family, dialogue dialect
- Buildings: `BuildingDefinitionLoader` + `BuildingTemplateRegistry` (culture → family → generic)
- Architecture palettes: JSON-backed
- Species: datapack catalog ≥134; startup waits for `SpeciesDataRegistry.ready()`

---

## F Gameplay chains

Verified in core (not claiming polished UX for all):

- Historical traces (wave 16): battle/raid/grave/memorial from real war/raid IDs → materializer
- Agency loops: assistance, underworld accept/bribe/fence, war room escort cycle
- Goods / housing / development modes (wave 46 guard): PLAYER_LED blocks auto houses
- Road life, foreign adoption, market quotes from server snapshot only
- Culture wiring + historic city evolution across tier growth

---

## G Test results

Honest matrix for this finalize pass:

| Gate | Result |
|------|--------|
| `DocumentationPinTest` | PASS (pins line exact) |
| `./scripts/test-core.sh` | **EXIT=0** locally (includes compile `-Werror`, full `core-tests.list`, 3650-day soak) |
| `DeterministicRefactorProofTest` | PASS (seed `0xA4C417EC7F00D26`, day 30/365 goldens) |
| `python3 scripts/release-audit.py` | PASS after retarget to scheduler/adapters/codecs/panels |
| New arch tests | `RuntimeSchedulerTest`, `ApiLifecycleSmokeTest`, `ProjectionBudgetTest`, domain codec/integrity, dialogue planner/token, loaders — PASS in core list |
| Linked NeoForge/Create build | **Not run** in this finalize environment (CI `linked-build` depends on core; re-run after push) |
| GameTest (`runGameTestServer`) | **Not run** here |
| Save migration matrix (schemas 1–21) | PASS inside core |
| Long-run soak (3650 days) | **PASS** inside core (`LongRunSoakTest`, day=3650 persistence gates) |
| Runtime smoke (client) | Headless `RuntimeSmokeTest` PASS in core; **full client runtimeSmoke not claimed** |

---

## H Remaining limitations

- All four residual hotspots received a behavior-preserving Final+ split. `SettlementConstructionMaterializer` still owns queue/discovery/terrain orchestration, and `SimulationState` still owns canonical stores/engines; further reduction is optional follow-up rather than an unfinished pass
- Household-pair formation and several claims/raid helpers remain in `CivilizationEngine`; aggregate demography and named mortality are extracted to `DemographyEngine`
- Linked build + GameTest + client runtime smoke need CI/agent with NeoForge deps
- Some player loops are CONSEQUENCE WIRED / DEEP without POLISHED UX
- Release claims remain only in `docs/RELEASE_GATES.md` — maturity vocabulary ≠ ship gate

---

## Adversarial audit notes

| Lens | Finding |
|------|---------|
| **Architect** | Scheduler + domain codecs remove the worst god-class edges; `SimulationState` remains the blast-radius core. Public API (wave 21) is thin but correctly event-shaped. |
| **Perf** | Budget/pressure path is real (materializers use `ProjectionBudget.forPlayers`). Soft/hard degradation exists; no claim of multiplayer stress beyond core projection tests. |
| **Designer** | Content packs make realms/cultures/buildings editable without Java; PLAYER_LED guard prevents silent auto-housing. Historical traces improve legibility of war aftermath. |
| **World** | Pins hold: 204–300 surface starters / 17–25 per realm / role-aware spacing. Persistent settlement/road/special-site blocks are chunk-driven; entity impostors remain bounded by per-kind LOD cutoffs. |
| **Maintainer** | Release-audit previously asserted strings only in `LivingRealmsEvents` / monolithic codec / dashboard screen — that bitrotted after extractions. Audit now follows scheduler/adapters/`CodecIO`/`EconomyPanel` so CI matches architecture without weakening gates. |

---

## Verdict

Architecture depth pass delivered a **modular monolith** with the earlier modular-monolith guarantees preserved under current schema-21/world-fabric pins. The explicit demography extraction is complete; residual hotspots are documented follow-up work rather than hidden blockers. Core + release-audit are the hard gates for this finalize; linked/GameTest remain CI follow-through.
