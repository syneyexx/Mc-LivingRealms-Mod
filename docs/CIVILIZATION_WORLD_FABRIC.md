# Civilization World Fabric

CURRENT PINS: schema 21 / minSchema 1 / protocol 20 / network 16 / contentRevision 16 / starterSettlements 204-300 / perRealm 17-25 / capitalSpacing 3000-4500 / roleAwareSpacing

This document is the current architecture contract for the civilization/world-fabric branch. Source and executable tests remain authoritative if this document ever drifts.

## 1. Canonical settlement hierarchy

Ordinary surface settlements use explicit roles:

- `CAPITAL`
- `CITY`
- `TOWN`
- `VILLAGE`
- `HAMLET`
- `SPECIAL` for sites that must not behave like ordinary settlement graph nodes.

Fresh surface realms seed:

- 1 capital;
- 10 authored satellites;
- 2–4 of those satellites as towns;
- 6–8 of those satellites as villages;
- 6–14 additional rural hamlets.

That yields **17–25 settlements per surface realm** and **204–300 surface starter settlements** across twelve realms. Wizard Trees remain a separate SPECIAL underground layer.

Existing anchored worlds are not wholesale relocated to satisfy the fresh-world layout. Schema/content migration preserves canonical positions and applies role/state migration without teleporting established settlements.

## 2. Role-aware spacing

`SettlementSpacingPolicy` is the single placement policy for ordinary settlement hierarchy. Minimum distances prevent collisions; preferred ranges guide placement.

| Pair | Preferred range | Exclusion floor |
|---|---:|---:|
| Capital ↔ Capital | 3000–4500 | 2500 |
| Capital ↔ City | 1600–2600 | 1600 |
| Capital ↔ Town | 650–1200 | 650 |
| Capital ↔ Village | 450–900 | 300 |
| Capital ↔ Hamlet | 250–700 | 180 |
| City ↔ City | — | 1600 |
| City ↔ Town | — | 650 |
| City ↔ Village | — | 300 |
| City ↔ Hamlet | — | 180 |
| Town ↔ Town | 700–1300 | 650 |
| Town ↔ Village | 350–650 | 300 |
| Town ↔ Hamlet | 220–500 | 180 |
| Village ↔ Village | 300–600 | 280 |
| Village ↔ Hamlet | 180–350 | 160 |
| Hamlet ↔ Hamlet | 150–300 | 150 |

SPECIAL sites use context-specific placement rather than pretending to be ordinary villages. Examples include refugee camps, Wizard Trees, foreign outlying sites, pirate/bandit sites and roadside sites.

## 2b. Civilization gap filling

Minimum spacing is only half of the placement contract. Fresh inhabited realms also prevent accidental empty corridors.

- Every ordinary starter settlement has another true ordinary settlement within roughly **800 blocks**.
- Each fresh realm receives an inner capital→town anchor in the **650–800** band.
- Authored villages are distributed across starter towns so one town does not monopolize the local network.
- Operational ROAD/CARAVAN corridors longer than 450 blocks receive deterministic non-settlement roadside anchors.
- Roadside anchors target roughly **380-block** spacing and the regression gate requires no ordinary inhabited road-fabric gap above **450 blocks**.
- Independent roadside sites retain a **160-block** minimum separation, so the result is inhabited countryside rather than continuous development.
- All corridor reconciliation is canonical/idempotent and bounded; physical roadside blocks still materialize only when their chunks are loaded.

## 3. Causal world population

Player proximity is not a settlement generator. `SettlementExpansionEngine.ensureNear(...)` is intentionally a no-op.

New canonical settlements arise from causal pressure such as population/housing stress, surplus, strategy and authored expansion catalog availability. Founding checks the same role-aware spacing policy before creation.

Foreign structures are classified rather than blindly promoted to settlements:

- bind to an existing physical footprint when already represented;
- become a `FOREIGN_ADOPTED` settlement only when role-pair spacing permits it;
- otherwise become an `OutlyingSite` attached to a host settlement.

## 4. Graph-first settlement streets

