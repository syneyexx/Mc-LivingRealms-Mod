# Living Realms — World Integration Expansion contract

CURRENT PINS: schema 21 / minSchema 1 / protocol 20 / network 16 / contentRevision 18 / starterSettlements 204-300 / perRealm 17-25 / capitalSpacing 3000-4500 / roleAwareSpacing

This document is the acceptance contract for the fixed singleplayer target modpack. Canonical systems remain authoritative. Fresh-world starter fabric is authored during chunk generation; runtime integration adds later visible-world depth without allowing loaded chunks to become simulation authority.

## Product settlement policy (authoritative)

- **12 normal surface realms**, each with **1 capital + 10 authored satellites + 6–14 rural hamlets**
- Starter range: **17–25 per realm**, **204–300 surface starters** total
- `SettlementSpacingPolicy` is role-aware: capital↔capital preferred **3000–4500**, capital↔town **650–1200**, town↔village **350–650**, village↔hamlet **180–350**, hamlet↔hamlet **150–300**
- Collision floors are type-pair-specific; there is no universal settlement exclusion constant
- Wizard Trees remains separate (3 colonies) and excluded from surface counts
- Additional settlements arise **causally** (overpopulation, surplus, strategy) — never because the player explored far enough
- Foreign sites that violate the relevant role-pair floor become **outlying sites**, not duplicate canonical settlements

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
- Foreign site outside every applicable role-pair exclusion floor → new `FOREIGN_ADOPTED` anchored settlement at real coordinates.
- Foreign site inside an applicable role-pair exclusion floor → **outlying site** / annex (not a settlement); physical infrastructure stays where generated.
- Adoption preserves existing physical infrastructure; Living Realms expands around it.
- Villagers at sites still participate in households, trade, tax, law, rumors and travel.

## 4. Wildlife expansion

- Bundled catalog: **134 species**.
- All species participate in data-driven ecology.
- Visual families improve silhouettes without requiring 134 unique Java model classes.
- `FLYING_SPEED` remains mandatory for flying projections.

## 5. Hierarchical living civilization

- Fresh worlds: **204–300 surface starters**, organized as 17–25 settlements per realm with role-aware spacing.
- Legacy/anchored saves preserve settlement IDs, names, ownership, population, construction and physical positions.
- Cities grow from graph-first street topology into parcels, frontage-facing buildings and coherent CITY+ wall/gate boundaries.
- Persistent settlement/road/special-site block fabric is chunk-driven without player-distance authority or permanent force-loading.
- Projection budgets remain bounded; full entities and regional impostors use separate per-kind LOD cutoffs.

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
- Sites are **not** ordinary settlements and use context-specific placement rules instead of the living-settlement spacing matrix.
- Wilderness between cities must remain wilderness — not continuous suburbs.

## 11. Waystones / creative catalog

- Waystones compatibility and creative catalog (K) remain as previously specified.
- NPC gun deny-list is separate from the creative catalog.

## Preservation rules

1. Save schema remains versioned and migration-safe (schemas 1→21 supported by the current migration matrix).
2. Existing foreign block entities are never blindly overwritten.
3. Loaded Minecraft entities/blocks do not replace canonical simulation authority.
4. Player-authored / registered structures are protected by WorldMutationGuard.
5. Anchored settlement position == physical location forever (no ordinary teleportation).
6. No feature is release-complete until the full fixed-modpack linked/runtime smoke verifies it in Minecraft.

## Historical note

Earlier policies using a universal 2000-block settlement floor, only three settlements per realm, the older 156/800 density, or worker-direct production wording are obsolete and belong only in clearly marked history.
