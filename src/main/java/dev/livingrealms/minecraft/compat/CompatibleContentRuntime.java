package dev.livingrealms.minecraft.compat;

import dev.livingrealms.sim.compat.ModCompatibilityPolicy;
import dev.livingrealms.sim.construction.PaletteSlot;
import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.military.MilitaryUnitClass;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Registry-only integration with the user's fixed content pack.
 *
 * <p>No foreign Java API classes are linked here. That keeps renderer/worldgen libraries isolated,
 * while still allowing compatible armor/weapons/building blocks to visibly appear in Living Realms.
 * Combat mods are discovered through registries; custom weapon firing APIs remain isolated.</p>
 */
public final class CompatibleContentRuntime {
    private static volatile EnumMap<PaletteSlot,List<Block>> blockCandidates;
    private static volatile EnumMap<EquipmentSlot,List<Item>> equipmentCandidates;
    private static volatile List<Item> weaponCandidates;

    private CompatibleContentRuntime(){}

    public static Optional<BlockState> decorativeBlock(long factionId,PaletteSlot slot){
        if(!supportsForeignPalette(slot))return Optional.empty();
        ensureBlocks();List<Block> options=blockCandidates.getOrDefault(slot,List.of());
        if(options.isEmpty())return Optional.empty();
        int index=Math.floorMod(Objects.hash(factionId,slot.ordinal(),0x4C52),options.size());
        return Optional.of(options.get(index).defaultBlockState());
    }

