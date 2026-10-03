package dev.livingrealms.sim.compat;

import java.util.*;

/**
 * Central compatibility contract for Romy's singleplayer target pack.
 *
 * <p>Create remains the only compile-time gameplay dependency of Living Realms itself. The target
 * pack may contain many more required mods, but integrations must use registry/tag discovery or
 * isolated adapters so client/performance mods cannot destabilize canonical simulation code.</p>
 */
public final class ModCompatibilityPolicy {
    public enum Category {
        REQUIRED, API, PERFORMANCE, CLIENT_VISUAL, UI_QOL, WORLDGEN, TRAVEL, STORAGE,
        BUILDING, COMBAT, MAGIC, CONTENT, CREATURES
    }
    public enum Strategy {
        HARD_DEPENDENCY,
        PACK_REQUIRED,
        PASSIVE_COEXISTENCE,
        TAG_AND_REGISTRY_DISCOVERY,
        OPTIONAL_API,
        PLAYER_ONLY
    }
    public record Entry(
            String displayName,
            String modId,
            Set<String> detectionIds,
            Category category,
            Strategy strategy,
            boolean usableByLivingWorld,
            String notes
    ) {
        public Entry {
            if(displayName==null||displayName.isBlank()||modId==null||modId.isBlank()||category==null||strategy==null)throw new IllegalArgumentException("compat entry");
            LinkedHashSet<String> ids=new LinkedHashSet<>();
            ids.add(modId);
            if(detectionIds!=null)for(String id:detectionIds)if(id!=null&&!id.isBlank())ids.add(id);
            detectionIds=Collections.unmodifiableSet(ids);
            notes=Objects.requireNonNullElse(notes,"");
        }
        public Entry(String displayName,String modId,Category category,Strategy strategy,boolean usableByLivingWorld,String notes,String...aliases){
            this(displayName,modId,aliases==null?Set.of():new LinkedHashSet<>(Arrays.asList(aliases)),category,strategy,usableByLivingWorld,notes);
        }
        public boolean matches(String id){return id!=null&&detectionIds.contains(id);}
    }

    private static Entry e(String name,String id,Category category,Strategy strategy,boolean world,String notes,String...aliases){
        return new Entry(name,id,category,strategy,world,notes,aliases);
    }

