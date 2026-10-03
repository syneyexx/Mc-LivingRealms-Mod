# Living Realms buildfix10 — World Integration Expansion contract

This document is the acceptance contract for the fixed singleplayer target modpack. Existing canonical systems remain authoritative; buildfix10 adds integration and visible-world depth without replacing the save model or allowing loaded chunks to become simulation authority.

## 1. M world map
- **M** opens the dedicated Living Realms strategic map.
- Server snapshot data includes discovered ecology/biome samples, kingdoms/claims, cities/towns/villages/hamlets, routes, armies and war fronts.
- The map is bounded and server-authoritative. It does not mutate simulation state.
- Runtime acceptance: verify the full fixed modpack, modded-biome labels, map readability and snapshot limits in a long-lived world.

## 2. Civilian life and physical jobs
- Civilians have deterministic identities, role schedules and street/road commuting.
- Lumberjacks harvest natural trees and feed canonical WOOD.
- Farmers harvest/replant mature CropBlock crops and feed canonical FOOD.
- Miners work natural ore blocks only, skip block entities and feed canonical mineral resources.
- Fishers require nearby water and generate bounded FOOD.
- Hunters seek Living Realms wildlife and successful kills feed canonical FOOD.
- Settlement population growth creates additional housing/infrastructure rather than leaving the physical settlement frozen.
- Runtime acceptance: observe work loops under chunk load/unload and confirm player builds are not indiscriminately damaged.

## 3. Existing vanilla/modded settlements join the world
- Loaded villager clusters can become canonical Living Realms settlements when not already covered.
- Loaded settlement-like structure starts from vanilla and target worldgen/building/content mods can be adopted.
- Adoption preserves the existing physical infrastructure first; future Living Realms growth expands around it.
- These settlements then participate in population, economy, diplomacy, trade, law, war and route systems.

## 4. Wildlife expansion
- Bundled catalog target for buildfix10: **134 species**.
- Added categories include domestic animals, insects, megafauna, primates, predators, snakes, birds of prey, vultures, marine mammals, fish and additional sharks.
- All Living Realms species participate in the data-driven ecology/utility-brain system: needs, locomotion, social behavior, hunting/fleeing/defending/migration where appropriate.
- `FLYING_SPEED` is mandatory for flying projections to prevent the Common Raven crash class.
- Visual limitation: 134 bespoke GeckoLib models/animations are **not** claimed in buildfix10; physical behavior is ahead of bespoke species art.

## 5. Dense living civilization
- Deterministic baseline: **12 kingdoms / 216 settlements** before foreign-world adoption and player-founded realms.
- Tier mix includes cities, towns, villages and hamlets.
- Regional roads/routes and local streets/sidewalks make settlements connected rather than isolated POIs.
- Projection budgets remain bounded so “more life” does not mean unbounded entity spawning.

## 6. Spawn kingdom
- The real Overworld spawn must fall within a Living Realms settlement/kingdom coverage radius.
- If the seeded capital does not cover the actual spawn, a bounded `Crownspawn` city is created.

## 7. Guards and visible target-mod equipment
- Settlement populations include guard representatives and military projections.
- Compatible registered armor/weapons/tools can be discovered from the fixed target pack.
- Citizen rendering includes held-item and humanoid-armor layers so equipment is visibly worn/held.
- Eligible RPG/ranged/magic equipment may be selected, excluding all denied gun namespaces.
- **Guns++ and GamingBarn's Guns are hard-denied for NPC loadouts** while remaining available to the player/creative catalog.
- Weapon adapters remain isolated from canonical simulation; denied gun mods are player-only.

## 8. Modded biomes and architecture
- Living Realms does not replace Biomes O' Plenty, Terralith, TerraBlender, Regions Unexplored or other worldgen authorities; their generated biome/terrain output is consumed by ecology and settlement placement.
- Settlements/wildlife are not restricted to vanilla biomes.
- Target-mod blocks are eligible for visual palette use, but automatic foreign blocks stay out of load-bearing geometry unless specifically authored. This prevents the malformed/floating-tower regression.
- Existing terrain-aware foundations/build-site search/road projection remain mandatory.

## 9. Player-founded realm
- `/livingrealms found <name>` creates a canonical player realm/settlement at the player's position when founding rules allow it.
- It uses the same population, construction, resources, diplomacy, trade, war and law systems as AI realms.
- `/livingrealms locate mine` locates the player's nearest canonical settlement.
- Immigration and ordinary growth can create civilians and additional physical construction over time.

## 10. Waystones
- When Waystones is loaded, a nearby loaded Living Realms settlement can receive one named settlement Waystone through an isolated compatibility bridge.
- The Waystone must persist and remain valid after save/reload.

## 11. Creative target-mod catalog
- **K** opens a creative-only searchable catalog of live registered non-vanilla items.
- Click-to-spawn is server-authoritative and validates creative mode, registry ID and stack count.
- It intentionally includes player-only items such as Guns++ and GamingBarn's Guns; the NPC deny-list is separate from the creative catalog.

## Fixed target modpack policy
The user's complete listed pack is treated as the fixed content target. Create is the only direct compile-hard gameplay dependency in Living Realms metadata; other target mods use stable APIs where justified and registry/tag/isolated-adapter integration elsewhere. This is an implementation choice for robustness, not a reduced compatibility target.

## Preservation rules
1. Save schema remains versioned and migration-safe.
2. Existing foreign block entities are never blindly overwritten.
3. Loaded Minecraft entities/blocks do not replace canonical simulation authority.
4. Player-authored structures are not generic resource quarries.
5. World integration is additive; existing Living Realms systems must continue passing their previous regression gates.
6. No feature is release-complete until the full fixed-modpack linked/runtime smoke verifies it in Minecraft.
