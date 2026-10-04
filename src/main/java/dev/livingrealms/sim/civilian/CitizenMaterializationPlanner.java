package dev.livingrealms.sim.civilian;

import dev.livingrealms.sim.civilization.DynastyState;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.*;

/**
 * Selects a bounded, deterministic set of citizen representatives around players. A physical NPC
 * is not the canonical citizen count: the settlement remains authoritative.
 */
public final class CitizenMaterializationPlanner {
    private CitizenMaterializationPlanner(){}

    public static List<CitizenProjection> plan(Collection<Faction> factions,Collection<SimPosition> players,double radius,int globalBudget){
        return plan(null,factions,players,radius,globalBudget);
    }

    /** State-aware plan binds capital court slots to dynasty ruler/heir projection identities when present. */
    public static List<CitizenProjection> plan(SimulationState state,Collection<Faction> factions,Collection<SimPosition> players,double radius,int globalBudget){
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
        for(Candidate candidate:candidates){
            int allowed=Math.min(candidate.desired(),remaining);
            if(allowed<=0)break;
            Set<Integer> usedSlots=new HashSet<>();
            int emitted=0;
            // Bind dynasty court identities first so capitals project the real ruler/heir, not proxies.
            if(state!=null&&isCapital(candidate.faction(),candidate.settlement())&&candidate.settlement().tier().ordinal()>=Settlement.Tier.TOWN.ordinal()){
                DynastyState dynasty=state.dynasties().get(candidate.faction().id());
                if(dynasty!=null){
                    SocialCitizen ruler=dynasty.rulerCitizenId()>0?state.findSocialCitizen(dynasty.rulerCitizenId()).filter(SocialCitizen::alive).orElse(null):null;
                    SocialCitizen heir=dynasty.heirCitizenId()>0?state.findSocialCitizen(dynasty.heirCitizenId()).filter(SocialCitizen::alive).orElse(null):null;
                    if(emitted<allowed&&ruler!=null&&ruler.settlementId()==candidate.settlement().id()&&usedSlots.add(ruler.projectionSlot())){
                        out.add(new CitizenProjection(candidate.faction().id(),candidate.settlement().id(),ruler.projectionSlot(),ruler.role()==CitizenRole.PRIEST?CitizenRole.PRIEST:CitizenRole.OFFICIAL,candidate.settlement().position()));
                        emitted++;
                    }
                    if(emitted<allowed&&heir!=null&&heir.settlementId()==candidate.settlement().id()&&heir.id()!=(ruler==null?0:ruler.id())&&usedSlots.add(heir.projectionSlot())){
                        out.add(new CitizenProjection(candidate.faction().id(),candidate.settlement().id(),heir.projectionSlot(),CitizenRole.OFFICIAL,candidate.settlement().position()));
                        emitted++;
                    }
                }
            }
            for(int slot=0;emitted<allowed&&slot<allowed+128;slot++){
                if(!usedSlots.add(slot))continue;
                out.add(new CitizenProjection(candidate.faction().id(),candidate.settlement().id(),slot,roleFor(candidate.faction(),candidate.settlement(),slot,allowed),candidate.settlement().position()));
                emitted++;
            }
            remaining-=emitted;
            if(remaining<=0)break;
        }
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
            // Court chaplain only when a temple actually exists.
            if(courtIndex==3&&settlement.completedConstruction().stream().anyMatch(k->k.startsWith("temple:")))return CitizenRole.PRIEST;
        }
        List<CitizenRole> roles=new ArrayList<>(List.of(CitizenRole.FARMER,CitizenRole.HUNTER,CitizenRole.ARTISAN,CitizenRole.TRADER,CitizenRole.BUILDER,CitizenRole.OFFICIAL));
        if(settlement.completedConstruction().stream().anyMatch(k->k.startsWith("mine:")))roles.add(CitizenRole.MINER);
        if(settlement.completedConstruction().stream().anyMatch(k->k.startsWith("lumber_camp:"))){roles.add(CitizenRole.LUMBERJACK);roles.add(CitizenRole.CARPENTER);}
        if(settlement.completedConstruction().stream().anyMatch(k->k.startsWith("fishery:"))){roles.add(CitizenRole.FISHER);roles.add(CitizenRole.SAILOR);}
        if(settlement.completedConstruction().stream().anyMatch(k->k.startsWith("dock:"))){roles.add(CitizenRole.DOCKWORKER);roles.add(CitizenRole.SAILOR);}
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
