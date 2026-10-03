package dev.livingrealms;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.transport.TransportMode;
import dev.livingrealms.sim.transport.TransportRoute;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

/** Regression gate for bounded rumor diffusion over the canonical transport graph. */
public final class RumorNetworkTest {
    private RumorNetworkTest() {}

    public static void main(String[] args){
        SimulationState state=new SimulationState(88_441L);
        Faction faction=new Faction(state.nextId(),"Roadrealm","Mara");
        Settlement west=new Settlement(state.nextId(),"Westmere",new SimPosition(0,0),120,180);
        Settlement east=new Settlement(state.nextId(),"Eastwatch",new SimPosition(900,0),90,140);
        faction.addSettlement(west);faction.addSettlement(east);state.addFaction(faction);
        state.addRoute(new TransportRoute(state.nextId(),faction.id(),west.id(),east.id(),TransportMode.ROAD,900,.8,.75,240));

        SocialCitizen trader=state.ensureSocialCitizen(faction.id(),west.id(),0,CitizenRole.TRADER);
        SocialCitizen farmer=state.ensureSocialCitizen(faction.id(),east.id(),0,CitizenRole.FARMER);
        trader.remember(new CitizenMemory(0,MemoryType.RUMOR,"rich_mine","miner:Ada","A rich iron vein was found west of Westmere.",new SimPosition(-180,35),.8,.9));

        new RumorEngine().simulateDay(state);
        CitizenMemory received=farmer.latestMemory(m->m.subjectKey().equals("rich_mine")).orElseThrow(() -> new AssertionError("route rumor did not reach connected settlement"));
        check(received.type()==MemoryType.RUMOR,"route knowledge must arrive as rumor");
        check(received.confidence()>0&&received.confidence()<.9,"rumor confidence must decay during travel");
        check(received.sourceKey().equals(trader.name()),"route rumor must retain immediate human source");

        System.out.println("PASS rumor network: bounded person-to-person information diffusion over canonical roads with confidence decay");
    }

    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
