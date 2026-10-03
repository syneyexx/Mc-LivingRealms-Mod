package dev.livingrealms.sim.property;

import dev.livingrealms.sim.construction.*;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;

/** Derives faction property from completed settlement construction; no per-block ownership save is required. */
public final class PropertyRightsEngine {
    private PropertyRightsEngine() {}

    public static Optional<PropertyClaim> resolve(Collection<Faction> factions,SimPosition position){return resolve(factions,position,.75);}
    public static Optional<PropertyClaim> resolve(Collection<Faction> factions,SimPosition position,double margin){
        Objects.requireNonNull(factions,"factions");Objects.requireNonNull(position,"position");
        if(!Double.isFinite(margin)||margin<0)throw new IllegalArgumentException("margin");
        PropertyClaim best=null;
        for(Faction faction:factions) for(var settlement:faction.settlements()) for(var intent:SettlementPlanner.plan(faction,settlement)){
            if(!settlement.isConstructionCompleted(intent.key()))continue;
            PropertyClaim claim=new PropertyClaim(faction.id(),settlement.id(),intent.key(),intent.role(),intent.center(),intent.width(),intent.depth());
            if(!claim.contains(position,margin))continue;
            if(best==null||claim.footprint()<best.footprint()||(claim.footprint()==best.footprint()&&claim.structureKey().compareTo(best.structureKey())<0))best=claim;
        }
        return Optional.ofNullable(best);
    }

    /** Resolves only storage blocks authored by a completed Living Realms blueprint. */
    public static Optional<PropertyClaim> resolveStorage(Collection<Faction> factions,SimPosition position){
        Objects.requireNonNull(factions,"factions");Objects.requireNonNull(position,"position");
        PropertyClaim best=null;
        for(Faction faction:factions) for(var settlement:faction.settlements()) for(var intent:SettlementPlanner.plan(faction,settlement)){
            if(!settlement.isConstructionCompleted(intent.key()))continue;
            StructureBlueprint blueprint=StructureBlueprintFactory.create(intent);
            boolean matches=blueprint.placements().stream().filter(p->p.slot()==PaletteSlot.STORAGE).anyMatch(p->{
                double x=intent.center().x()+p.dx()+.5,z=intent.center().z()+p.dz()+.5;
                return Math.abs(position.x()-x)<=.75&&Math.abs(position.z()-z)<=.75;
            });
            if(!matches)continue;
            PropertyClaim claim=new PropertyClaim(faction.id(),settlement.id(),intent.key(),intent.role(),intent.center(),intent.width(),intent.depth());
            if(best==null||claim.footprint()<best.footprint()||(claim.footprint()==best.footprint()&&claim.structureKey().compareTo(best.structureKey())<0))best=claim;
        }
        return Optional.ofNullable(best);
    }
}
