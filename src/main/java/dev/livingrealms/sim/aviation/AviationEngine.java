package dev.livingrealms.sim.aviation;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Strategic air operations, production, interception and fuel/readiness cycles. */
public final class AviationEngine {
    public void simulateDay(SimulationState state,DeterministicRng rng){
        produceAircraft(state);
        assignMissions(state);
        for(AirWing wing:new ArrayList<>(state.airWings())){wing.moveDay();if(wing.position().distanceTo(wing.basePosition())<20){Faction f=state.findFaction(wing.factionId()).orElse(null);if(f!=null){double fuel=f.stockpile().take(ResourceType.FUEL,wing.aircraft()*.08);wing.refuel(fuel/Math.max(.1,wing.aircraft()*.08)*.22);wing.adjustReadiness(.025);}}}
        resolveInterceptions(state,rng);
        state.removeDestroyedAirWings();
    }
    private static void produceAircraft(SimulationState state){for(Faction f:state.factions()){if(f.technology()<1.0||f.settlements().stream().noneMatch(s->s.tier().ordinal()>=Settlement.Tier.CITY.ordinal()))continue;if(state.airWings().stream().anyMatch(w->w.factionId()==f.id())||f.stockpile().get(ResourceType.MACHINERY)<45||f.stockpile().get(ResourceType.FUEL)<20)continue;Settlement base=f.settlements().stream().max(Comparator.comparingInt(Settlement::population)).orElseThrow();f.stockpile().take(ResourceType.MACHINERY,45);f.stockpile().take(ResourceType.FUEL,20);state.addAirWing(new AirWing(state.nextId(),f.id(),AircraftModel.LIGHT_FIGHTER,4,base.position()));state.history().add(new WorldEvent(state.clock().day(),"air_wing_formed",f.name()+" formed fighter wing at "+base.name()));}}
    private static void assignMissions(SimulationState state){for(AirWing wing:state.airWings()){if(wing.mission()!=AirMission.IDLE||wing.fuel()<.35)continue;Faction owner=state.findFaction(wing.factionId()).orElse(null);if(owner==null)continue;Faction enemy=owner.relations().entrySet().stream().filter(e->e.getValue().status()==RelationStatus.WAR).map(e->state.findFaction(e.getKey()).orElse(null)).filter(Objects::nonNull).findFirst().orElse(null);if(enemy==null)continue;Settlement target=enemy.settlements().stream().min(Comparator.comparingDouble(s->s.position().distanceTo(wing.position()))).orElse(null);if(target!=null&&wing.position().distanceTo(target.position())<=wing.model().range()*10)wing.assign(wing.model().role()==AircraftRole.BOMBER?AirMission.BOMBING:AirMission.CAP,target.position());}}
    private static void resolveInterceptions(SimulationState state,DeterministicRng rng){List<AirWing> wings=new ArrayList<>(state.airWings());for(int i=0;i<wings.size();i++)for(int j=i+1;j<wings.size();j++){AirWing a=wings.get(i),b=wings.get(j);if(a.factionId()==b.factionId()||a.destroyed()||b.destroyed()||a.position().distanceTo(b.position())>180)continue;Faction fa=state.findFaction(a.factionId()).orElse(null);if(fa==null||fa.relations().get(b.factionId())==null||fa.relations().get(b.factionId()).status()!=RelationStatus.WAR)continue;double pa=a.combatPower()*rng.between(.8,1.2),pb=b.combatPower()*rng.between(.8,1.2),sum=Math.max(.1,pa+pb);a.losses(Math.min(.55,pb/sum*rng.between(.08,.30)));b.losses(Math.min(.55,pa/sum*rng.between(.08,.30)));a.gainExperience(.01);b.gainExperience(.01);state.history().add(new WorldEvent(state.clock().day(),"air_battle","wings="+a.id()+"/"+b.id()+", survivors="+a.aircraft()+"/"+b.aircraft()));}}
}
