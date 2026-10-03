package dev.livingrealms.sim.aviation;
import dev.livingrealms.sim.world.SimPosition;
public record AircraftProjection(long wingId,long factionId,int slot,AircraftModel model,SimPosition position){public AircraftProjection{if(wingId<=0||factionId<=0||slot<0||model==null||position==null)throw new IllegalArgumentException("aircraft projection");}public String projectionKey(){return wingId+":"+slot;}}
