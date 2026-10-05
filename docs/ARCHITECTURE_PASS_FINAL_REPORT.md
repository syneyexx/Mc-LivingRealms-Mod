# Architecture depth pass — final report (scaffold)

Branch: `cursor/architecture-depth-pass-f4a7`  
Status: **draft scaffold** — fill sections after Wave 23/25/26 gates are green and remaining hotspot work is either done or explicitly deferred.

CURRENT PINS: schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

## 1. Intent

Convert Living Realms from a few god-classes into a **modular monolith**: Simulation / Projection / Construction kernels, Runtime Scheduler, Persistence domain codecs, Dialogue pipeline, Content loaders — without changing schema 20 semantics or player-visible authority rules.

## 2. Baseline

- Inventory: `docs/ARCHITECTURE_INVENTORY.md`
- Frozen baseline: `docs/BASELINE_REPORT.md` (HEAD `2180af6` at inventory start)
- Pass status: `docs/ARCHITECTURE_PASS_STATUS.md`

## 3. Extractions kernels (fill with class lists)

| Kernel | Extracted modules | Still oversized |
|---|---|---|
| Simulation | `SimulationEngine`, lifecycle engines (migration, epidemic, household, …) | `CivilizationEngine` demography helpers; `SimulationState` |
| Projection | budget / LOD planners | materializer + event hub |
| Construction | `ResolvedBuildSite`, access validators, building templates | `SettlementPlanner`, `SettlementConstructionMaterializer` |
| Runtime | `RuntimeBudgetController`, defer tracker | NeoForge tick bridge thickness |
| Persistence | domain codecs under `sim/persistence/codec/` | codec envelope still central |
| Dialogue | interpreter / knowledge / planner / style / realizer | façade size |
| Content | realm / culture / building loaders | — |
| UI | dashboard section codecs + panels | screen façade |

## 4. Behavior preservation

- Fixture: `DeterministicRefactorProofTest` seed `0xA4C417EC7F00D26L`
- Checkpoints: day 30 and day 365 count bands (factions, settlements, population band, wars, routes)
- Golden values documented in that test; day-365 people/routes refreshed after Wave 16/21 hooks (commented in test)

## 5. Documentation truth

- Maturity levels replace blanket COMPLETE: FOUNDATION / CANONICAL / PLAYABLE / PHYSICALIZED / DEEP / POLISHED
- Player agency experience matrix: `docs/PLAYER_EXPERIENCE_MATRIX.md`
- Release claims remain `docs/RELEASE_GATES.md` only

## 6. Evidence checklist (to complete)

- [ ] `DocumentationPinTest` PASS
- [ ] `./scripts/test-core.sh` EXIT=0
- [ ] `DeterministicRefactorProofTest` PASS
- [ ] Note linked-build / GameTest / runtime-smoke separately if run
- [ ] List deferred hotspots with reasons

## 7. Verdict (fill last)

_Pending suite evidence._
