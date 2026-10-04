package dev.livingrealms.minecraft.compat;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.civilian.CivilianRoleInference;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;

/**
 * Allowlist/adapter gate for adopting foreign civilian humanoids into Living Realms dialogue/social
 * identity. Hostile monsters are never adopted just because they look humanoid.
 */
public final class CivilianNpcAdoption {
    private static final Set<String> ALLOWED_NAMESPACES=Set.of(
            "minecraft",
            "bettervillages",
            "better_villages",
            "guardvillagers",
            "immersive_melodies",
            "medieval_buildings"
    );
    private static final Set<String> ALLOWED_PATH_HINTS=Set.of(
            "villager","villager_", "nitwit","wanderer","civilian","townsperson","townsfolk","peasant","merchant","trader","citizen",
            "farmer","guard","priest","cleric","blacksmith","miner","fisher","baker","brewer","scholar","librarian"
    );
    private static final Set<String> DENIED_PATH_HINTS=Set.of(
            "zombie","skeleton","pillager","vindicator","evoker","ravager","witch","illusioner","piglin","hoglin","warden","raider"
    );

    private CivilianNpcAdoption(){}

    public static boolean isAdoptableCivilian(Entity entity){
        if(!(entity instanceof LivingEntity living)||!living.isAlive())return false;
        if(entity instanceof AbstractVillager)return true;
        if(entity instanceof Enemy)return false;
        if(!(entity instanceof Mob))return false;
        ResourceLocation id=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if(id==null)return false;
        String ns=id.getNamespace().toLowerCase(Locale.ROOT);
        String path=id.getPath().toLowerCase(Locale.ROOT);
        for(String denied:DENIED_PATH_HINTS)if(path.contains(denied))return false;
        if(!ALLOWED_NAMESPACES.contains(ns)&&!ns.contains("village")&&!ns.contains("town")&&!ns.contains("civil"))return false;
        for(String hint:ALLOWED_PATH_HINTS)if(path.contains(hint))return true;
        // Namespace allowlisted villagers/civilians with generic names still need a humanoid-ish path.
        return ALLOWED_NAMESPACES.contains(ns)&&(path.contains("npc")||path.contains("person")||path.contains("human"));
    }

    /** Infer a Living Realms role from entity registry path / custom name. */
    public static CitizenRole inferRole(Entity entity){
        if(entity==null)return CitizenRole.TRADER;
        ResourceLocation id=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String path=id==null?"":id.getPath();
        String name=entity.hasCustomName()?entity.getCustomName().getString():"";
        return CivilianRoleInference.fromSignals(path,name);
    }
}
