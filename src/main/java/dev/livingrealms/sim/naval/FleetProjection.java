package dev.livingrealms.sim.naval;

import dev.livingrealms.sim.world.SimPosition;

public record FleetProjection(long fleetId,long factionId,int slot,ShipClass shipClass,int representedShips,SimPosition position) {
    public FleetProjection {if(fleetId<=0||factionId<=0||slot<0||shipClass==null||representedShips<1||position==null)throw new IllegalArgumentException("fleet projection");}
    public String projectionKey(){return fleetId+":"+slot;}
}
