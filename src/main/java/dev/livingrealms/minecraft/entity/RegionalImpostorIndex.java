package dev.livingrealms.minecraft.entity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionalImpostorIndex {
    private static final Map<UUID, RegionalImpostorEntity> BY_UUID = new ConcurrentHashMap<>();

    private RegionalImpostorIndex() {}

    public static void joined(RegionalImpostorEntity e) { BY_UUID.put(e.getUUID(), e); }
    public static void left(RegionalImpostorEntity e) { BY_UUID.remove(e.getUUID()); }
    public static Collection<RegionalImpostorEntity> loaded() { return List.copyOf(BY_UUID.values()); }

    public static RegionalImpostorEntity forKey(String key) {
        return BY_UUID.values().stream().filter(e -> e.projectionKey().equals(key) && !e.isRemoved()).findFirst().orElse(null);
    }

    public static void clear() { BY_UUID.clear(); }
}
