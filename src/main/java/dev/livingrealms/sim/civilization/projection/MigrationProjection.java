package dev.livingrealms.sim.civilization.projection;

import dev.livingrealms.sim.civilization.MigrationReason;
import dev.livingrealms.sim.world.SimPosition;

/** One bounded physical representative of a canonical traveling migration group. */
public record MigrationProjection(long groupId,long factionId,int slot,int representedPeople,MigrationReason reason,SimPosition position) {
    public MigrationProjection {if(groupId<=0||factionId<=0||slot<0||representedPeople<=0||reason==null||position==null)throw new IllegalArgumentException("migration projection");}
    public String projectionKey(){return "migration:"+groupId+":"+slot;}
}
