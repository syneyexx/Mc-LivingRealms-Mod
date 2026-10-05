# Living Realms delivery roadmap

CURRENT PINS: schema 19 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

## Product scope
- [x] Offline singleplayer only; integrated server authoritative.
- [x] No dedicated multiplayer; no LLM.
- [x] Minecraft 1.21.1 + NeoForge 21.1.219 + Create 6.0.10 + Java 21.

## v3.0 — release-ready singleplayer
- [x] Deterministic civilization/ecology core.
- [x] Versioned persistent world state through schema **19** (schemas 1–18 readable).
- [x] Dashboard protocol **20**, network **16**, ContentRevision **15**.
- [x] Surface density: 12 kingdoms × 3 settlements (capital + 1 Spec + 1 rural) = **36**, spacing **2000**, plus Wizard Trees (3).
- [x] Goods chain GRAIN/FLOUR/BREAD/MEAT/ALE/WOOL; construction keys only via materializer / FOREIGN_ADOPTED.
- [x] SettlementTransfer on capture/rebellion; capital war targets; named rosters; tokenized dialogue.
- [x] 3650-day soak, projection stress, save fuzz, release-audit, GameTests.

## Geschiedenis
- Roadmap previously pinned schema 16–17, protocol 14–17, 216/380+ settlements, and 2000-block spacing.
