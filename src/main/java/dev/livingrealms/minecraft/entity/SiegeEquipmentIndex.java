package dev.livingrealms.minecraft.entity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SiegeEquipmentIndex {
    private static final Map<UUID,SiegeEquipmentEntity> BY_UUID=new ConcurrentHashMap<>();
    private SiegeEquipmentIndex(){}
    public static void joined(SiegeEquipmentEntity e){BY_UUID.put(e.getUUID(),e);}
    public static void left(SiegeEquipmentEntity e){BY_UUID.remove(e.getUUID());}
    public static Collection<SiegeEquipmentEntity> loaded(){return List.copyOf(BY_UUID.values());}
    public static SiegeEquipmentEntity forSlot(long siegeId,int slot,String kind){
        return BY_UUID.values().stream()
                .filter(e->e.siegeId()==siegeId&&e.projectionSlot()==slot&&e.kind().name().equals(kind)&&!e.isRemoved())
                .findFirst().orElse(null);
    }
    public static void clear(){BY_UUID.clear();}
}
