# Architecture depth pass — status

Branch: `cursor/architecture-depth-pass-f4a7`  
Pins: schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

Final checkpoint for this modular-monolith pass. Feature maturity ≠ release readiness (`docs/RELEASE_GATES.md`).

## Waves completed (this pass)

| Wave | Theme | Status |
|---|---|---|
| 0–1 | Inventory + baseline (`ARCHITECTURE_INVENTORY.md`, `BASELINE_REPORT.md`) | Done |
| 2 | Dashboard section codecs / panels (snapshot-only client) | Largely done |
| 3–4 | Construction truth + parcel `ResolvedBuildSite` / access gates | Largely done |
| 5–7 | SimulationEngine, lifecycle domain engines, domain persistence codecs, integrity repair | Done |
| Final | `DemographyEngine` extraction for settlement attraction, aggregate births/deaths, named mortality | Done |
| Final+ | `SettlementRoadPlanner` + `SettlementHousingPlanner`; `PhysicalLossService` + `PlayerLawService` | Done |
| Final+ | `LivingRealmsCommands` + `ProjectionEntityLifecycleBridge` extracted from `LivingRealmsEvents` | Done |
| Final+ | `ConstructionBlockApplier` extracted from `SettlementConstructionMaterializer` | Done |
| 8–9 / 15.1 / 31 | Data-driven realm / culture / building content loaders | Done |
| 10 | Parcel-bound build sites + access-gated completion | Done |
| 11 | Dialogue interpreter / knowledge / planner / style / realizer | Done |
| 12–14 | Agency feedback loops, underworld board UI, war room surface | Done |
| 23 / 40 | Documentation truth: maturity levels replace blanket COMPLETE | Done |
| 25 | Focused domain tests (scheduler, integrity, loaders, dialogue, migration, epidemic) | Done |
| 26 | Behavior-preserving refactor proof (`DeterministicRefactorProofTest`) | Done |
| 28 | Memory pressure / bounded collections (`StateRetentionCompactor` monthly/quarterly) | Done |
| 29 | Save size audit (`SaveSizeAuditor` day 0/365/3650) | Done |
| 30 | Stronger cultural identity (`CulturalNaming` + dialogue/festival/district wiring) | Done |
| 33 | Historic city evolution (append-only completed keys across tier growth) | Done |
| 46 | Development modes (`DevelopmentModeGuard` PLAYER_LED/HYBRID/AUTO) | Done |

## Deferred follow-up (not required to close this pass)

| Theme | Honest note |
|---|---|
| Further hotspot shrink | All four original residual hotspots were reduced in Final+: planner roads/housing, state physical/law bridges, event commands/projection lifecycle, and materializer block application. Further peeling is optional follow-up and must be justified by proof tests rather than LOC alone. |
| Household/family split | Aggregate demography is now extracted; household formation remains in `CivilizationEngine` while household lifecycle/births live in `HouseholdLifecycleEngine`. This is a maintainability follow-up, not a correctness blocker. |
| Linked / GameTest / runtime smoke | Must be taken from CI / real runtime evidence; headless core alone cannot claim them. |
| Polish | Some player loops are CONSEQUENCE WIRED / DEEP without POLISHED UX. |

The architecture pass is complete for PR #19: the major god-object edges targeted by this pass are split, persistence/schema pins are preserved, and the final report documents residual hotspots instead of claiming they disappeared.

## Documentation maturity vocabulary

FOUNDATION → CANONICAL → PLAYABLE → PHYSICALIZED → DEEP → POLISHED  
Applied in `COMPLETION_MATRIX.md`, `DETAIL_MATRIX.md`, `WAVE0_SYSTEM_INVENTORY.md`, and cross-linked from project state / roadmap / readiness docs.

## Proof gates for this checkpoint

- `DocumentationPinTest` must PASS with exact CURRENT PINS line.
- `DeterministicRefactorProofTest` locks day-30 / day-365 count bands for known seed.
- Full `./scripts/test-core.sh` EXIT=0.
- Wave 28–46 proof: `StateRetentionCompactorTest`, `SaveSizeAuditorTest`, `CulturalIdentityWiringTest`, `HistoricCityEvolutionTest`, `DevelopmentModeGuardTest`.
- Demography extraction proof: `ArchitectureLifecycleEnginesTest` directly exercises `DemographyEngine`.
