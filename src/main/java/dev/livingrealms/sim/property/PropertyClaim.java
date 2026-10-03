package dev.livingrealms.sim.property;

import dev.livingrealms.sim.construction.StructureRole;
import dev.livingrealms.sim.world.SimPosition;

/** Derived ownership volume for a physically completed settlement structure. */
public record PropertyClaim(
        long factionId,
        long settlementId,
        String structureKey,
        StructureRole role,
        SimPosition center,
        int width,
        int depth
) {
    public PropertyClaim {
        if(factionId<=0||settlementId<=0||structureKey==null||structureKey.isBlank()||role==null||center==null||width<1||depth<1) throw new IllegalArgumentException("property claim");
    }
    public boolean contains(SimPosition point,double margin){
        if(point==null||!Double.isFinite(margin)||margin<0)return false;
        double hx=width*.5+margin,hz=depth*.5+margin;
        return Math.abs(point.x()-center.x())<=hx&&Math.abs(point.z()-center.z())<=hz;
    }
    public int footprint(){return width*depth;}
}