    public static void equipCitizen(Mob entity,long factionId,CitizenRole role,int projectionSlot){
        if(entity==null||role==null)return;
        if(role!=CitizenRole.GUARD&&role!=CitizenRole.OFFICIAL&&role!=CitizenRole.HUNTER&&role!=CitizenRole.LUMBERJACK&&role!=CitizenRole.PRIEST&&role!=CitizenRole.SCHOLAR)return;
        ensureEquipment();
        if(role==CitizenRole.GUARD||role==CitizenRole.HUNTER){
            Item weapon=pick(weaponCandidates,factionId,projectionSlot,31).orElse(Items.IRON_SWORD);
            entity.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(weapon));
            equip(entity,EquipmentSlot.HEAD,factionId,projectionSlot,37);
            equip(entity,EquipmentSlot.CHEST,factionId,projectionSlot,41);
            equip(entity,EquipmentSlot.LEGS,factionId,projectionSlot,43);
            equip(entity,EquipmentSlot.FEET,factionId,projectionSlot,47);
        }else if(role==CitizenRole.LUMBERJACK){
            pickMatchingWeapon(factionId,projectionSlot,59,"axe").ifPresentOrElse(
                    item->entity.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(item)),
                    ()->entity.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE)));
        }else{
            // Officials, priests and scholars can visibly carry compatible fantasy/magic staves.
            // Actual spell casting stays behind an isolated runtime adapter instead of being faked.
            List<Item> magic=weaponCandidates.stream().filter(CompatibleContentRuntime::looksMagical).toList();
            pick(magic,factionId,projectionSlot,53+role.ordinal()).ifPresent(item->entity.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(item)));
        }
    }

    /** Safe visible pirate equipment. Player-only gun namespaces remain excluded by usableEquipmentNamespace(). */
    public static void equipPirate(Mob entity,long originFactionId,int projectionSlot){
        if(entity==null)return;
        ensureEquipment();
        Item weapon=pick(weaponCandidates,originFactionId,projectionSlot,67).orElse((projectionSlot&1)==0?Items.IRON_SWORD:Items.CROSSBOW);
        entity.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(weapon));
        equip(entity,EquipmentSlot.HEAD,originFactionId,projectionSlot,69);
        equip(entity,EquipmentSlot.CHEST,originFactionId,projectionSlot,73);
    }

    public static void equipMilitary(Mob entity,long factionId,MilitaryUnitClass unitClass,int projectionSlot){
        if(entity==null||unitClass==null)return;
        ensureEquipment();
        Item fallback=switch(unitClass){case ARTILLERY->Items.CROSSBOW;case CAVALRY->Items.IRON_SWORD;case ARMOR->Items.IRON_AXE;default->Items.IRON_SWORD;};
        Item weapon=pick(weaponCandidates,factionId,projectionSlot,71+unitClass.ordinal()*13).orElse(fallback);
        entity.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(weapon));
        equip(entity,EquipmentSlot.HEAD,factionId,projectionSlot,79);
        equip(entity,EquipmentSlot.CHEST,factionId,projectionSlot,83);
        equip(entity,EquipmentSlot.LEGS,factionId,projectionSlot,89);
        equip(entity,EquipmentSlot.FEET,factionId,projectionSlot,97);
    }

    private static Optional<Item> pickMatchingWeapon(long factionId,int projectionSlot,int salt,String token){
        ensureEquipment();
        List<Item> matches=weaponCandidates.stream().filter(item->{ResourceLocation id=BuiltInRegistries.ITEM.getKey(item);return id!=null&&id.getPath().toLowerCase(Locale.ROOT).contains(token);}).toList();
        return pick(matches,factionId,projectionSlot,salt);
    }

    private static void equip(Mob entity,EquipmentSlot slot,long factionId,int projectionSlot,int salt){
        pick(equipmentCandidates.getOrDefault(slot,List.of()),factionId,projectionSlot,salt)
                .ifPresent(item->entity.setItemSlot(slot,new ItemStack(item)));
    }

    private static <T> Optional<T> pick(List<T> values,long factionId,int projectionSlot,int salt){
        if(values==null||values.isEmpty())return Optional.empty();
        int index=Math.floorMod(Objects.hash(factionId,projectionSlot,salt),values.size());
        return Optional.of(values.get(index));
    }

    private static synchronized void ensureBlocks(){
        if(blockCandidates!=null)return;
        EnumMap<PaletteSlot,List<Block>> out=new EnumMap<>(PaletteSlot.class);
        for(PaletteSlot slot:PaletteSlot.values())out.put(slot,new ArrayList<>());
        for(Block block:BuiltInRegistries.BLOCK){
            ResourceLocation id=BuiltInRegistries.BLOCK.getKey(block);if(id==null||!usableBuildingNamespace(id.getNamespace()))continue;
            BlockState state=block.defaultBlockState();if(state.hasBlockEntity())continue;
            String path=id.getPath().toLowerCase(Locale.ROOT);
            for(PaletteSlot slot:List.of(PaletteSlot.FOUNDATION,PaletteSlot.FLOOR,PaletteSlot.WALL,PaletteSlot.BEAM,PaletteSlot.ROOF,PaletteSlot.GLASS,PaletteSlot.FENCE,PaletteSlot.PATH,PaletteSlot.LIGHT))
                if(matchesBlock(slot,path,state))out.get(slot).add(block);
        }
        for(var list:out.values())list.sort(Comparator.comparing(b->String.valueOf(BuiltInRegistries.BLOCK.getKey(b))));
        EnumMap<PaletteSlot,List<Block>> frozen=new EnumMap<>(PaletteSlot.class);for(var e:out.entrySet())frozen.put(e.getKey(),List.copyOf(e.getValue()));
        blockCandidates=frozen;
    }

    private static synchronized void ensureEquipment(){
        if(equipmentCandidates!=null&&weaponCandidates!=null)return;
        EnumMap<EquipmentSlot,List<Item>> armor=new EnumMap<>(EquipmentSlot.class);
        for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))armor.put(slot,new ArrayList<>());
        List<Item> weapons=new ArrayList<>();
        for(Item item:BuiltInRegistries.ITEM){
            ResourceLocation id=BuiltInRegistries.ITEM.getKey(item);if(id==null||!usableEquipmentNamespace(id.getNamespace()))continue;
            String path=id.getPath().toLowerCase(Locale.ROOT);
            if(weaponPath(path))weapons.add(item);
            EquipmentSlot slot=armorSlot(path);if(slot!=null)armor.get(slot).add(item);
        }
        Comparator<Item> byId=Comparator.comparing(i->String.valueOf(BuiltInRegistries.ITEM.getKey(i)));
        weapons.sort(byId);for(var list:armor.values())list.sort(byId);
        EnumMap<EquipmentSlot,List<Item>> frozen=new EnumMap<>(EquipmentSlot.class);for(var e:armor.entrySet())frozen.put(e.getKey(),List.copyOf(e.getValue()));
        equipmentCandidates=frozen;weaponCandidates=List.copyOf(weapons);
    }

    private static boolean usableBuildingNamespace(String namespace){
        return ModCompatibilityPolicy.find(namespace).map(e->e.usableByLivingWorld()&&(e.category()==ModCompatibilityPolicy.Category.BUILDING||e.category()==ModCompatibilityPolicy.Category.CONTENT)).orElse(false);
    }
    private static boolean usableEquipmentNamespace(String namespace){
        if(namespace==null||namespace.equals("minecraft"))return false;
        String id=namespace.toLowerCase(Locale.ROOT);
        if(id.contains("gamingbarn")||id.equals("gbguns")||id.equals("mr_guns")||id.contains("gunsplusplus")||id.contains("guns_plus_plus")||ModCompatibilityPolicy.isPlayerOnly(namespace))return false;
        return ModCompatibilityPolicy.find(namespace).map(e->e.usableByLivingWorld()&&(e.category()==ModCompatibilityPolicy.Category.COMBAT||e.category()==ModCompatibilityPolicy.Category.MAGIC||e.category()==ModCompatibilityPolicy.Category.CONTENT)).orElse(true);
    }
    private static boolean supportsForeignPalette(PaletteSlot slot){return switch(slot){
        case FOUNDATION,FLOOR,WALL,BEAM,ROOF,GLASS,FENCE,PATH,LIGHT -> true;
        default -> false;
    };}
    private static boolean matchesBlock(PaletteSlot slot,String path,BlockState state){
        return switch(slot){
            case FOUNDATION -> structural(path,"brick","stone","cobble","tile","slate","marble","limestone","concrete")&&!thinOrInteractive(path);
            case FLOOR -> structural(path,"plank","floor","tile","parquet","brick","stone")&&!thinOrInteractive(path);
            case WALL -> structural(path,"brick","plank","panel","stone","timber","marble","limestone")&&!thinOrInteractive(path);
            case BEAM -> structural(path,"beam","log","timber","pillar","post")&&!thinOrInteractive(path);
            case GLASS -> path.contains("window")||path.contains("glass");
            case FENCE -> path.contains("fence")||path.contains("railing")||path.contains("wall");
            case ROOF -> path.contains("roof")||path.contains("shingle")||path.contains("thatch");
            case PATH -> path.contains("path")||path.contains("paving")||path.contains("pavement")||path.contains("road");
            case LIGHT -> (path.contains("lamp")||path.contains("light")||path.contains("lantern"))&&state.getLightEmission()>0;
            default -> false;
        };
    }
    private static boolean weaponPath(String path){return path.contains("sword")||path.contains("spear")||path.contains("mace")||path.contains("halberd")||path.contains("dagger")||path.contains("katana")||path.contains("staff")||path.contains("wand")||path.contains("bow")||path.contains("crossbow")||path.contains("rapier")||path.contains("glaive")||path.contains("scythe")||path.contains("hammer")||path.contains("axe")||path.contains("spellbook")||path.contains("gun")||path.contains("rifle")||path.contains("pistol")||path.contains("revolver")||path.contains("musket")||path.contains("carbine")||path.contains("shotgun");}
    private static boolean structural(String path,String...tokens){for(String token:tokens)if(path.contains(token))return true;return false;}
    private static boolean thinOrInteractive(String path){return path.contains("slab")||path.contains("stairs")||path.contains("fence")||path.contains("wall")||path.contains("door")||path.contains("trapdoor")||path.contains("button")||path.contains("pressure_plate")||path.contains("sign")||path.contains("gate")||path.contains("window")||path.contains("pane")||path.contains("powder");}
    private static EquipmentSlot armorSlot(String path){
        if(path.contains("helmet")||path.endsWith("_hood"))return EquipmentSlot.HEAD;
        if(path.contains("chestplate")||path.endsWith("_chest")||path.contains("robe"))return EquipmentSlot.CHEST;
        if(path.contains("leggings")||path.endsWith("_legs"))return EquipmentSlot.LEGS;
        if(path.contains("boots"))return EquipmentSlot.FEET;
        return null;
    }
    private static boolean looksMagical(Item item){ResourceLocation id=BuiltInRegistries.ITEM.getKey(item);if(id==null)return false;String p=id.getPath().toLowerCase(Locale.ROOT);return p.contains("staff")||p.contains("wand")||p.contains("spell");}
}
