package dev.livingrealms.sim.military;

import dev.livingrealms.sim.world.SimPosition;
import java.util.Objects;

/** Bounded near-player projection request for one siege machine. */
public final class SiegeEquipmentProjection {
    private final long siegeId;
    private final long attackerFactionId;
    private final long settlementId;
    private final int slot;
    private final SiegeEquipmentKind kind;
    private final SimPosition position;

    public SiegeEquipmentProjection(long siegeId,long attackerFactionId,long settlementId,int slot,SiegeEquipmentKind kind,SimPosition position){
        if(siegeId<=0||attackerFactionId<=0||settlementId<=0||slot<0||kind==null||position==null)throw new IllegalArgumentException("siege equipment projection");
        this.siegeId=siegeId;this.attackerFactionId=attackerFactionId;this.settlementId=settlementId;this.slot=slot;this.kind=kind;this.position=position;
    }
    public long siegeId(){return siegeId;} public long attackerFactionId(){return attackerFactionId;} public long settlementId(){return settlementId;}
    public int slot(){return slot;} public SiegeEquipmentKind kind(){return kind;} public SimPosition position(){return position;}
    public String projectionKey(){return siegeId+":"+slot+":"+kind.name();}
    @Override public boolean equals(Object o){return o instanceof SiegeEquipmentProjection p&&p.siegeId==siegeId&&p.slot==slot&&p.kind==kind;}
    @Override public int hashCode(){return Objects.hash(siegeId,slot,kind);}
}
