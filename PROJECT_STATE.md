# Living Realms project state

CURRENT PINS: schema 21 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / starterSettlements 204-300 / perRealm 17-25 / capitalSpacing 3000-4500 / roleAwareSpacing

## Current production state
- Twelve surface kingdoms each seed 1 capital + 10 authored satellites + 6–14 rural hamlets: **17–25 settlements per realm**, **204–300 surface starter settlements** total. Wizard Trees remain a separate SPECIAL underground layer.
- Settlement placement uses `SettlementSpacingPolicy`: capital↔capital preferred **3000–4500**, capital↔city **1600–2600**, capital↔town **650–1200**, town↔village **350–650**, village↔hamlet **180–350**, hamlet↔hamlet **150–300**; SPECIAL sites use their own local bands.
- Save schema **21** adds underworld contracts + stolen-goods ledger (schemas 1–21 remain readable; schema 19 provenance/sites retained). Dashboard protocol **20**. Network registration **16**. Outer `ContentRevision=15`; schema 21 adds settlement-role persistence/migration while anchored legacy settlements remain in place.
- Named citizen rosters seed per settlement (hamlet 6 … capital 64) independently of chunk projection; aggregate population remains the demographic scale.
- Construction completion keys are written only by the materializer or explicit `FOREIGN_ADOPTED` adoption; economy production counts only materialized (or receipt-backed) keys.
- Capture and rebellion use `SettlementTransfer` so citizens, claims, industry, ports and related ownership follow the new faction.
- War discovery targets the capital (highest tier → population → oldest id) with typed war goals.
- Release suite: `scripts/core-tests.list` → `./scripts/test-core.sh`; production runners emit `RELEASE_MANIFEST.json`.

## Architecture depth pass (modular monolith)
See `docs/ARCHITECTURE.md` and `docs/ARCHITECTURE_PASS_STATUS.md`. Extracted kernels so far:
- **Simulation kernel** — `SimulationEngine` day orchestration; lifecycle domain engines (migration, epidemic, household, dynasty, …).
- **Projection kernel** — LOD planners, materializers, runtime budget / `RuntimeScheduler`.
- **Construction kernel** — planner → blueprint → parcel `ResolvedBuildSite` → materializer completion.
- **Persistence** — `SimulationStateCodec` envelope + domain codecs under `sim/persistence/codec/`.
- **Dialogue** — interpreter / knowledge / planner / style / realizer split.
- **Content** — data-driven realm / culture / building loaders.

Subsystem maturity (not release readiness) lives in `COMPLETION_MATRIX.md` using FOUNDATION / CANONICAL / PLAYABLE / PHYSICALIZED / DEEP / POLISHED.

## Verified core gates
- Java 21 core compiles with `-Xlint:all -Werror`.
- 365-day deterministic replay and 3650-day soak with checkpoints at day 30 / 365 / 3650.
- Species pack audit: 134 species.
- Mandatory gates include density, goods chain, migration order, settlement transfer, war goals, dialogue tokens, roster-without-projection, siege breach, documentation pins, and deterministic refactor proof.

## Explicit status claim
**core-green candidate** when `./scripts/test-core.sh` + `release-audit.py` pass for these pins.
Claim `linked-build-green` / `gametest-green` / `runtime-smoke-green` / `release-ready` only with matching evidence (see `docs/RELEASE_GATES.md`). Do not treat headless core alone as release-ready.

## Geschiedenis
- Earlier waves used schema 16–18, ContentRevision 10–14, denser layouts (including a brief 156/800 regression), and older protocol pins. Those values are obsolete for current status except as migrated LEGACY settlements.
- Dashboard protocol historically climbed through v5–v19 before the player-structure / War Room bump to protocol 20.
