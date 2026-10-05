# Living Realms project state

CURRENT PINS: schema 19 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

## Current production state
- Twelve surface kingdoms each seed capital + 1 authored satellite + 1 rural hamlet (`TARGET_SETTLEMENTS_PER_REALM=3` → `SURFACE_STARTER_SETTLEMENTS=36`), plus Wizard Trees (3 colonies, excluded from surface density).
- Minimum settlement clearance is **2000** blocks (shared by densifier, causal expansion, foreign adoption, and player founding).
- Save schema **19** adds settlement provenance/anchors, outlying sites, road life, registered player structures (schemas 1–18 remain readable; schema 18 goods chain retained). Dashboard protocol **20**. Network registration **16**. Outer `ContentRevision=15` (sparse 2000 policy for new worlds; legacy dense saves preserved in place).
- Named citizen rosters seed per settlement (hamlet 6 … capital 64) independently of chunk projection; aggregate population remains the demographic scale.
- Construction completion keys are written only by the materializer or explicit `FOREIGN_ADOPTED` adoption; economy production counts only materialized (or receipt-backed) keys.
- Capture and rebellion use `SettlementTransfer` so citizens, claims, industry, ports and related ownership follow the new faction.
- War discovery targets the capital (highest tier → population → oldest id) with typed war goals.
- Release suite: `scripts/core-tests.list` → `./scripts/test-core.sh`; production runners emit `RELEASE_MANIFEST.json`.

## Verified core gates
- Java 21 core compiles with `-Xlint:all -Werror`.
- 365-day deterministic replay and 3650-day soak with checkpoints at day 30 / 365 / 3650.
- Species pack audit: 134 species.
- Mandatory gates include density, goods chain, migration order, settlement transfer, war goals, dialogue tokens, roster-without-projection, siege breach, and documentation pins.

## Explicit status claim
**RELEASE-READY** — pins above are authoritative; historical notes live under Geschiedenis.

## Geschiedenis
- Earlier waves used schema 16–18, ContentRevision 10–14, denser layouts (including a brief 156/800 regression), and older protocol pins. Those values are obsolete for current status except as migrated LEGACY settlements.
- Dashboard protocol historically climbed through v5–v19 before the player-structure / War Room bump to protocol 20.
