package dev.livingrealms.sim.society;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.*;
import java.util.ArrayList;
import java.util.Comparator;

/** Deterministic secession model; only chronically unstable peripheral settlements can break away. */
public final class RebellionEngine {
    public void simulateDay(SimulationState state, DeterministicRng rng, double threshold){
        var additions=new ArrayList<Faction>();
        for(Faction parent:new ArrayList<>(state.factions())){
            if(parent.settlements().size()<2) continue;
            Settlement rebel=parent.settlements().stream().filter(s->s.unrest()>=threshold).max(Comparator.comparingDouble(Settlement::unrest)).orElse(null);
            if(rebel==null) continue;
            double chance=Math.max(0,(rebel.unrest()-threshold)*.012)*(1-parent.government().stability());
            if(!rng.chance(chance)) continue;
            Settlement moved=parent.removeSettlement(rebel.id()); if(moved==null) continue;
            long id=state.nextId();
            Faction splinter=new Faction(id,moved.name()+" Free State","Council of "+moved.name());
            splinter.addSettlement(moved);
            splinter.stockpile().add(ResourceType.FOOD,parent.stockpile().take(ResourceType.FOOD,Math.max(20,moved.population()*.15)));
            splinter.stockpile().add(ResourceType.WOOD,parent.stockpile().take(ResourceType.WOOD,25));
            splinter.relationWith(parent.id()).declareWar(); parent.relationWith(splinter.id()).declareWar();
            additions.add(splinter);
            state.history().add(new WorldEvent(state.clock().day(),"rebellion",moved.name()+" seceded from "+parent.name()+" as "+splinter.name()));
        }
        additions.forEach(state::addFaction);
    }
}
