package dev.livingrealms.minecraft.entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks only loaded physical bounty-hunter projections. Canonical contracts live in SimulationState. */
public final class BountyHunterIndex {
    private static final Map<UUID,BountyHunterEntity> BY_UUID=new ConcurrentHashMap<>();
    private BountyHunterIndex(){}
    public static void joined(BountyHunterEntity entity){BY_UUID.put(entity.getUUID(),entity);}
    public static void left(BountyHunterEntity entity){BY_UUID.remove(entity.getUUID());}
    public static Collection<BountyHunterEntity> loaded(){return List.copyOf(BY_UUID.values());}
    public static List<BountyHunterEntity> forContract(long contractId){return BY_UUID.values().stream().filter(e->e.contractId()==contractId&&!e.isRemoved()).toList();}
    public static void clear(){BY_UUID.clear();}
}
