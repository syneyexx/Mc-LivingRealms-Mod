package dev.livingrealms.sim.military;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;
import java.util.*;

/** Plans bounded siege-camp machinery near players from active SiegeState equipment. */
public final class SiegeMaterializationPlanner {
    private static final int MAX_PER_SIEGE=12;
    private SiegeMaterializationPlanner(){}

    public static List<SiegeEquipmentProjection> plan(SimulationState state,Collection<SimPosition> players,double radius,int globalBudget){
        Objects.requireNonNull(state);Objects.requireNonNull(players);
        if(radius<=0||!Double.isFinite(radius)||globalBudget<0)throw new IllegalArgumentException("siege planner config");
        if(players.isEmpty()||globalBudget==0)return List.of();
        List<Candidate> candidates=new ArrayList<>();
        for(SiegeState siege:state.sieges()){
            if(!siege.active())continue;
            Settlement settlement=state.findSettlement(siege.settlementId()).orElse(null);
            if(settlement==null)continue;
            double d=nearest(settlement.position(),players);
            if(d<=radius)candidates.add(new Candidate(siege,settlement,d));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance).thenComparingLong(c->c.siege().id()));
        List<SiegeEquipmentProjection> out=new ArrayList<>();
        int left=globalBudget;
        long seed=state.seed();
        for(Candidate c:candidates){
            List<SiegeEquipmentKind> pieces=pieces(c.siege());
            int allowed=Math.min(MAX_PER_SIEGE,Math.min(pieces.size(),left));
            DeterministicRng rng=new DeterministicRng(seed^c.siege().id()*0x9E3779B97F4A7C15L);
            for(int slot=0;slot<allowed;slot++){
                SiegeEquipmentKind kind=pieces.get(slot);
                double rad=18+rng.between(4,28),ang=rng.between(0,Math.PI*2);
                // Camp ring outside the settlement core toward the attacker army if present.
                SimPosition camp=campAnchor(state,c.siege(),c.settlement());
                SimPosition pos=new SimPosition(camp.x()+Math.cos(ang)*rad,camp.z()+Math.sin(ang)*rad);
                out.add(new SiegeEquipmentProjection(c.siege().id(),c.siege().attackerFactionId(),c.settlement().id(),slot,kind,pos));
            }
            left-=allowed;
            if(left<=0)break;
        }
        return List.copyOf(out);
    }

    private static List<SiegeEquipmentKind> pieces(SiegeState siege){
        List<SiegeEquipmentKind> list=new ArrayList<>();
        for(int i=0;i<siege.rams();i++)list.add(SiegeEquipmentKind.RAM);
        for(int i=0;i<siege.ladders();i++)list.add(SiegeEquipmentKind.LADDER);
        for(int i=0;i<siege.artilleryPieces();i++)list.add(SiegeEquipmentKind.ARTILLERY);
        return list;
    }

    private static SimPosition campAnchor(SimulationState state,SiegeState siege,Settlement settlement){
        Faction attacker=state.findFaction(siege.attackerFactionId()).orElse(null);
        if(attacker!=null){
            return attacker.armies().stream()
                    .filter(a->!a.destroyed())
                    .min(Comparator.comparingDouble(a->a.position().distanceTo(settlement.position())))
                    .map(a->{
                        SimPosition army=a.position();
                        double dx=settlement.position().x()-army.x(),dz=settlement.position().z()-army.z();
                        double len=Math.hypot(dx,dz);if(len<1e-6)return settlement.position();
                        // Place camp between army and walls, closer to settlement rim.
                        double t=Math.min(.82,Math.max(.35,1.0-80.0/len));
                        return new SimPosition(army.x()+dx*t,army.z()+dz*t);
                    })
                    .orElse(settlement.position());
        }
        return settlement.position();
    }

    private static double nearest(SimPosition p,Collection<SimPosition> players){
        double best=Double.POSITIVE_INFINITY;for(SimPosition x:players)best=Math.min(best,p.distanceTo(x));return best;
    }
    private record Candidate(SiegeState siege,Settlement settlement,double distance){}
}
