package dev.livingrealms.sim.civilization.projection;

import dev.livingrealms.sim.world.SimPosition;

/** One bounded physical representative of a canonical pirate band. */
public record PirateProjection(long bandId,long originFactionId,int slot,int representedPirates,SimPosition position) {
    public PirateProjection {if(bandId<=0||originFactionId<=0||slot<0||representedPirates<=0||position==null)throw new IllegalArgumentException("pirate projection");}
    public String projectionKey(){return "pirate:"+bandId+":"+slot;}
}
