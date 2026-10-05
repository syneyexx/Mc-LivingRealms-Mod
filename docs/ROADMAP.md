# Living Realms delivery roadmap

CURRENT PINS: schema 18 / minSchema 1 / protocol 19 / network 15 / contentRevision 14 / surfaceSettlements 156 / perRealm 13 / spacing 800

## Product scope
- [x] Offline singleplayer only; integrated server authoritative.
- [x] No dedicated multiplayer; no LLM.
- [x] Minecraft 1.21.1 + NeoForge 21.1.219 + Create 6.0.10 + Java 21.

## v3.0 — release-ready singleplayer
- [x] Deterministic civilization/ecology core.
- [x] Versioned persistent world state through schema **18** (schemas 1–17 readable).
- [x] Dashboard protocol **18**, network **14**, ContentRevision **14**.
- [x] Surface density: 12 kingdoms × 13 settlements (capital + 10 Specs + 2 rural) = **156**, spacing **800**, plus Wizard Trees (3).
- [x] Goods chain GRAIN/FLOUR/BREAD/MEAT/ALE/WOOL; construction keys only via materializer / FOREIGN_ADOPTED.
- [x] SettlementTransfer on capture/rebellion; capital war targets; named rosters; tokenized dialogue.
- [x] 3650-day soak, projection stress, save fuzz, release-audit, GameTests.

## Geschiedenis
- Roadmap previously pinned schema 16–17, protocol 14–17, 216/380+ settlements, and 2000-block spacing.
