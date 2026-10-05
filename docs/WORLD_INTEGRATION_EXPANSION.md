# Living Realms — World Integration Expansion contract

CURRENT PINS: schema 19 / minSchema 1 / protocol 20 / network 16 / contentRevision 15 / surfaceSettlements 36 / perRealm 3 / spacing 2000

This document is the acceptance contract for the fixed singleplayer target modpack. Existing canonical systems remain authoritative. Integration adds visible-world depth without replacing the save model or allowing loaded chunks to become simulation authority.

## Product settlement policy (authoritative)

- **12 normal surface realms** × **3 starter settlements** = **36 surface starters**
- Composition per realm: 1 capital + 1 distant authored satellite + 1 rural hamlet
- **MIN_SETTLEMENT_SPACING = 2000** blocks (shared by seeding, founding, causal expansion, foreign adoption)
- Remaining authored Specs are an **expansion catalog** for later causal founding — not immediate seeds
- Wizard Trees remains separate (3 colonies) and excluded from surface counts
- Additional settlements arise **causally** (overpopulation, surplus, strategy) — never because the player explored far enough
- Foreign villages inside the 2000-block belt become **outlying sites**, not extra canonical settlements

## 1. M world map

- **M** opens the dedicated Living Realms strategic map.
- Terrain knowledge states: **ACTUAL** (sampled loaded chunks), **REGIONAL_ESTIMATE** (coarse canonical geography), **UNKNOWN** (parchment/fog — never fake sine-relief hills).
- Map rendering must not force-load world chunks.
- Server snapshot data includes discovered ecology/biome samples, kingdoms/claims, settlements, routes, armies and war fronts.

## 2. Civilian life and physical jobs

**Authority rule:** Canonical economic engines determine production. Physical workers project/choreograph those outcomes. Verified irreversible player interactions may bridge into canonical state, but ordinary NPC animation never creates load-dependent production.

- Civilians have deterministic identities, role schedules and street/road commuting.
- Physical lumberjack/farmer/miner/fisher/hunter loops are presentation of canonical yields — they must not mint extra resources merely because a chunk is loaded.
- Projected hunter kills of Living Realms wildlife must **not** directly mint canonical FOOD.
- Settlement population growth creates additional housing/infrastructure rather than leaving the physical settlement frozen.
- Runtime acceptance: observe work loops under chunk load/unload and confirm player builds are not indiscriminately damaged.

## 3. Existing vanilla/modded settlements join the world

- Duplicate physical footprint near an existing settlement → bind into that settlement.
- Foreign village **≥2000** from every canonical settlement → new `FOREIGN_ADOPTED` anchored settlement at real coordinates.
- Foreign village **&lt;2000** from an existing settlement → **outlying site** / annex (not a settlement); physical village stays where generated.
- Adoption preserves existing physical infrastructure; Living Realms expands around it.
- Villagers at sites still participate in households, trade, tax, law, rumors and travel.

## 4. Wildlife expansion

- Bundled catalog: **134 species**.
- All species participate in data-driven ecology.
- Visual families improve silhouettes without requiring 134 unique Java model classes.
- `FLYING_SPEED` remains mandatory for flying projections.

## 5. Sparse living civilization

- Fresh worlds: **36 surface starters** at **2000**-block spacing (not the historical dense 156/800 regression).
- Legacy dense saves (schema ≤18) preserve settlement IDs, names, ownership, population, construction and positions; marked `LEGACY` + physically anchored.
- Cities grow into streets, parcels, sidewalks, houses, apartments and districts via street-graph / parcel planning.
- Projection budgets remain bounded.

## 6. Spawn kingdom

- The real Overworld spawn must fall within Living Realms settlement/kingdom coverage.
- If needed, a bounded Crownspawn city may be created.

## 7. Guards and visible target-mod equipment

- Settlement populations include guard representatives and military projections.
- **Guns++ and GamingBarn's Guns are hard-denied for NPC loadouts** while remaining available to the player.

## 8. Modded biomes and architecture

- Living Realms does not replace Biomes O' Plenty, Terralith, TerraBlender, Regions Unexplored or other worldgen authorities.
- Architecture families are derived from civilization traits + geography (not `faction.id() % 8`).
- Terrain-aware foundations/build-site search/road projection remain mandatory.

## 9. Player-founded realm

- `/livingrealms found <name>` creates a **founding camp** (small population, temporary capacity, town-hall civic anchor — not an instant keep/city).
- Default development mode: **HYBRID** (player buildings count; Living Realms may fill deficits over time).
- Modes: AUTO / HYBRID / PLAYER_LED.
- Registered player structures contribute verified housing capacity without Living Realms rewriting player blocks.
- Immigration is gradual and pressure-driven.

## 10. Road Life layer

- Sparse journeys (courier, pilgrim, patrol, tax collector, …) and roadside sites (waystation, shrine, camp, …).
- Sites are **not** settlements and do not affect 2000-block spacing.
- Wilderness between cities must remain wilderness — not continuous suburbs.

## 11. Waystones / creative catalog

- Waystones compatibility and creative catalog (K) remain as previously specified.
- NPC gun deny-list is separate from the creative catalog.

## Preservation rules

1. Save schema remains versioned and migration-safe (schema 1→19 readable).
2. Existing foreign block entities are never blindly overwritten.
3. Loaded Minecraft entities/blocks do not replace canonical simulation authority.
4. Player-authored / registered structures are protected by WorldMutationGuard.
5. Anchored settlement position == physical location forever (no ordinary teleportation).
6. No feature is release-complete until the full fixed-modpack linked/runtime smoke verifies it in Minecraft.

## Historical note

Earlier dense policies (216 / 156 surface settlements, 800-block spacing, worker-direct production wording) are obsolete product regressions and must not appear in current-status documentation.
