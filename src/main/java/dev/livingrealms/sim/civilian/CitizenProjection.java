package dev.livingrealms.sim.civilian;

import dev.livingrealms.sim.world.SimPosition;

/** Desired one-slot physical representative of an aggregate settlement population. */
public record CitizenProjection(long factionId,long settlementId,int slot,CitizenRole role,SimPosition settlementCenter) {
    public CitizenProjection {
        if(factionId<=0||settlementId<=0||slot<0||role==null||settlementCenter==null)throw new IllegalArgumentException("citizen projection");
    }
    public String projectionKey(){return settlementId+":"+slot;}
}
