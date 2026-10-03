package dev.livingrealms.sim.social;

import dev.livingrealms.sim.civilian.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.util.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Maintains bounded persistent people without replacing aggregate population simulation. */
public final class SocialPopulationEngine {
    private final RumorEngine rumorEngine=new RumorEngine();
    public SocialCitizen ensureProjectionCitizen(SimulationState state,long factionId,long settlementId,int slot,CitizenRole role){
        Objects.requireNonNull(state);Objects.requireNonNull(role);
        SocialCitizen existing=state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c->c.settlementId()==settlementId&&c.projectionSlot()==slot).findFirst().orElse(null);
        if(existing!=null){if(existing.role()!=role)existing.setRole(role);return existing;}
        Faction faction=state.findFaction(factionId).orElseThrow(()->new IllegalArgumentException("unknown faction"));
        Settlement settlement=state.findSettlement(settlementId).orElseThrow(()->new IllegalArgumentException("unknown settlement"));
        long id=state.nextId();CitizenIdentity identity=CitizenIdentity.forAgent(state.seed(),id,factionId,settlementId,slot,role);
        DeterministicRng rng=new DeterministicRng(state.seed()^id*0x9E3779B97F4A7C15L);
        int ageYears=18+rng.nextInt(role==CitizenRole.GUARD?35:53); long birthDay=state.clock().day()-(long)ageYears*365L-rng.nextInt(365);
        CitizenPersonality personality=new CitizenPersonality(rng.between(.05,.95),rng.between(.05,.95),rng.between(.05,.95),rng.between(.05,.95),rng.between(.05,.95),rng.between(.02,.75));
        SocialCitizen created=new SocialCitizen(id,faction.id(),settlement.id(),slot,identity.name(),identity.skinVariant(),role,birthDay,personality);
        created.needs().restore(settlement.foodSecurity(),settlement.publicOrder(),.55+.35*settlement.prosperity(),.35+.5*settlement.prosperity(),Math.min(1,.35+.35*settlement.infrastructure()+.3*Math.min(1,(double)settlement.housing()/Math.max(1,settlement.population()))));
        state.addSocialCitizen(created);return created;
    }

    public void simulateDay(SimulationState state){
        long day=state.clock().day();
        for(SocialCitizen citizen:state.socialCitizens()){
            if(!citizen.alive())continue;Settlement settlement=state.findSettlement(citizen.settlementId()).orElse(null);Faction faction=state.findFaction(citizen.factionId()).orElse(null);if(settlement==null||faction==null)continue;
            double housing=Math.min(1,(double)settlement.housing()/Math.max(1,settlement.population()));
            double socialTarget=Mathx.clamp(.45+settlement.prosperity()*.35-settlement.unrest()*.25+(has(settlement,"tavern:")?.08:0)+(has(settlement,"temple:")?.04:0),0,1);
            double statusTarget=Mathx.clamp(.25+settlement.prosperity()*.45+(citizen.role()==CitizenRole.OFFICIAL?.2:0)+(citizen.role()==CitizenRole.SCHOLAR?.08:0),0,1);
            double comfort=Mathx.clamp(.25+settlement.infrastructure()*.35+housing*.35+(has(settlement,"well:")?.06:0)+(has(settlement,"irrigation:")?.035:0)+(has(settlement,"aqueduct:")?.08:0)+(has(settlement,"clinic:")?.04:0),0,1);
            citizen.needs().approach(settlement.foodSecurity(),settlement.publicOrder(),socialTarget,statusTarget,comfort,.10);
            double needStress=1-citizen.needs().overall();double care=(has(settlement,"clinic:")?.0008:0)+(has(settlement,"well:")?.00035:0)+(has(settlement,"aqueduct:")?.0003:0);citizen.adjustHealth((settlement.foodSecurity()-.45)*.0015-needStress*.0012+care);
            double learning=(.00018+.00016*citizen.needs().status()+.00014*citizen.personality().caution())*(has(settlement,"school:")?1.18:1.0);if(citizen.health()>.35&&needStress<.72)citizen.practiceProfession(learning);
            if(day%30==Math.floorMod(citizen.id(),30L)&&needStress>.45)citizen.remember(new CitizenMemory(day,MemoryType.LOCAL_EVENT,"settlement:"+settlement.id(),"self","Life has been difficult in "+settlement.name()+" lately.",settlement.position(),.35+.4*needStress,.9));
        }
        rumorEngine.simulateDay(state);
    }
    private static boolean has(Settlement settlement,String prefix){return settlement.completedConstruction().stream().anyMatch(k->k.startsWith(prefix));}
}
