package dev.livingrealms.minecraft.compat;

import dev.livingrealms.LivingRealms;
import dev.livingrealms.sim.compat.ModCompatibilityPolicy;
import java.util.*;
import java.util.stream.Collectors;
import net.neoforged.fml.ModList;

/** Runtime-only target-pack detection. No optional mod classes are linked from this class. */
public final class ModCompatibilityRuntime {
    private ModCompatibilityRuntime(){}
    public static boolean loaded(String modId){return modId!=null&&ModList.get().isLoaded(modId);}
    public static boolean loaded(ModCompatibilityPolicy.Entry entry){return entry!=null&&entry.detectionIds().stream().anyMatch(ModCompatibilityRuntime::loaded);}

    public static void logDetectedPack(){
        ModCompatibilityPolicy.validate();
        String detected=ModCompatibilityPolicy.targets().stream().filter(ModCompatibilityRuntime::loaded).map(ModCompatibilityPolicy.Entry::displayName).collect(Collectors.joining(", "));
        LivingRealms.LOGGER.info("Living Realms target-pack scan: {}",detected.isBlank()?"no target mods detected":detected);

        List<String> missing=ModCompatibilityPolicy.targets().stream()
                .filter(e->e.strategy()==ModCompatibilityPolicy.Strategy.PACK_REQUIRED||e.strategy()==ModCompatibilityPolicy.Strategy.HARD_DEPENDENCY)
                .filter(e->!loaded(e)).map(ModCompatibilityPolicy.Entry::displayName).sorted().toList();
        if(!missing.isEmpty())LivingRealms.LOGGER.warn("Living Realms target pack is missing {} expected mod(s): {}. Registry fallbacks remain enabled where possible.",missing.size(),String.join(", ",missing));

        List<String> excludedLoaded=ModCompatibilityPolicy.removedFromTargetPack().stream().filter(ModCompatibilityRuntime::loaded).sorted().toList();
        if(!excludedLoaded.isEmpty())LivingRealms.LOGGER.warn("Excluded Create addon id(s) detected: {}. They are outside the tested Living Realms pack target.",String.join(", ",excludedLoaded));

        LivingRealms.LOGGER.info("Worldgen coexistence mode: biome source replacement={}, block-entity overwrite={}; NPC gun deny: Guns++={}, GamingBarn={}",
                ModCompatibilityPolicy.mayReplaceBiomeSource(),ModCompatibilityPolicy.mayOverwriteBlockEntities(),
                ModCompatibilityPolicy.isPlayerOnly("mr_guns"),ModCompatibilityPolicy.isPlayerOnly("gamingbarns_guns"));
    }
}
