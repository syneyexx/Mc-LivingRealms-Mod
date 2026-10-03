package dev.livingrealms.minecraft.entity;

import dev.livingrealms.sim.materialization.PhysicalProjection;
import java.util.*;

/** Server-thread index of currently loaded physical wildlife projections. */
public final class WildlifeProjectionIndex {
    private static final Map<UUID, LivingRealmsAnimalEntity> LOADED = new LinkedHashMap<>();
    private WildlifeProjectionIndex() {}

    public static void joined(LivingRealmsAnimalEntity entity) { LOADED.put(entity.getUUID(), entity); }
    public static void left(LivingRealmsAnimalEntity entity) { LOADED.remove(entity.getUUID()); }
    public static void clear() { LOADED.clear(); }

    public static List<PhysicalProjection> snapshots() {
        List<PhysicalProjection> out = new ArrayList<>();
        Iterator<Map.Entry<UUID, LivingRealmsAnimalEntity>> iterator = LOADED.entrySet().iterator();
        while (iterator.hasNext()) {
            LivingRealmsAnimalEntity entity = iterator.next().getValue();
            if (entity.isRemoved()) { iterator.remove(); continue; }
            if (entity.populationGroupId() > 0 && entity.projectionSlot() >= 0 && !entity.speciesId().isBlank()) {
                out.add(new PhysicalProjection(entity.getUUID().toString(), entity.populationGroupId(), entity.speciesId(), entity.projectionSlot()));
            }
        }
        return List.copyOf(out);
    }

    public static LivingRealmsAnimalEntity byEntityKey(String key) {
        try { return LOADED.get(UUID.fromString(key)); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
