# Manual Runtime Acceptance Plan

Hand checklist after automated core suite, release-audit, and linked NeoForge build are green.
This is a catch-net — it does **not** replace automated tests.

CURRENT PINS: schema 18 / minSchema 1 / protocol 19 / network 14 / contentRevision 14 / surfaceSettlements 156 / perRealm 13 / spacing 800

## Environment
1. JAR from `./build-production.sh` / `build/libs`.
2. Minecraft 1.21.1 + NeoForge 21.1.219 + Create 6.0.10.
3. Fresh world + one migrated save.

## Smoke checklist

| # | Check | Pass criteria |
|---|-------|---------------|
| 1 | Mod loads | No crash; Living Realms in mod list |
| 2 | Density | ~156 surface settlements + Wizard Trees; Spec names present |
| 3 | Spacing | Founding needs 800m clearance (HUD/found message) |
| 4 | F12 dashboard | All tabs; GRAIN/FLOUR/BREAD/MEAT/ALE/WOOL visible |
| 5 | War tab | Goals show capital targets (not list-order first town) |
| 6 | M map | Terrain base on; no vanilla blur; settlement ids match sim |
| 7 | Dialogue | "welk koninkrijk is dit?" → faction, not ruler |
| 8 | Capture | After conquest, citizen names the new faction |
| 9 | Market | Buy BREAD near completed market; refuse when far |
| 10 | setday | Large jump spreads (chat remaining days); no long freeze |
| 11 | Construction | Farms/houses appear as blocks; no phantom farm keys on new hamlets |
| 12 | Guns | mr_guns items never on guards |

## Failure handling
- Crash / silent no-op / duplicate projection → leave `runtimeSmoke` **unverified** or fail.
- Only a real client run may set `runtimeSmoke=pass` in the manifest.

## Geschiedenis
- Older plans referenced Wave 249 / EXTERNAL GATE language and schema 17 pins.
