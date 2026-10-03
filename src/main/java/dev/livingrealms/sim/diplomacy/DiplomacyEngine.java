package dev.livingrealms.sim.diplomacy;

import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Synchronizes explicit wars/treaties with relation state and resolves exhausted wars. */
public final class DiplomacyEngine {
    public void simulateDay(SimulationState state,DeterministicRng rng){
        expireTreaties(state);
        discoverWars(state);
        for(WarState war:new ArrayList<>(state.wars())) if(war.active()) updateWar(state,war,rng);
    }
    private static void expireTreaties(SimulationState state){for(Treaty t:state.treaties())if(t.active()&&state.clock().day()>t.endDay())t.terminate();}
    private static void discoverWars(SimulationState state){
        for(Faction a:state.factions())for(var e:a.relations().entrySet())if(a.id()<e.getKey()&&e.getValue().status()==RelationStatus.WAR){
            long b=e.getKey(); if(state.wars().stream().anyMatch(w->w.active()&&w.between(a.id(),b)))continue;
            Faction def=state.findFaction(b).orElse(null);if(def==null)continue;
            long target=def.settlements().isEmpty()?0:def.settlements().getFirst().id();
            WarState war=new WarState(state.nextId(),a.id(),b,WarGoalType.CONQUEST,target,state.clock().day());state.addWar(war);
            state.history().add(new WorldEvent(state.clock().day(),"war_started",a.name()+" vs "+def.name()+", goal="+war.goal()));
        }
    }
    private static void updateWar(SimulationState state,WarState war,DeterministicRng rng){
        Faction a=state.findFaction(war.attackerFactionId()).orElse(null),b=state.findFaction(war.defenderFactionId()).orElse(null);if(a==null||b==null){war.end();return;}
        double ap=power(a),bp=power(b);double total=Math.max(1,ap+bp);war.adjustScore((ap-bp)/total*.20);
        war.addExhaustion(a.id(),Math.min(.01,.00035+a.population()*.00000002+armyDeficit(a)*.001));
        war.addExhaustion(b.id(),Math.min(.01,.00035+b.population()*.00000002+armyDeficit(b)*.001));
        long duration=state.clock().day()-war.startDay();
        boolean peace=duration>30&&(war.attackerExhaustion()>.82||war.defenderExhaustion()>.82||duration>720);
        if(!peace&&duration>120&&rng.chance(.0005*(war.attackerExhaustion()+war.defenderExhaustion())))peace=true;
        if(peace)makePeace(state,war,a,b);
    }
    private static void makePeace(SimulationState state,WarState war,Faction a,Faction b){
        a.relationWith(b.id()).makePeace();b.relationWith(a.id()).makePeace();war.end();
        Treaty peace=new Treaty(state.nextId(),a.id(),b.id(),TreatyType.PEACE_TREATY,state.clock().day(),state.clock().day()+180);state.addTreaty(peace);
        double reparations=war.attackerScore()>25?Math.min(b.treasury()*.12,250):war.attackerScore()<-25?-Math.min(a.treasury()*.12,250):0;
        if(reparations>0){b.addTreasury(-reparations);a.addTreasury(reparations);}else if(reparations<0){double amount=-reparations;a.addTreasury(-amount);b.addTreasury(amount);}
        state.history().add(new WorldEvent(state.clock().day(),"peace_treaty",a.name()+" / "+b.name()+", score="+Math.round(war.attackerScore())));
    }
    private static double power(Faction f){return f.armies().stream().mapToDouble(Army::combatPower).sum()+f.population()*.02;}
    private static double armyDeficit(Faction f){double target=Math.max(20,f.population()*.025);double actual=f.armies().stream().mapToDouble(Army::totalPersonnel).sum();return Math.max(0,(target-actual)/target);}
}
