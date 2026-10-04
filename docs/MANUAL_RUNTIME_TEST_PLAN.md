# Wave 249 — Manual Runtime Acceptance Plan

External gate after automated core suite, release audit, and linked NeoForge build stay green.
Do **not** mark the product COMPLETE until this checklist is run in a live 1.21.1 NeoForge + Create client.

## Environment

1. Install the JAR from `build/libs` produced by `./build-production.sh`.
2. Launch Minecraft 1.21.1 with NeoForge 21.1.x and Create 6.0.10.
3. Create a fresh singleplayer world (Creative first pass, then Survival spot-checks).

## Smoke checklist

| # | Check | Pass criteria |
|---|-------|---------------|
| 1 | Mod loads | No hard crash; Living Realms appears in mod list |
| 2 | Spawn capital | City-scale capital near spawn with streets/castle court |
| 3 | Dashboard F12 | Opens; Overview shows day, membership, realm stats |
| 4 | Dashboard a11y | Settings → UI scale cycles Compact/Normal/Large; high contrast toggles readable colors; prefs survive reopen |
| 5 | Keyboard a11y | Arrow keys page; keys 1–9 switch tabs |
| 6 | World map M | Biomes/claims/settlements/routes visible |
| 7 | Citizens | Named citizens near settlement; professions readable; refugees show travel kits if present |
| 8 | Caravans | Wagon/pack silhouettes on trade routes, not generic villagers only |
| 9 | Wildlife | Morphology-family models/textures; no duplicate projection storms |
| 10 | Military | Formation presentation near armies; court skins at keep |
| 11 | Ships | Cargo / patrol / war / landing classes use distinct textures and silhouettes |
| 12 | Aircraft | Fighter / recon / transport / bomber roles use role textures, scale, and part visibility |
| 13 | Industry | Status markers on industrial sites |
| 14 | Influence | Overview influence buttons send server actions with chat feedback |
| 15 | Projection lifecycle | Teleport across LOD; no duplicate wing/fleet/citizen IDs after unload/reload |
| 16 | Ambience | Settlement particles/sounds present without spam |

## Failure handling

- Any crash, silent no-op action, duplicate projection ID, or missing class texture → record in PR notes and leave `runtime-smoke` as **unverified** / fail.
- On full pass, update `RELEASE_MANIFEST.json` `runtime_smoke` to `pass` via a follow-up rebuild notes commit.

## Explicit non-claims

Passing this plan validates interactive singleplayer smoke only. It does not claim multiplayer soak, every wave COMPLETE, or external art QA sign-off.