    private static final List<Entry> TARGETS=List.of(
            e("Create","create",Category.REQUIRED,Strategy.HARD_DEPENDENCY,true,"Industrial projection targets Create 6.0.10 on Minecraft 1.21.1."),
            e("GeckoLib","geckolib",Category.API,Strategy.PACK_REQUIRED,true,"Animation API may back Living Realms creature/content adapters without entering canonical simulation."),
            e("Curios API","curios",Category.API,Strategy.PACK_REQUIRED,true,"Equipment discovery may use Curios registries; Living Realms does not replace Curios slots."),
            e("FerriteCore","ferritecore",Category.PERFORMANCE,Strategy.PACK_REQUIRED,false,"Coexistence only; no mixins target FerriteCore or collection internals."),
            e("3D Skin Layers","skinlayers3d",Category.CLIENT_VISUAL,Strategy.PACK_REQUIRED,false,"Player renderer coexistence; Living Realms does not replace player skin layers.","skinlayers"),
            e("Just Enough Items","jei",Category.UI_QOL,Strategy.PACK_REQUIRED,false,"Recipe visibility integration only; simulation never depends on JEI."),
            e("Clumps","clumps",Category.PERFORMANCE,Strategy.PACK_REQUIRED,false,"Coexistence only; XP orb logic is untouched."),
            e("Shulker Box Tooltip","shulkerboxtooltip",Category.UI_QOL,Strategy.PACK_REQUIRED,false,"Tooltip coexistence; no tooltip interception."),
            e("Biomes O' Plenty","biomesoplenty",Category.WORLDGEN,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Biome source remains authoritative; ecology consumes generated biomes.","biomes_o_plenty"),
            e("GlitchCore","glitchcore",Category.API,Strategy.PACK_REQUIRED,false,"Support library; no canonical dependency."),
            e("CodxLib","codxlib",Category.API,Strategy.PACK_REQUIRED,false,"Target-pack support library; no canonical state ownership."),
            e("TerraBlender","terrablender",Category.WORLDGEN,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Living Realms never replaces TerraBlender regions or biome source."),
            e("Waystones","waystones",Category.TRAVEL,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Waystones can be recognized as strategic travel infrastructure; builders preserve their block entities."),
            e("Balm","balm",Category.API,Strategy.PACK_REQUIRED,false,"Support library used by pack mods; no canonical dependency."),
            e("Puzzles Lib","puzzleslib",Category.API,Strategy.PACK_REQUIRED,false,"Target-pack support library; Living Realms does not intercept its components."),
            e("Terralith","terralith",Category.WORLDGEN,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Terrain and biome output is consumed, never replaced."),
            e("Lithostitched","lithostitched",Category.WORLDGEN,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Datapack/worldgen registries remain authoritative."),
            e("Traveler's Backpack","travelersbackpack",Category.STORAGE,Strategy.PACK_REQUIRED,false,"Backpack inventories remain player-owned and are excluded from faction-container theft tracking.","travelers_backpack"),
            e("NeoCulus","neoculus",Category.CLIENT_VISUAL,Strategy.PACK_REQUIRED,false,"Shader/render coexistence only; no renderer mixins are required by Living Realms.","oculus"),
            e("Embeddium","embeddium",Category.PERFORMANCE,Strategy.PACK_REQUIRED,false,"Renderer/performance coexistence only."),
            e("Embeddium (Rubidium) Extra","embeddium_extra",Category.CLIENT_VISUAL,Strategy.PACK_REQUIRED,false,"Client options coexistence only.","rubidium_extra"),
            e("GPUBooster","gpubooster",Category.PERFORMANCE,Strategy.PACK_REQUIRED,false,"Performance coexistence only; Living Realms does not alter GPU selection or renderer internals.","gpu_booster","gpu_tape"),
            e("Armor of the Ages","armoroftheages",Category.COMBAT,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Eligible faction equipment/content source through registries and tags."),
            e("YetAnotherConfigLib","yet_another_config_lib_v3",Category.API,Strategy.PACK_REQUIRED,false,"Configuration library coexistence; Living Realms keeps its canonical config independent.","yacl","yet_another_config_lib"),
            e("Artemis Laboratory Blocks","artemis",Category.BUILDING,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Eligible faction construction palette; foreign block entities are preserved.","artemislab","artemis_laboratory_blocks","laboratoryblocks"),
            e("Fusion Connected Textures","fusion",Category.CLIENT_VISUAL,Strategy.PACK_REQUIRED,false,"Connected-texture coexistence; builders place registered blocks normally."),
            e("Elomod","elomod",Category.CONTENT,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Eligible registry/tag content source without direct class dependency."),
            e("Beyond and More","beyond_and_more",Category.BUILDING,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Large building/resource palette may be consumed through registries/tags.","beyondandmore","steel_and_more"),
            e("Alighieri's Legacy","alighieris_legacy",Category.CONTENT,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Eligible world/faction content source through registry discovery.","alighieri_legacy"),
            e("Armory (RPG Series)","armory",Category.COMBAT,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Faction equipment source; weapons/armor are selected through tags/registries.","armory_rpg_series","armory_rpgs"),
            e("Spell Engine","spell_engine",Category.MAGIC,Strategy.PACK_REQUIRED,true,"Magic-capable factions may consume Spell Engine content through isolated adapters."),
            e("Ranged Weapons API","ranged_weapon_api",Category.API,Strategy.PACK_REQUIRED,true,"Ranged equipment compatibility layer for faction loadouts.","ranged_weapon_api_v2"),
            e("Armor Model API","armor_model_api",Category.API,Strategy.PACK_REQUIRED,true,"Armor model compatibility layer; no direct canonical dependency."),
            e("PlayerAnimator","playeranimator",Category.API,Strategy.PACK_REQUIRED,true,"Animation compatibility for player-facing combat content.","player_animator"),
            e("Cloth Config API","cloth_config",Category.API,Strategy.PACK_REQUIRED,false,"Configuration-library coexistence only.","cloth-config"),
            e("Spell Power Attributes","spell_power",Category.MAGIC,Strategy.PACK_REQUIRED,true,"Magic-stat compatibility for faction equipment and combat adapters.","spell_power_attributes"),
            e("Reliable Advancements","reliable_advancements",Category.UI_QOL,Strategy.PACK_REQUIRED,false,"Advancement reliability coexistence; Living Realms does not patch advancement internals.","reliableadvancements"),
            e("Iron's Lib","irons_lib",Category.API,Strategy.PACK_REQUIRED,true,"Required support library for the Iron's content stack in the target pack.","ironslib"),
            e("Iron's Spells 'n Spellbooks","irons_spellbooks",Category.MAGIC,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Magic schools/spells may feed faction doctrine and equipment through isolated integration.","irons_spells_n_spellbooks"),
            e("Iron's Spells Dynamic Skill Trees","irons_spells_dynamic_skilltree",Category.MAGIC,Strategy.PACK_REQUIRED,true,"Skill-tree coexistence for the Iron's magic stack.","irons_spellbooks_skill_tree","irons_spellbooks_dynamic_skill_trees","irons_skill_tree"),
            e("Pufferfish's Skills","puffish_skills",Category.API,Strategy.PACK_REQUIRED,true,"Player progression coexistence; Living Realms faction rank remains separate."),
            e("Pufferfish Attributes","puffish_attributes",Category.API,Strategy.PACK_REQUIRED,true,"Attribute compatibility for combat/equipment adapters."),
            e("Pufferfish Unofficial Additions","pufferfish_unofficial_additions",Category.API,Strategy.PACK_REQUIRED,true,"Target-pack progression addon coexistence.","puffish_skills_additions","puffish_unofficial_additions","puffish_additions"),
            e("Macaw's Windows","mcwwindows",Category.BUILDING,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Faction architecture palette."),
            e("Macaw's Fences and Walls","mcwfences",Category.BUILDING,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Faction architecture palette."),
            e("Macaw's Bridges","mcwbridges",Category.BUILDING,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Faction bridges/roads may use registered blocks without overwriting block entities."),
            e("Macaw's Stairs","mcwstairs",Category.BUILDING,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Faction architecture palette."),
            e("Macaw's Roofs","mcwroofs",Category.BUILDING,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Faction architecture palette."),
            e("Macaw's Doors","mcwdoors",Category.BUILDING,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Faction architecture palette."),
            e("Macaw's Furniture","mcwfurnitures",Category.BUILDING,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Settlement interior palette.","mcwfurniture"),
            e("Macaw's Paths and Pavings","mcwpaths",Category.BUILDING,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Road/settlement path palette."),
            e("Macaw's Paintings","mcwpaintings",Category.BUILDING,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Settlement decoration palette."),
            e("Macaw's Lights and Lamps","mcwlights",Category.BUILDING,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Settlement lighting palette."),
            e("Macaw's Trapdoors","mcwtrpdoors",Category.BUILDING,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Faction architecture palette.","mcwtrapdoors"),
            e("Macaw's Holidays","mcwholidays",Category.BUILDING,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Seasonal settlement decoration palette."),
            e("Create Nuclear","createnuclear",Category.CONTENT,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Advanced industrial content may be consumed by late-game faction industry.","create_nuclear"),
            e("Guns++","mr_guns",Category.COMBAT,Strategy.PLAYER_ONLY,false,"Explicit NPC deny-list: Guns++ remains player-only for Living Realms equipment discovery.","gunsplusplus","guns_plus_plus","gunspp"),
            e("Alex's Mobs Continued","alexsmobs",Category.CREATURES,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"External wildlife can coexist with Living Realms ecology; no duplicate takeover of foreign entities.","alexsmobscontinued","alexs_mobs_continued"),
            e("Immersive Armors","immersive_armors",Category.COMBAT,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Eligible faction armor source through registry/tag discovery.","immersivearmors"),
            e("Armor Statues","armor_statues",Category.BUILDING,Strategy.PACK_REQUIRED,false,"Decoration coexistence; Living Realms builders preserve block entities.","armorstatues"),
            e("Fantasy Armor (Medieval Series)","fantasy_armor",Category.COMBAT,Strategy.TAG_AND_REGISTRY_DISCOVERY,true,"Eligible faction armor source for medieval/fantasy realms.","fantasyarmor"),
            e("Regions Unexplored","regions_unexplored",Category.WORLDGEN,Strategy.PACK_REQUIRED,true,"Biome and block content are first-class Living Realms habitat/building inputs.","regionsunexplored","regions_unexplored_neoforge"),
            e("Better Villages","better_village",Category.WORLDGEN,Strategy.PACK_REQUIRED,true,"Generated villages are adopted into Living Realms settlement simulation instead of remaining static scenery.","bettervillages","better_villages"),
            e("Library Ferret","libraryferret",Category.API,Strategy.PACK_REQUIRED,false,"Target-pack library dependency; no canonical simulation ownership.","library_ferret"),
            e("SecurityCraft","securitycraft",Category.BUILDING,Strategy.PACK_REQUIRED,true,"Security blocks may appear in high-security settlements; foreign block entities are never overwritten.","security_craft"),
            e("Mob Captains","mobcaptains",Category.CREATURES,Strategy.PACK_REQUIRED,true,"Captain mobs coexist with settlement guards and faction combat.","mob_captains"),
            e("Nyctophobia","nyctophobia",Category.WORLDGEN,Strategy.PACK_REQUIRED,true,"Biome output is classified by Living Realms ecology without replacing its terrain authority."),
            e("Ad Astra","ad_astra",Category.CONTENT,Strategy.PACK_REQUIRED,true,"Late-game realms may use registered Ad Astra resources/equipment while space dimensions remain foreign-authoritative.","adastra"),
            e("Towers of the Wild","towers_of_the_wild",Category.WORLDGEN,Strategy.PACK_REQUIRED,true,"Generated towers are recognized as world landmarks and may influence nearby settlement routes.","towersofthewild","totw_modded"),
            e("Medieval Buildings","medieval_buildings",Category.WORLDGEN,Strategy.PACK_REQUIRED,true,"Generated medieval structures are eligible for settlement adoption and visual palette discovery.","medievalbuildings"),
            e("Mobs of Mythology","mobs_of_mythology",Category.CREATURES,Strategy.PACK_REQUIRED,true,"Mythological mobs coexist with canonical ecology and may be treated as dangerous regional fauna.","mobsofmythology"),
            e("Create: Radars","create_radar",Category.CONTENT,Strategy.PACK_REQUIRED,true,"Advanced kingdoms may consume radar blocks as strategic detection infrastructure.","create_radars","createradar"),
            e("Mystical Agriculture","mysticalagriculture",Category.CONTENT,Strategy.PACK_REQUIRED,true,"Registered crops/resources can feed advanced agriculture and faction equipment discovery.","mystical_agriculture"),
            e("Butchery","butchery",Category.CONTENT,Strategy.PACK_REQUIRED,true,"Food/meat content can participate in hunting and settlement food presentation."),
            e("Ben's Sharks","bens_sharks",Category.CREATURES,Strategy.PACK_REQUIRED,true,"External sharks coexist with the marine ecology without duplicate entity takeover.","benssharks","bens_shark"),
            e("Elemental Wizards (RPG Series)","elemental_wizards_rpg",Category.MAGIC,Strategy.PACK_REQUIRED,true,"Wizard equipment and magic content may be assigned to appropriate faction roles.","elemental_wizards","elementalwizards"),
            e("GamingBarn's Guns","gamingbarns_guns",Category.COMBAT,Strategy.PLAYER_ONLY,false,"Explicit NPC deny-list: these guns remain player-only even though the mod is part of the required pack.","gamingbarn_guns","gamingbarnsguns"),
            e("More Bows and Arrows","more_bows_and_arrows",Category.COMBAT,Strategy.PACK_REQUIRED,true,"Registered bows/crossbows are eligible for faction guard and soldier loadouts.","morebowsandarrows","more_bows"),
            e("Create: Big Cannons","createbigcannons",Category.COMBAT,Strategy.PACK_REQUIRED,true,"Advanced kingdoms may use registered artillery/cannon content through isolated adapters.","create_big_cannons","cbc")
    );

    private static final Map<String,Entry> BY_ID;
    static {
        Map<String,Entry> m=new LinkedHashMap<>();
        for(Entry e:TARGETS)for(String id:e.detectionIds())m.putIfAbsent(id,e);
        BY_ID=Collections.unmodifiableMap(m);
    }

    private ModCompatibilityPolicy(){}
    public static List<Entry> targets(){return TARGETS;}
    public static Optional<Entry> find(String modId){return Optional.ofNullable(BY_ID.get(modId));}
    public static boolean worldgenSensitive(String modId){return find(modId).map(e->e.category()==Category.WORLDGEN).orElse(false);}
    public static boolean mayReplaceBiomeSource(){return false;}
    public static boolean mayOverwriteBlockEntities(){return false;}
    public static boolean mayUseForLivingWorld(String modId){return find(modId).map(Entry::usableByLivingWorld).orElse(false);}
    public static boolean isPlayerOnly(String modId){return find(modId).map(e->e.strategy()==Strategy.PLAYER_ONLY).orElse(false);}
    public static Set<String> removedFromTargetPack(){return Set.of("create_deep_seas","createdeepseas","create_aeronautics","createaeronautics");}

    public static void validate(){
        Set<String> primary=new HashSet<>();
        Set<String> allIds=new HashSet<>();
        for(Entry e:TARGETS){
            if(!primary.add(e.modId()))throw new IllegalStateException("duplicate primary compatibility mod id: "+e.modId());
            for(String id:e.detectionIds())if(!allIds.add(id))throw new IllegalStateException("duplicate compatibility detection id: "+id);
        }
        long hard=TARGETS.stream().filter(e->e.strategy()==Strategy.HARD_DEPENDENCY).count();
        if(hard!=1||!find("create").map(e->e.strategy()==Strategy.HARD_DEPENDENCY).orElse(false))throw new IllegalStateException("Create must be the only compile-time hard dependency");
        if(find("irons_lib").isEmpty())throw new IllegalStateException("Iron's Lib must be in the target pack");
        if(!isPlayerOnly("mr_guns")||mayUseForLivingWorld("mr_guns"))throw new IllegalStateException("Guns++ must remain NPC-forbidden");
        if(!isPlayerOnly("gamingbarns_guns")||mayUseForLivingWorld("gamingbarns_guns"))throw new IllegalStateException("GamingBarn guns must remain NPC-forbidden");
        for(String removed:removedFromTargetPack())if(find(removed).isPresent())throw new IllegalStateException("removed mod present in target pack policy: "+removed);
    }
}