`SettlementStreetGraph` is the source of truth for settlement road topology.

- `SettlementRoadPlanner` authors graph segments.
- Parcel planning consumes graph frontage.
- Housing is placed on legal graph-derived parcels.
- Housing shortages extend the graph with residential side streets.
- ROAD construction intents are projections of graph segments, not an independent road generator.
- Append-only growth tests protect already-finished geometry from moving when population/tier grows.

## 5. CITY+ boundary and gates

`SettlementBoundary` owns CITY+ defensive topology.

- The perimeter is one coherent closed boundary.
- Wall runs cover the perimeter except explicit gate openings.
- Gates are stable deterministic nodes.
- Every gate has a graph-authored approach segment passing through the opening.
- Inter-settlement transport uses `gateToward(...)` so regional roads terminate at the destination-facing city gate rather than cutting through curtain walls.
- Housing growth must not move finished gates.

## 6. Core completeness before optional detail

`SettlementCoreCompleteness` defines a tier-specific recognizable physical core.

Examples:

- camps require a minimal path and shelter;
- hamlets add food/water;
- villages add market identity;
- towns add plaza/government;
- cities/metropolises require four gates, boundary fabric and an outward gate road.

Construction discovery prioritizes pending intents that close active core deficits before optional/detail intents while preserving normal priority/key ordering inside each class.

Completion truth remains receipt-backed. The completeness evaluator does not create a second construction authority.

## 7. Loaded-chunk world fabric

Persistent civilization blocks are **loaded-chunk driven**, not player-distance driven.

Core domains include:

- settlement structures/streets;
- inter-settlement roads/rail;
- urban core;
- roadside sites;
- historical ruins/caches/pirate hideouts;
- industrial sites;
- canonical outlying sites.

Materializers:

- never force-load chunks;
- mutate only when the relevant chunk/column is already available;
- use typed `AuthoredBlockLedger` provenance and `WorldMutationGuard`;
- do not adopt arbitrary player blocks as Living Realms ownership;
- use bounded work/fairness cursors so one site category cannot permanently starve another.

Player proximity may still control **presentation** such as particles or entities. It is not block-existence authority.

## 8. Entity LOD reconciliation

Canonical state is independent from Minecraft entity count.

Full entity projections use per-domain physical envelopes:

- citizens use `citizenRadiusBlocks`;
- military, caravans and wildlife use their configured physical envelope;
- migrations use `migrationRadiusBlocks`;
- fleets use `navalRadiusBlocks`.

`RegionalImpostorPlanner.LodBands` gives each impostor kind its matching physical cutoff. A regional impostor is allowed only outside the corresponding full-entity envelope and inside the regional radius.

This prevents simultaneous duplicate representation such as:

- full citizens + settlement-bustle impostor;
- full army + military banner;
- full caravan + caravan dust;
- full herd + herd token;
- full ship + sail;
- full migration entities + migration token.

Beyond the regional radius, state remains abstract/canonical.

## 9. Persistence

Schema 21 persists settlement roles and migrates legacy settlement records into the explicit hierarchy. Schemas 1–20 remain decoder-supported.

The schema migration does not justify relocating established settlements. Fresh-world density policy and legacy-world preservation are separate concerns.

## 10. Regression gates

The branch carries focused proof for the architecture, including:

- `LivingWorldDensityTest`
- `ForeignSettlementSpacingTest`
- `RegionalSettlementGraphTest`
- `SettlementStreetGraphTest`
- `SettlementBoundaryTest`
- `SettlementCoreCompletenessTest`
- `WorldFabricChunkPolicyTest`
- `SpecialSiteWorldFabricTest`
- `SpecialSiteSpacingTest`
- `RegionalImpostorPlannerTest`
- save migration/integrity/fuzz gates
- `DocumentationPinTest`
- the full ordered `scripts/core-tests.list`
- `scripts/release-audit.py`

A branch is not release-green merely because these tests exist. The release-state vocabulary in `docs/RELEASE_GATES.md` remains authoritative and requires actual CI/runtime evidence.
