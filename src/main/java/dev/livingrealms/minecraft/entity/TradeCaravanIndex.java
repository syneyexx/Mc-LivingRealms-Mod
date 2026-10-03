package dev.livingrealms.minecraft.entity;

import java.util.*;

/** Server-thread index; keyed by shipment so duplicate caravan projections can be reconciled. */
public final class TradeCaravanIndex {
    private static final Map<UUID,TradeCaravanEntity> LOADED=new LinkedHashMap<>();
    private TradeCaravanIndex(){}
    public static void joined(TradeCaravanEntity e){LOADED.put(e.getUUID(),e);} public static void left(TradeCaravanEntity e){LOADED.remove(e.getUUID());} public static void clear(){LOADED.clear();}
    public static Collection<TradeCaravanEntity> loaded(){LOADED.values().removeIf(TradeCaravanEntity::isRemoved);return List.copyOf(LOADED.values());}
    public static TradeCaravanEntity forShipment(long id){return loaded().stream().filter(e->e.shipmentId()==id).findFirst().orElse(null);}
}
