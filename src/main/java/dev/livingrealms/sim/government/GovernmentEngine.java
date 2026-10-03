package dev.livingrealms.sim.government;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.civilization.DynastyState;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.world.SimulationState;
import dev.livingrealms.sim.world.WorldEvent;
import java.util.*;

/** Annual government lifecycle: legitimacy, stability and human-aware succession. */
public final class GovernmentEngine {
    public void simulateDay(SimulationState state, DeterministicRng rng) {
        long day=state.clock().day();
        for(Faction faction:state.factions()) {
            GovernmentState g=faction.government();
            boolean playerRuler=state.playerRuler(faction.id()).isPresent();
            double prosperity=faction.settlements().stream().mapToDouble(s->s.prosperity()).average().orElse(.5);
            double unrest=faction.settlements().stream().mapToDouble(s->s.unrest()).average().orElse(0);
            g.adjustStability((prosperity-.5)*.0008-unrest*.0012-g.corruption()*.00025);
            g.adjustLegitimacy((g.ruler().legitimacy()-.5)*.00025-unrest*.0004);
            g.adjustCorruption((g.taxRate()-.18)*.00008-(g.lawEnforcement()-.5)*.00005);
            DynastyState dynasty=state.dynasties().get(faction.id());
            if(!playerRuler&&dynasty!=null&&dynasty.rulerCitizenId()>0){
                SocialCitizen person=state.findSocialCitizen(dynasty.rulerCitizenId()).orElse(null);
                if(person!=null){if(g.ruler().dead()&&person.alive())person.markDead();else if(!person.alive())g.ruler().adjustHealth(-1);else g.ruler().adjustHealth(person.health()-g.ruler().health());}
                if(dynasty.regency()){
                    SocialCitizen regent=state.findSocialCitizen(dynasty.regentCitizenId()).filter(SocialCitizen::alive).orElse(null);
                    if(regent==null){dynasty.endRegency();dynasty.startCrisis(day);g.adjustStability(-.02);}
                    else if(person!=null&&person.ageYears(day)>=18){dynasty.endRegency();dynasty.endCrisis();state.history().add(new WorldEvent(day,"regency_ended","faction="+faction.id()+", ruler="+person.id()+", regent="+regent.id()));}
                    else{g.adjustStability((regent.personality().loyalty()-regent.personality().treachery())*.00035);g.adjustCorruption(regent.personality().greed()*.00008-regent.personality().loyalty()*.00004);}
                }
            }
            if(!playerRuler&&day>0 && day%365==0) g.advanceYear();
            if(!playerRuler){double ageRisk=Math.max(0,g.ruler().ageYears()-65)*.000012;double healthRisk=(1-g.ruler().health())*.0007;if(g.ruler().dead()||rng.chance(ageRisk+healthRisk)) succeed(state,faction,rng);}
        }
    }

