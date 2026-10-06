# Manual Runtime Acceptance Plan

Hand checklist after automated core suite, release-audit, and linked NeoForge/Create build are green.
This is a catch-net — it does **not** replace automated tests.

CURRENT PINS: schema 21 / minSchema 1 / protocol 20 / network 16 / contentRevision 16 / starterSettlements 204-300 / perRealm 17-25 / capitalSpacing 3000-4500 / roleAwareSpacing

## Environment

1. Use the JAR produced from the exact tested HEAD.
2. Minecraft 1.21.1 + NeoForge 21.1.219 + Create 6.0.10.
3. Test one **fresh world** and one migrated pre-schema-21 save.
4. Keep normal render/simulation distance first; repeat the >640 approach with spectator flight and a higher render distance if needed to observe chunk generation clearly.

## Fresh-world civilization fabric

| # | Check | Pass criteria |
|---|---|---|
| 1 | Spawn capital | Spawn realm immediately reads as a capital: dense street/building mass, government/keep, market/plaza, housing, basic economy, and coherent walls/gates where applicable. It must not look like keep + empty road grid. |
| 2 | Leave spawn on a main road | Follow a regional road outward. The route physically continues beyond the capital and connects toward real towns/villages/hamlets rather than ending as decoration. |
| 3 | Settlement hierarchy | In the spawn realm, observe multiple towns, villages and hamlets. Typical hierarchy should resemble 1 capital + 2–4 towns + 6–8 villages + 6–14 rural hamlets, subject to deterministic geography. |
| 4 | True-settlement gaps | In ordinary inhabited territory, another real ordinary settlement should normally be within about **800 blocks**. Record any larger gap and verify it has a meaningful geography/frontier/history cause rather than an exclusion constant. |
| 5 | Roadside fabric gaps | On operational ROAD/CARAVAN corridors, verify waystations/milestones/shrines/camps or settlement endpoints keep meaningful civilization fabric roughly every **150–450 blocks** without turning the road into continuous suburbs. |
| 6 | >640 block approach | Pick a known settlement, move/fly more than 640 blocks away, then approach while its chunks generate. Roads/buildings/walls must appear because the chunks exist — not suddenly start construction only after crossing a player-distance activation threshold. |
| 7 | No permanent force-loading | Leave the region and inspect normal server/chunk behavior. Civilization chunks must unload normally; Living Realms must not retain the whole realm/world as permanent tickets. |
| 8 | CITY+ perimeter | Fly around a CITY/METROPOLIS. The defensive perimeter is closed except explicit gate openings; no random wall stubs or wall segments permanently cross their own roads. |
| 9 | Gate alignment | Follow at least two outgoing regional roads through city gates. Roads must pass through real openings and gates should face meaningful destination directions. |
| 10 | Street morphology | Compare at least two morphologies. Organic/radial/hill/river/planned layouts must visibly differ topologically; non-grid layouts must contain real diagonal/curved/polyline streets rather than offset orthogonal rectangles. |
| 11 | Frontage from the air | Inspect housing/civic lots from spectator view. Buildings should front/access streets, dense cores should show dense frontage, and houses must not float randomly inside giant green rectangles. |
| 12 | Hamlet completeness | Inspect several hamlets. Each should read as a small complete place — short path/road, housing cluster and basic food/water/economic fabric — not a miniature city or an empty marker. |
| 13 | Rural/border belt | Travel away from inner-realm corridors. Density should fall into villages/hamlets/farms/resource sites, then sparse borderland/wilderness. The result must retain meaningful wilderness rather than uniform urban sprawl. |
| 14 | Special sites | Inspect available refugee/outlying/bandit/pirate/ruin/resource sites. They must use context-specific placement and remain distinct from ordinary village spacing/graph nodes. |

## Entity / detail LOD

| # | Check | Pass criteria |
|---|---|---|
| 15 | Citizens activate against existing cores | Approach an already-built settlement. Citizens/guards/workers/market life appear near the player and target real built structures; the settlement blocks already existed before those entities materialized. |
| 16 | Leave entity radius | Move away again. Full entities may dematerialize, but roads/buildings/walls/farms remain. Canonical population/economy does not disappear on entity unload. |
| 17 | Regional impostors | At regional distance, full citizens/armies/caravans/herds/ships/migrations must not overlap their corresponding impostor representation. Each token begins only outside its full-entity cutoff. |
| 18 | Guards / weapons | Guns++ / GamingBarn gun items remain player-only and must never be equipped on Living Realms guards/NPCs. |
| 19 | Create integration | Create machinery remains intact and functional. Living Realms block reconciliation must not overwrite foreign block entities or replace the active biome source. |

## Persistence / migration

| # | Check | Pass criteria |
|---|---|---|
| 20 | Fresh save/reload | Save, quit and reload after visiting multiple settlements/routes. No duplicate starter settlements, duplicate road fabric or missing construction receipts. |
| 21 | Migrated anchored save | Load an older supported save. Existing physically anchored settlements keep their coordinates/identity; schema migration must not teleport them to satisfy the fresh-world layout. |
| 22 | Foreign settlement preservation | Visit an adopted/foreign village or structure. It must remain physically intact and be integrated/classified rather than bulldozed or duplicated on top of itself. |
| 23 | UI/commands smoke | F12/M/K, join/leave/found/locate, market, dialogue and setday still operate. Founding clearance messages must reflect role-aware spacing rather than a universal 2000-block rule. |

## Failure handling

- A crash, silent no-op, duplicate projection, settlement relocation, player-radius block creation, broken city perimeter, or forced global chunk retention is a **failure**.
- Leave `runtimeSmoke` **unverified** unless this full client/integrated-server procedure is actually performed.
- Only a real client run may set `runtimeSmoke=pass` in the release manifest.

## History

Earlier runtime plans pinned schema 20, 36 surface starters / 3 per realm and a universal 2000-block clearance. Those values describe the retired architecture and are not current product policy.
