package dev.livingrealms.minecraft.entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Loaded projection index. Canonical population remains in Settlement. */
public final class FactionCitizenIndex {
    private static final Map<UUID,FactionCitizenEntity> BY_UUID=new ConcurrentHashMap<>();
    private FactionCitizenIndex(){}
    public static void joined(FactionCitizenEntity entity){BY_UUID.put(entity.getUUID(),entity);}
    public static void left(FactionCitizenEntity entity){BY_UUID.remove(entity.getUUID());}
    public static Collection<FactionCitizenEntity> loaded(){return List.copyOf(BY_UUID.values());}
    public static FactionCitizenEntity forSlot(long settlementId,int slot){return BY_UUID.values().stream().filter(e->e.settlementId()==settlementId&&e.projectionSlot()==slot&&!e.isRemoved()).findFirst().orElse(null);}
    public static FactionCitizenEntity forCitizen(long citizenId){return BY_UUID.values().stream().filter(e->e.citizenId()==citizenId&&!e.isRemoved()).findFirst().orElse(null);}
    public static void clear(){BY_UUID.clear();}
}
