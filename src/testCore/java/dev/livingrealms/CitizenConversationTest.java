package dev.livingrealms;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.world.*;

public final class CitizenConversationTest {
    public static void main(String[] args){
        SimulationState state=new SimulationState(4411L);Faction faction=new Faction(state.nextId(),"Talkers","Mayor Venn");Settlement settlement=new Settlement(state.nextId(),"Wharf",new SimPosition(0,0),90,80);faction.addSettlement(settlement);state.addFaction(faction);
        SocialCitizen a=state.ensureSocialCitizen(faction.id(),settlement.id(),1,CitizenRole.TRADER);SocialCitizen b=state.ensureSocialCitizen(faction.id(),settlement.id(),2,CitizenRole.GUARD);
        a.remember(new CitizenMemory(state.clock().day(),MemoryType.RUMOR,"bandits:north","caravan","Bandits were seen on the north road.",settlement.position(),.8,.9));
        var ex=CitizenConversationService.converse(state,a,b);check(ex.transferred(),"rumor did not transfer");check(b.latestMemory(m->m.subjectKey().equals("bandits:north")&&m.sourceKey().equals(a.name())).isPresent(),"speaker source not preserved");check(!ex.firstLine().isBlank()&&!ex.secondLine().isBlank(),"visible lines missing");
        var again=CitizenConversationService.converse(state,a,b);check(!again.transferred(),"duplicate rumor transferred");check(a.memories().size()<=SocialCitizen.MAX_MEMORIES&&b.memories().size()<=SocialCitizen.MAX_MEMORIES,"memory bound broken");
        System.out.println("PASS citizen conversation: grounded visible exchange + bounded source-preserving rumor transfer");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
