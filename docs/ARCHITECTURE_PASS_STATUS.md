# Architecture depth pass — status

Branch: `cursor/architecture-depth-pass-f4a7`  
Pins: schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

Honest checkpoint for modular-monolith work. Feature maturity ≠ release readiness (`docs/RELEASE_GATES.md`).

## Waves completed (this pass)

| Wave | Theme | Status |
|---|---|---|
| 0–1 | Inventory + baseline (`ARCHITECTURE_INVENTORY.md`, `BASELINE_REPORT.md`) | Done |
| 2 | Dashboard section codecs / panels (snapshot-only client) | Done |
| 3–4 | Construction truth + parcel `ResolvedBuildSite` / access gates | Done |
| 5–7 | SimulationEngine, lifecycle domain engines, domain persistence codecs, integrity repair | Done |
| 8–9 / 15.1 / 31 | Data-driven realm / culture / building content loaders | Done |
| 10 | Parcel-bound build sites + access-gated completion | Done |
| 11 | Dialogue interpreter / knowledge / planner / style / realizer | Done |
| 12–14 | Agency feedback loops, underworld board UI, war room surface | Done |
| **16** | **Historical traces** — `HistoricalTraceEngine` battle/raid/grave/memorial from real war/raid IDs; materializer projects legend markers / cause-aware ruins | **Done** |
| **21** | **Public API** — `dev.livingrealms.api` providers + immutable lifecycle events from clean mutation sites | **Done** |
| **22** | **Compat adapters** — Create / Waystones / Civilian NPC behind `minecraft.compat.*`; Create hard dep preserved | **Done** |
| 23 / 40 | Documentation truth: maturity levels replace blanket COMPLETE | Done |
| 25 | Focused domain tests (scheduler, integrity, loaders, dialogue, migration, epidemic) | Done |
| 26 | Behavior-preserving refactor proof (`DeterministicRefactorProofTest`) | Done |
| 28 / 29 / 30 / 33 / 46 | Retention compaction, save-size audit, culture wiring, historic growth, `DevelopmentModeGuard` | Done |
| **34 / 35** | **Failure isolation** — `RuntimeFailureIsolator` + `StructuredErrorReporter`; recoverable tasks disable, critical do not swallow | **Done** |

## Remaining / in progress

| Theme | Honest note |
|---|---|
| Further hotspot shrink | `SettlementPlanner`, `SettlementConstructionMaterializer`, `SimulationState` still large; peel only with proof tests |
| Demography engine extract | Births/deaths still private in `CivilizationEngine`; MigrationEngine + EpidemicEngine already extracted |
| Final report | Filled in `docs/ARCHITECTURE_PASS_FINAL_REPORT.md` (sections A–H + adversarial notes) |
| Release-audit alignment | Retargeted to scheduler / adapters / domain codecs / EconomyPanel so architecture moves do not false-fail CI |
| Linked / GameTest / client runtime smoke | Not claimed from headless core alone — expect GitHub Actions after push |
| Polish | Some player loops are CONSEQUENCE WIRED / DEEP without POLISHED UX |

## Documentation maturity vocabulary

FOUNDATION → CANONICAL → PLAYABLE → PHYSICALIZED → DEEP → POLISHED  
Applied in `COMPLETION_MATRIX.md`, `DETAIL_MATRIX.md`, `WAVE0_SYSTEM_INVENTORY.md`, and cross-linked from project state / roadmap / readiness docs.

## Proof gates for this checkpoint

- `DocumentationPinTest` must PASS with exact CURRENT PINS line.
- `DeterministicRefactorProofTest` locks day-30 / day-365 count bands for known seed.
- Full `./scripts/test-core.sh` EXIT=0.
- `python3 scripts/release-audit.py` PASS (paths follow post-extraction layout).
