package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimPosition;

/** Persistent home base for one pirate band. The band remains the mobile force; the hideout stores loot, recovery and discovery state. */
public final class PirateHideout {
    private final long id,bandId,originSettlementId,createdDay;
    private final SimPosition position;
    private double defense=.35,storedLoot;
    private long discoveredByFactionId;
    private boolean active=true,destroyed;
    public PirateHideout(long id,long bandId,long originSettlementId,long createdDay,SimPosition position){if(id<=0||bandId<=0||originSettlementId<=0||createdDay<0||position==null)throw new IllegalArgumentException("pirate hideout");this.id=id;this.bandId=bandId;this.originSettlementId=originSettlementId;this.createdDay=createdDay;this.position=position;}
    public long id(){return id;} public long bandId(){return bandId;} public long originSettlementId(){return originSettlementId;} public long createdDay(){return createdDay;} public SimPosition position(){return position;} public double defense(){return defense;} public double storedLoot(){return storedLoot;} public long discoveredByFactionId(){return discoveredByFactionId;} public boolean active(){return active&&!destroyed;} public boolean destroyed(){return destroyed;}
    public void addLoot(double value){if(value>0&&Double.isFinite(value))storedLoot+=value;}
    public double takeLoot(double value){double taken=Math.min(storedLoot,Math.max(0,value));storedLoot-=taken;return taken;}
    public void adjustDefense(double delta){defense=Mathx.clamp(defense+delta,0,1);}
    public void discover(long factionId){if(factionId>0)discoveredByFactionId=factionId;}
    public void destroy(){destroyed=true;active=false;defense=0;}
    public void restore(double defense,double storedLoot,long discoveredByFactionId,boolean active,boolean destroyed){if(!Double.isFinite(storedLoot)||storedLoot<0)throw new IllegalArgumentException("pirate hideout restore");this.defense=Mathx.clamp(defense,0,1);this.storedLoot=storedLoot;this.discoveredByFactionId=Math.max(0,discoveredByFactionId);this.active=active;this.destroyed=destroyed;}
}
