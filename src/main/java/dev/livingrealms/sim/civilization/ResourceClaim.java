package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.SimPosition;
import java.util.Objects;

/** Strategic catchment claim. Physical blocks remain owned by Minecraft/other mods; this records political interest only. */
public final class ResourceClaim {
    private final long id,settlementId; private long factionId; private final ResourceClaimType type; private final SimPosition position; private double strength; private long contestedByFactionId; private boolean active=true;
    public ResourceClaim(long id,long factionId,long settlementId,ResourceClaimType type,SimPosition position,double strength){if(id<=0||factionId<=0||settlementId<=0)throw new IllegalArgumentException("resource claim");this.id=id;this.factionId=factionId;this.settlementId=settlementId;this.type=Objects.requireNonNull(type);this.position=Objects.requireNonNull(position);this.strength=unit(strength);}
    public long id(){return id;} public long factionId(){return factionId;} public long settlementId(){return settlementId;} public ResourceClaimType type(){return type;} public SimPosition position(){return position;} public double strength(){return strength;} public long contestedByFactionId(){return contestedByFactionId;} public boolean active(){return active;}
    public void setFactionId(long value){if(value<=0)throw new IllegalArgumentException("claim faction");factionId=value;} public void setStrength(double value){strength=unit(value);} public void setContestedByFactionId(long value){if(value<0||value==factionId)throw new IllegalArgumentException("contested faction");contestedByFactionId=value;} public void deactivate(){active=false;}
    public void restore(double strength,long contestedBy,boolean active){this.strength=unit(strength);this.contestedByFactionId=Math.max(0,contestedBy);this.active=active;}
    private static double unit(double v){if(!Double.isFinite(v))throw new IllegalArgumentException("claim strength");return Mathx.clamp(v,0,1);}
}
