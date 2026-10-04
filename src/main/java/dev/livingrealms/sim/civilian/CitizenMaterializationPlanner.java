package dev.livingrealms.sim.civilian;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.SimPosition;
import java.util.*;

/**
 * Selects a bounded, deterministic set of citizen representatives around players. A physical NPC
 * is not the canonical citizen count: the settlement remains authoritative.
 */
public final class CitizenMaterializationPlanner {
    private CitizenMaterializationPlanner(){}

    public static List<CitizenProjection> plan(Collection<Faction> factions,Collection<SimPosition> players,double radius,int globalBudget){
        Objects.requireNonNull(factions,"factions");Objects.requireNonNull(players,"players");
        if(radius<=0||!Double.isFinite(radius)||globalBudget<0)throw new IllegalArgumentException("planner config");
        if(players.isEmpty()||globalBudget==0)return List.of();
        List<Candidate> candidates=new ArrayList<>();
        for(Faction faction:factions)for(Settlement settlement:faction.settlements()){
            double distance=nearest(settlement.position(),players);if(distance>radius)continue;
            // A physical citizen is still only a representative, but settlements should visibly
            // feel inhabited. Scale sub-linearly with population so cities look busy without
            // turning canonical population into thousands of entities.
            boolean capital=isCapital(faction,settlement);
            int desired=Math.min(capital?48:42,Math.max(capital?8:5,(int)Math.ceil(4.0+Math.sqrt(Math.max(1,settlement.population()))*.78+(capital?4:0))));
            candidates.add(new Candidate(faction,settlement,distance,desired));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance).thenComparingLong(c->c.settlement().id()));
        List<CitizenProjection> out=new ArrayList<>();int remaining=globalBudget;
        for(Candidate candidate:candidates){int allowed=Math.min(candidate.desired(),remaining);for(int slot=0;slot<allowed;slot++)out.add(new CitizenProjection(candidate.faction().id(),candidate.settlement().id(),slot,roleFor(candidate.faction(),candidate.settlement(),slot,allowed),candidate.settlement().position()));remaining-=allowed;if(remaining<=0)break;}
        return List.copyOf(out);
    }

    private static CitizenRole roleFor(Faction faction,Settlement settlement,int slot,int count){
        int guards=Math.max(1,(int)Math.ceil(count*(.10+faction.government().lawEnforcement()*.22)));
        if(slot<guards)return CitizenRole.GUARD;
        boolean capital=isCapital(faction,settlement)&&settlement.tier().ordinal()>=Settlement.Tier.TOWN.ordinal();
        // Capitals materialize a small court cluster at the keep: ruler-proxy official, spouse/heir proxies, then guards already above.
        if(capital){
            int courtIndex=slot-guards;
            if(courtIndex==0)return CitizenRole.OFFICIAL; // ruler / regent representative
            if(courtIndex==1)return CitizenRole.OFFICIAL; // heir / consort representative
            if(courtIndex==2)return CitizenRole.GUARD;    // court guard
            if(courtIndex==3)return CitizenRole.PRIEST;   // court chaplain when temples exist, else falls through
        }
        List<CitizenRole> roles=new ArrayList<>(List.of(CitizenRole.FARMER,CitizenRole.HUNTER,CitizenRole.ARTISAN,CitizenRole.TRADER,CitizenRole.BUILDER,CitizenRole.OFFICIAL));
        if(settlement.completedConstruction().stream().anyMatch(k->k.startsWith("mine:")))roles.add(CitizenRole.MINER);
        if(settlement.completedConstruction().stream().anyMatch(k->k.startsWith("lumber_camp:"))){roles.add(CitizenRole.LUMBERJACK);roles.add(CitizenRole.CARPENTER);}
        if(settlement.completedConstruction().stream().anyMatch(k->k.startsWith("fishery:"))){roles.add(CitizenRole.FISHER);roles.add(CitizenRole.SAILOR);}
        if(settlement.completedConstruction().stream().anyMatch(k->k.startsWith("clinic:")))roles.add(CitizenRole.HEALER);
        if(settlement.completedConstruction().stream().anyMatch(k->k.startsWith("market:")||k.startsWith("warehouse:")))roles.add(CitizenRole.BUTCHER);
        if(settlement.completedConstruction().stream().anyMatch(k->k.startsWith("temple:")))roles.add(CitizenRole.PRIEST);
        if(settlement.completedConstruction().stream().anyMatch(k->k.startsWith("school:")||k.startsWith("observatory:"))){roles.add(CitizenRole.SCHOLAR);roles.add(CitizenRole.TEACHER);}
        int v=Math.floorMod((int)(faction.id()*31+settlement.id()*13+slot*17),roles.size());
        return roles.get(v);
    }

    private static boolean isCapital(Faction faction,Settlement settlement){
        return faction.settlements().stream().max(Comparator.comparingInt(Settlement::population).thenComparingLong(Settlement::id)).map(s->s.id()==settlement.id()).orElse(false);
    }
    private static double nearest(SimPosition p,Collection<SimPosition> players){double best=Double.POSITIVE_INFINITY;for(SimPosition player:players)best=Math.min(best,p.distanceTo(player));return best;}
    private record Candidate(Faction faction,Settlement settlement,double distance,int desired){}
}
