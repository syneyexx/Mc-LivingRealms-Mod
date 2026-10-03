package dev.livingrealms;

import dev.livingrealms.sim.economy.ResourceDominanceEngine;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.world.*;

public final class ResourceDominanceTest {
    private ResourceDominanceTest(){}
    public static void main(String[] args){
        SimulationState state=new SimulationState(99117L);
        Faction a=faction(state,"Iron Crown",0);Faction b=faction(state,"Low March",700);
        a.stockpile().add(ResourceType.IRON,120_000);b.stockpile().add(ResourceType.IRON,12);
        var first=ResourceDominanceEngine.analyze(state,ResourceType.IRON);
        check(first.leaderFactionId()==a.id(),"dominant iron realm not detected");
        check(first.monopoly(),"extreme real supply dominance must qualify as a monopoly");
        check(ResourceDominanceEngine.sellerLeverageMultiplier(state,a.id(),ResourceType.IRON)>1,"dominance must create bounded seller leverage");
        b.stockpile().add(ResourceType.IRON,300_000);
        var second=ResourceDominanceEngine.analyze(state,ResourceType.IRON);
        check(second.leaderFactionId()==b.id(),"monopoly must move when real capacity changes");
        check(ResourceDominanceEngine.share(state,a.id(),ResourceType.IRON)<first.leaderShare(),"dominance must be derived, not a sticky flag");
        System.out.println("PASS resource dominance: real capacity-derived monopoly + bounded trade leverage + automatic loss of dominance");
    }
    private static Faction faction(SimulationState state,String name,double x){Faction f=new Faction(state.nextId(),name,"Ruler");f.addSettlement(new Settlement(state.nextId(),name+" City",new SimPosition(x,0),300,340));state.addFaction(f);return f;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
