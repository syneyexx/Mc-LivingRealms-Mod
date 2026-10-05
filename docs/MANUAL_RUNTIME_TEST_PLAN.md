# Manual Runtime Acceptance Plan

Hand checklist after automated core suite, release-audit, and linked NeoForge build are green.
This is a catch-net — it does **not** replace automated tests.

CURRENT PINS: schema 20 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

## Environment
1. JAR from `./build-production.sh` / `build/libs`.
2. Minecraft 1.21.1 + NeoForge 21.1.219 + Create 6.0.10.
3. Fresh world + one migrated save.

## Smoke checklist

| # | Check | Pass criteria |
|---|-------|---------------|
| 1 | Mod loads | No crash; Living Realms in mod list |
| 2 | Density | 36 surface settlements + Wizard Trees; large wilderness gaps; Spec catalog preserved for causal expansion |
| 3 | Spacing | Founding needs 2000m clearance (HUD/found message) |
| 4 | F12 dashboard | Settlements show verified housing / deficit / development mode; goods chain visible |
| 5 | War Room | Ruler can declare war, order capture/siege/raid, petition peace |
| 6 | Player buildings | Found camp → register house → capacity rises; PLAYER_LED does not spam houses |
| 7 | M map | Loaded terrain ACTUAL; unloaded estimate/unknown — no fake sine hills |
| 8 | Dialogue | "welk koninkrijk is dit?" → faction, not ruler |
| 9 | Capture | After conquest, citizen names the new faction |
| 10 | Crime/underworld | Witnessed theft → wanted; contract completes only via matching crime |
| 11 | Wilderness travel | 2000+ block route: nature, sparse travelers/sites — not continuous suburbs |
| 12 | Market | Buy BREAD near completed market; refuse when far |
| 13 | setday | Large jump spreads (chat remaining days); no long freeze |
| 14 | Construction | Roads/parcels/doors usable; farms/houses appear; no phantom farm keys on new hamlets |
| 15 | Guns | mr_guns items never on guards |

## Failure handling
- Crash / silent no-op / duplicate projection → leave `runtimeSmoke` **unverified** or fail.
- Only a real client run may set `runtimeSmoke=pass` in the manifest.

## Geschiedenis
- Older plans referenced Wave 249 / EXTERNAL GATE language and schema 17 pins.