    private static void succeed(SimulationState state,Faction faction,DeterministicRng rng){
        GovernmentState g=faction.government();String old=g.ruler().name();DynastyState dynasty=state.dynasties().get(faction.id());
        SocialCitizen chosen=null;boolean hereditary=g.successionLaw()==SuccessionLaw.HEREDITARY;
        if(hereditary&&dynasty!=null&&dynasty.heirCitizenId()>0){
            chosen=state.findSocialCitizen(dynasty.heirCitizenId()).filter(SocialCitizen::alive).orElse(null);
            if(chosen==null)chosen=materializeDependentHeir(state,faction,dynasty.heirCitizenId());
        }
        if(chosen==null)chosen=politicalSuccessor(state,faction,g.successionLaw(),0);
        long id;String name;int age;double health,diplomacy,stewardship,martial,legitimacy;
        if(chosen!=null){id=state.nextId();name=chosen.name();age=Math.max(0,chosen.ageYears(state.clock().day()));health=chosen.health();diplomacy=.35+chosen.personality().tradeAffinity()*.35+chosen.personality().caution()*.12;stewardship=.3+chosen.personality().loyalty()*.28+chosen.personality().tradeAffinity()*.2;martial=.25+chosen.personality().aggression()*.45+chosen.personality().loyalty()*.12;legitimacy=successionLegitimacy(g.successionLaw(),dynasty,rng);chosen.setRole(g.type()==GovernmentType.THEOCRACY?CitizenRole.PRIEST:CitizenRole.OFFICIAL);}
        else{id=state.nextId();name=successorName(faction.name(),id);age=24+rng.nextInt(28);health=.82+rng.nextDouble()*.18;double competence=.35+rng.nextDouble()*.55;diplomacy=competence;stewardship=.3+rng.nextDouble()*.65;martial=.3+rng.nextDouble()*.65;legitimacy=successionLegitimacy(g.successionLaw(),dynasty,rng);}
        RulerProfile next=new RulerProfile(id,name,age,health,diplomacy,stewardship,martial,legitimacy);g.setRuler(next);g.adjustLegitimacy((legitimacy-g.legitimacy())*.75);g.adjustStability(-.06+(legitimacy-.5)*.08);faction.setRulerName(name);
        if(dynasty!=null){
            long chosenId=chosen==null?0:chosen.id();dynasty.recordSuccession(state.clock().day(),chosenId);dynasty.endRegency();
            if(hereditary&&chosen==null){dynasty.startCrisis(state.clock().day());g.adjustStability(-.10);g.adjustLegitimacy(-.08);state.history().add(new WorldEvent(state.clock().day(),"succession_crisis","faction="+faction.id()+", house="+dynasty.houseName()+", oldRuler="+old));}
            else if(hereditary&&chosen!=null&&chosen.ageYears(state.clock().day())<18){SocialCitizen regent=politicalSuccessor(state,faction,SuccessionLaw.APPOINTED,chosen.id());if(regent!=null){dynasty.beginRegency(regent.id());dynasty.endCrisis();g.adjustStability(-.025);state.history().add(new WorldEvent(state.clock().day(),"regency_started","faction="+faction.id()+", ruler="+chosen.id()+", regent="+regent.id()+", house="+dynasty.houseName()));}else{dynasty.startCrisis(state.clock().day());g.adjustStability(-.08);}}
            else dynasty.endCrisis();
        }
        state.history().add(new WorldEvent(state.clock().day(),"succession","faction="+faction.id()+", old="+old+", new="+name+(dynasty==null?"":", house="+dynasty.houseName()+", generation="+dynasty.generation())));
    }

    private static SocialCitizen materializeDependentHeir(SimulationState state,Faction faction,long heirId){
        for(HouseholdState household:state.households()){
            DependentChild child=household.findChild(heirId).orElse(null);if(child==null)continue;if(household.factionId()!=faction.id())return null;
            CitizenRole role=faction.government().type()==GovernmentType.THEOCRACY?CitizenRole.PRIEST:CitizenRole.OFFICIAL;
            SocialCitizen heir=FamilyMaterializer.materialize(state,household,child,role);
            state.history().add(new WorldEvent(state.clock().day(),"minor_heir_materialized","faction="+faction.id()+", citizen="+heir.id()+", age="+heir.ageYears(state.clock().day())));
            return heir;
        }
        return null;
    }

    private static SocialCitizen politicalSuccessor(SimulationState state,Faction faction,SuccessionLaw law,long excludedCitizenId){
        Comparator<SocialCitizen> order=switch(law){case MILITARY->Comparator.comparingDouble((SocialCitizen c)->c.personality().aggression()+c.personality().loyalty()).reversed();case ELECTIVE,COUNCIL->Comparator.comparingDouble((SocialCitizen c)->c.personality().tradeAffinity()+c.personality().loyalty()-c.personality().treachery()).reversed();case APPOINTED,HEREDITARY->Comparator.comparingDouble((SocialCitizen c)->c.personality().loyalty()+c.personality().caution()).reversed();};
        return state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c->c.factionId()==faction.id()).filter(c->c.id()!=excludedCitizenId).filter(c->c.ageYears(state.clock().day())>=21).filter(c->c.role()==CitizenRole.OFFICIAL||c.role()==CitizenRole.GUARD||c.role()==CitizenRole.PRIEST||c.role()==CitizenRole.SCHOLAR).sorted(order.thenComparingLong(SocialCitizen::id)).findFirst().orElse(null);
    }
    private static double successionLegitimacy(SuccessionLaw law,DynastyState dynasty,DeterministicRng rng){double base=switch(law){case HEREDITARY->.55;case ELECTIVE,COUNCIL->.60;case MILITARY->.42;case APPOINTED->.50;};if(law==SuccessionLaw.HEREDITARY&&dynasty!=null)base+=.18*(dynasty.prestige()-.5);return Math.min(.94,Math.max(.2,base+rng.nextDouble()*.25));}
    private static String successorName(String faction,long id){List<String> first=List.of("Alden","Mira","Cassian","Elara","Dorian","Lyra","Soren","Nadia","Tarin","Vera","Kael","Iris");String clean=faction.replaceAll("[^A-Za-z]","");if(clean.isBlank())clean="Realm";String house=clean.substring(0,Math.min(6,clean.length()));return first.get(Math.floorMod(id,first.size()))+" "+house;}
}
