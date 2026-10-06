# Living Realms project state

CURRENT PINS: schema 21 / minSchema 1 / protocol 20 / network 16 / contentRevision 18 / starterSettlements 204-300 / perRealm 17-25 / capitalSpacing 3000-4500 / roleAwareSpacing

## Current production state
- Twelve surface kingdoms each seed 1 capital + 10 authored satellites + 6–14 rural hamlets: **17–25 settlements per realm**, **204–300 surface starter settlements** total. Wizard Trees remain a separate SPECIAL underground layer.
- Settlement placement uses `SettlementSpacingPolicy`: capital↔capital preferred **3000–4500**, capital↔city **1600–2600**, capital↔town **650–1200**, town↔village **350–650**, village↔hamlet **180–350**, hamlet↔hamlet **150–300**; SPECIAL sites use their own local bands.
- Save schema **21**, dashboard protocol **20**, network registration **16**, outer `ContentRevision=18`. Current production development assumes a fresh world per update; old physical-layout migration is not a release target.
- Named citizen rosters seed per settlement (hamlet 6 … capital 64) independently of chunk projection; aggregate population remains the demographic scale.
- Fresh-world starter settlements, regional roads, roadside anchors and Wizard Trees baseline are authored during true chunk worldgen. Their canonical completion uses `WORLDGEN` receipts; runtime materializers remain for later growth/evolution and explicit foreign adoption.
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
- Earlier waves used schema 16–18, ContentRevision 10–17, denser layouts and older protocol pins. Those values are historical only; current development validates newly created worlds.
- Dashboard protocol historically climbed through v5–v19 before the player-structure / War Room bump to protocol 20.
