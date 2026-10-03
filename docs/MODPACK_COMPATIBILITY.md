# Living Realms — fixed singleplayer target-pack compatibility

Target: **Minecraft 1.21.1 + NeoForge 21.1.x + Create 6.0.10 + Java 21**.

The complete user-specified mod list is the fixed content target for Living Realms. Mods can therefore be assumed present in the intended pack. Living Realms still avoids brittle direct Java links where a stable public API is unnecessary: registry/tag discovery and isolated adapters are preferred so a content-mod update does not corrupt canonical simulation state.

## Core integration policy
- **Create:** direct compile/runtime gameplay dependency. Industry projection may use Create machinery.
- **Waystones:** settlement travel integration; Living Realms settlements may receive named Waystones without overwriting foreign block entities.
- **Biomes O' Plenty, Terralith, TerraBlender, Regions Unexplored, Lithostitched, Nyctophobia:** their terrain/biomes remain authoritative. Living Realms consumes the generated biome/terrain output for ecology, settlement placement and map presentation; it does not replace the biome source.
- **Better Villages, Medieval Buildings, Towers of the Wild and other structure/worldgen content:** loaded settlement-like structures can be adopted into canonical Living Realms simulation while preserving the existing physical build first.
- **Macaw building suite, Artemis Laboratory Blocks, Beyond and More, Elomod, Alighieri's Legacy, SecurityCraft and other eligible building/content mods:** registered blocks may contribute to safe non-load-bearing settlement decoration, roads, paths, lights and authored integration points. Foreign block entities are never blindly overwritten.
- **Armor of the Ages, Armory (RPG Series), Immersive Armors, Fantasy Armor, Iron's Spells stack, More Bows and Arrows and other eligible combat/magic mods:** registered equipment may be selected for guards/soldiers and is visibly rendered when equipped. Mod-specific firing/casting behavior remains adapter-dependent until verified in the full-pack runtime smoke.
- **GamingBarn's Guns:** intentionally **player-only**. Living Realms NPC equipment discovery must exclude this mod even though its items remain available to the player and creative catalog.
- **Alex's Mobs Continued, Ben's Sharks, Mobs of Mythology, Mob Captains and other creature mods:** foreign entities coexist with Living Realms wildlife. Living Realms does not take ownership of their canonical AI/entity state unless a dedicated adapter explicitly does so.
- **Ad Astra, Create: Radars, Create: Big Cannons, Create Nuclear, Mystical Agriculture, Butchery and similar progression/content mods:** registered content can be used by advanced realms through isolated adapters/registry discovery without making loaded blocks authoritative for the abstract simulation.

## Fixed target-pack inventory
The intended pack includes: GeckoLib, Curios API, Create, FerriteCore, 3D Skin Layers, JEI, Clumps, Shulker Box Tooltip, Biomes O' Plenty, GlitchCore, TerraBlender, Waystones, Balm, Terralith, Lithostitched, Traveler's Backpack, NeoCulus, Embeddium, Embeddium Extra, GPUBooster/GPUTape, Armor of the Ages, YetAnotherConfigLib, Artemis Laboratory Blocks, Fusion Connected Textures, Elomod, Beyond and More, Alighieri's Legacy, Armory (RPG Series), Spell Engine, Ranged Weapon API, Armor Model API, PlayerAnimator, Cloth Config API, Spell Power Attributes, Reliable Advancements, Iron's Spells 'n Spellbooks, Iron's Spells Dynamic Skill Trees, Pufferfish's Skills, Pufferfish's Attributes, Pufferfish's Unofficial Additions, the Macaw building suite, Create Nuclear, Guns++, Alex's Mobs Continued, CodxLib, Immersive Armors, Armor Statues, Puzzles Lib, Fantasy Armor, Iron's Lib, Regions Unexplored, Better Villages, Library Ferret, SecurityCraft, Mob Captains, Nyctophobia, Ad Astra, Towers of the Wild, Medieval Buildings, Mobs of Mythology, Create: Radars, Mystical Agriculture, Butchery, Ben's Sharks, Elemental Wizards, GamingBarn's Guns, More Bows and Arrows and Create: Big Cannons.

## Passive coexistence
FerriteCore, NeoCulus, Embeddium, Embeddium Extra, GPUBooster/GPUTape, 3D Skin Layers, Fusion Connected Textures, JEI, Clumps, Shulker Box Tooltip, Reliable Advancements, YACL, Cloth Config, Curios, Balm, GlitchCore, Traveler's Backpack and support libraries remain outside canonical Living Realms simulation ownership unless an explicit adapter needs them.

Traveler's Backpack and other portable/player-owned inventories are excluded from faction-storage theft tracking.

## Non-negotiable safety rules
1. Never replace the pack biome source.
2. Never overwrite an existing foreign block entity during settlement/route construction.
3. Never make client renderer/performance internals part of canonical save state.
4. Guns++ and GamingBarn's Guns remain NPC-forbidden.
5. Faction theft applies only to authored Living Realms storage slots, not arbitrary nearby containers.
6. Registry-discovered content keeps a safe fallback path and is not used blindly for load-bearing geometry.
7. Foreign generated settlements/structures are preserved before Living Realms expands them.
8. Loaded Minecraft entities/blocks never replace canonical simulation authority.

The authoritative machine-readable catalog is `ModCompatibilityPolicy.java`; `SystemCompletenessTest`, `LivingWorldDensityTest` and `scripts/release-audit.py` enforce the critical exclusions and integration rules.
