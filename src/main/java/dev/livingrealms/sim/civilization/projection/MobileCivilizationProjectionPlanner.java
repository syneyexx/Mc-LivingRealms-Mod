package dev.livingrealms.sim.civilization.projection;

import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Pure LOD planner for traveling refugees/migrants and pirate bands. It never mutates canonical state. */
public final class MobileCivilizationProjectionPlanner {
    private MobileCivilizationProjectionPlanner(){}
    public static List<MigrationProjection> migrations(SimulationState state,Collection<SimPosition> players,double radius,int budget){
        Objects.requireNonNull(state);Objects.requireNonNull(players);check(radius,budget);if(players.isEmpty()||budget==0)return List.of();
        List<GroupCandidate> candidates=new ArrayList<>();
        for(MigrationGroup group:state.migrationGroups())if(group.status()==MigrationStatus.TRAVELING&&group.people()>0){
            Settlement source=state.findSettlement(group.sourceSettlementId()).orElse(null),target=group.targetSettlementId()>0?state.findSettlement(group.targetSettlementId()).orElse(null):null;if(source==null||target==null)continue;
            SimPosition base=source.position().lerp(target.position(),group.progress());double distance=nearest(base,players);if(distance<=radius)candidates.add(new GroupCandidate(group,base,distance));
        }
        candidates.sort(Comparator.comparingDouble(GroupCandidate::distance).thenComparingLong(c->c.group().id()));List<MigrationProjection> out=new ArrayList<>();int remaining=budget;
        for(GroupCandidate c:candidates){int visuals=Math.min(4,Math.max(1,(int)Math.ceil(c.group().people()/35.0)));visuals=Math.min(visuals,remaining);if(visuals<=0)break;int left=c.group().people();for(int slot=0;slot<visuals;slot++){int represented=Math.max(1,(int)Math.ceil(left/(double)(visuals-slot)));left-=represented;out.add(new MigrationProjection(c.group().id(),c.group().originFactionId(),slot,represented,c.group().reason(),offset(c.base(),c.group().id(),slot,4.5)));}remaining-=visuals;if(remaining<=0)break;}
        return List.copyOf(out);
    }
    public static List<PirateProjection> pirates(SimulationState state,Collection<SimPosition> players,double radius,int budget){
        Objects.requireNonNull(state);Objects.requireNonNull(players);check(radius,budget);if(players.isEmpty()||budget==0)return List.of();
        List<PirateCandidate> candidates=new ArrayList<>();for(PirateBand band:state.pirateBands())if(band.active()){double distance=nearest(band.position(),players);if(distance<=radius){long faction=state.findSettlementOwner(band.originSettlementId()).map(f->f.id()).orElse(0L);if(faction>0)candidates.add(new PirateCandidate(band,faction,distance));}}
        candidates.sort(Comparator.comparingDouble(PirateCandidate::distance).thenComparingLong(c->c.band().id()));List<PirateProjection> out=new ArrayList<>();int remaining=budget;
        for(PirateCandidate c:candidates){int visuals=Math.min(4,Math.max(1,(int)Math.ceil(c.band().strength()/8.0)));visuals=Math.min(visuals,remaining);if(visuals<=0)break;int left=c.band().strength();for(int slot=0;slot<visuals;slot++){int represented=Math.max(1,(int)Math.ceil(left/(double)(visuals-slot)));left-=represented;out.add(new PirateProjection(c.band().id(),c.originFactionId(),slot,represented,offset(c.band().position(),c.band().id(),slot,5.5)));}remaining-=visuals;if(remaining<=0)break;}
        return List.copyOf(out);
    }
    private static void check(double radius,int budget){if(!Double.isFinite(radius)||radius<=0||budget<0)throw new IllegalArgumentException("projection config");}
    private static double nearest(SimPosition p,Collection<SimPosition> players){double best=Double.POSITIVE_INFINITY;for(SimPosition player:players)best=Math.min(best,p.distanceTo(player));return best;}
    private static SimPosition offset(SimPosition base,long id,int slot,double radius){double a=Math.toRadians(Math.floorMod(Objects.hash(id,slot),360));double r=radius*(.45+.55*Math.floorMod(Objects.hash(slot,id),100)/99.0);return new SimPosition(base.x()+Math.cos(a)*r,base.z()+Math.sin(a)*r);}
    private record GroupCandidate(MigrationGroup group,SimPosition base,double distance){}
    private record PirateCandidate(PirateBand band,long originFactionId,double distance){}
}
