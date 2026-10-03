package dev.livingrealms.minecraft.entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Loaded physical mobile-civilization projections; canonical records remain in SimulationState. */
public final class MobileCivilizationIndex {
    private static final Map<UUID,MobileCivilizationEntity> BY_UUID=new ConcurrentHashMap<>();
    private MobileCivilizationIndex(){}
    public static void joined(MobileCivilizationEntity entity){BY_UUID.put(entity.getUUID(),entity);} public static void left(MobileCivilizationEntity entity){BY_UUID.remove(entity.getUUID());}
    public static Collection<MobileCivilizationEntity> loaded(){BY_UUID.values().removeIf(MobileCivilizationEntity::isRemoved);return List.copyOf(BY_UUID.values());}
    public static MobileCivilizationEntity forSlot(MobileCivilizationKind kind,long id,int slot){return loaded().stream().filter(e->e.kind()==kind&&e.canonicalId()==id&&e.projectionSlot()==slot).findFirst().orElse(null);}
    public static void clear(){BY_UUID.clear();}
}
