# Living Realms delivery roadmap

CURRENT PINS: schema 21 / minSchema 1 / protocol 20 / network 16 / contentRevision 18 / starterSettlements 204-300 / perRealm 17-25 / capitalSpacing 3000-4500 / roleAwareSpacing

## Product scope
- [x] Offline singleplayer only; integrated server authoritative.
- [x] No dedicated multiplayer; no LLM.
- [x] Minecraft 1.21.1 + NeoForge 21.1.219 + Create 6.0.10 + Java 21.

## Maturity vocabulary
Feature rows track depth with FOUNDATION → CANONICAL → PLAYABLE → PHYSICALIZED → DEEP → POLISHED (see `COMPLETION_MATRIX.md`). Release claims use `docs/RELEASE_GATES.md` vocabulary only.

## v3.0 — release-ready singleplayer
- [x] Deterministic civilization/ecology core (**DEEP** / soak-proven).
- [x] Versioned persistent world state through schema **21** (schemas 1–21 readable).
- [x] Dashboard protocol **20**, network **16**, ContentRevision **16**.
- [x] Surface density: 12 kingdoms × **17–25** settlements = **204–300** starters (capital + 10 authored satellites + 6–14 rural hamlets), with role-aware spacing; Wizard Trees remain a separate SPECIAL layer.
- [x] Goods chain GRAIN/FLOUR/BREAD/MEAT/ALE/WOOL; construction keys only via materializer / FOREIGN_ADOPTED.
- [x] SettlementTransfer on capture/rebellion; capital war targets; named rosters; tokenized dialogue.
- [x] 3650-day soak, projection stress, save fuzz, release-audit, GameTests.
- [x] Architecture depth pass complete for this PR: modular-monolith extractions + demography extraction + final report; residual hotspots are explicitly deferred (see `docs/ARCHITECTURE_PASS_STATUS.md`).

## Architecture depth waves (summary)
Completed themes on this branch include runtime scheduler, SimulationEngine + lifecycle domain engines, domain persistence codecs, content loaders, dialogue pipeline split, construction parcel/access gates, and player agency surfaces (underworld / war room). Documentation truth (Wave 23/40), focused domain tests (Wave 25), and deterministic refactor proof (Wave 26) land with this checkpoint. This pass is closed with an honest residual-hotspot list. Further hotspot extraction and UX polish are follow-up work, not blockers for this architecture PR.

## Geschiedenis
- Roadmap previously pinned schema 16–17, protocol 14–17, 216/380+ settlements, and 2000-block spacing.
