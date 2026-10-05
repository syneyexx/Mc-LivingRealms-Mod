package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.civilian.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.government.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.util.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Cultural policy bootstrap, dynasties, succession, and political marriages.
 */
public final class DynastyLifecycleEngine {
    private DynastyLifecycleEngine() {}

    public static void ensureCulturalPoliciesAndDynasties(SimulationState state){
        for(Faction faction:state.factions()){
            boolean newProfile=state.findFactionCivilization(faction.id()).isEmpty();
            FactionCivilizationState civ=state.ensureFactionCivilization(faction.id());
            if(newProfile){
                long h=CivilizationSupport.mix(state.seed()^faction.id()*0x9E3779B97F4A7C15L);
                civ.setCultureTraits(CivilizationSupport.unit(h,0),CivilizationSupport.unit(h,9),CivilizationSupport.unit(h,18),CivilizationSupport.unit(h,27),CivilizationSupport.unit(h,36),CivilizationSupport.unit(h,45));
                double severity=.28+.48*CivilizationSupport.unit(h,6),due=.28+.58*CivilizationSupport.unit(h,22),refugees=.25+.65*CivilizationSupport.unit(h,39);
                if(faction.government().type()==GovernmentType.THEOCRACY){severity=Math.max(severity,.5);due=Math.max(.35,due-.08);} civ.setLawPolicy(severity,due,refugees);
            }
            if(hereditaryGovernment(faction.government())){
                DynastyState dynasty=state.dynasties().get(faction.id());
                if(dynasty==null){dynasty=state.ensureDynasty(faction.id(),state.clock().day(),CivilizationSupport.houseName(faction));state.history().add(new WorldEvent(state.clock().day(),"dynasty_founded","faction="+faction.id()+", house="+dynasty.houseName()));}
                if(state.playerRuler(faction.id()).isEmpty())ensureRulerCitizen(state,faction,dynasty);
            }
        }
    }

    public static boolean hereditaryGovernment(GovernmentState government){return government.successionLaw()==SuccessionLaw.HEREDITARY||government.type()==GovernmentType.FEUDAL_MONARCHY||government.type()==GovernmentType.ABSOLUTE_MONARCHY||government.type()==GovernmentType.CONSTITUTIONAL_MONARCHY;}

    public static void ensureRulerCitizen(SimulationState state,Faction faction,DynastyState dynasty){
        SocialCitizen ruler=dynasty.rulerCitizenId()>0?state.findSocialCitizen(dynasty.rulerCitizenId()).filter(SocialCitizen::alive).orElse(null):null;
        if(ruler==null)ruler=state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c->c.factionId()==faction.id()&&c.name().equals(faction.rulerName())).findFirst().orElse(null);
        if(ruler==null&&state.socialCitizens().size()<CivilizationSupport.MAX_NAMED_CITIZENS&&!faction.settlements().isEmpty()){
            Settlement capital=faction.settlements().stream().max(Comparator.comparingInt(Settlement::population)).orElseThrow();long id=state.nextId();int slot=SocialPopulationEngine.allocateVirtualProjectionSlot(state,capital.id(),id);CitizenRole role=faction.government().type()==GovernmentType.THEOCRACY?CitizenRole.PRIEST:CitizenRole.OFFICIAL;long birth=state.clock().day()-faction.government().ruler().ageYears()*365L-120;DeterministicRng r=new DeterministicRng(state.seed()^id);CitizenPersonality personality=new CitizenPersonality(r.between(.2,.8),r.between(.2,.8),r.between(.2,.8),r.between(.15,.8),r.between(.35,.95),r.between(.02,.5));int ageYears=Math.max(18,faction.government().ruler().ageYears());var appearance=dev.livingrealms.sim.civilian.AppearanceProfile.forCitizen(state.seed(),id,role,ageYears,faction.id());ruler=new SocialCitizen(id,faction.id(),capital.id(),slot,faction.rulerName(),appearance.textureIndex(),role,birth,personality);ruler.restoreAppearance(appearance.pack());ruler.addMoney(Math.max(40,faction.treasury()*.02));state.addSocialCitizen(ruler);state.history().add(new WorldEvent(state.clock().day(),"ruler_personified","faction="+faction.id()+", citizen="+id+", name="+ruler.name()));
        }
        if(ruler!=null)dynasty.setRulerCitizenId(ruler.id());
    }

    public static void refreshDynasties(SimulationState state){
        long day=state.clock().day();for(Faction faction:state.factions()){
            DynastyState dynasty=state.dynasties().get(faction.id());if(dynasty==null)continue;SocialCitizen ruler=state.findSocialCitizen(dynasty.rulerCitizenId()).orElse(null);if(ruler==null||!ruler.alive()){dynasty.startCrisis(day);}else{long heir=findHeir(state,ruler);dynasty.setHeirCitizenId(heir);if(heir>0)dynasty.endCrisis();else if(faction.government().successionLaw()==SuccessionLaw.HEREDITARY)dynasty.startCrisis(day);}
            if(dynasty.successionCrisis()){
                long duration=Math.max(0,day-dynasty.crisisSinceDay());
                faction.government().adjustLegitimacy(-.0025-Math.min(.004,duration/4000.0));
                faction.government().adjustStability(-.0018);
                dynasty.adjustPrestige(-.004);
                for(Settlement s:faction.settlements())s.adjustUnrest(.002+(duration>90?.003:0));
                if(duration>0&&duration%30==0)state.history().add(new WorldEvent(day,"succession_crisis_ongoing","faction="+faction.id()+", house="+dynasty.houseName()+", days="+duration));
            }
            double prestigeDelta=(faction.government().legitimacy()-.5)*.02-faction.settlements().stream().mapToDouble(Settlement::unrest).average().orElse(0)*.01;dynasty.adjustPrestige(prestigeDelta);
        }
    }

    public static long findHeir(SimulationState state,SocialCitizen ruler){
        long named=ruler.relationships().entrySet().stream().filter(e->e.getValue().familyBond()==FamilyBond.CHILD||e.getValue().familyBond()==FamilyBond.ADOPTED_CHILD).mapToLong(e->CivilizationSupport.parseCitizenKey(e.getKey())).filter(id->state.findSocialCitizen(id).filter(SocialCitizen::alive).isPresent()).boxed().min(Comparator.comparingLong(id->state.findSocialCitizen(id).orElseThrow().birthDay())).orElse(0L);
        if(named>0)return named;
        return state.households().stream().flatMap(h->h.children().stream()).filter(child->child.parentAId()==ruler.id()||child.parentBId()==ruler.id()||child.adoptiveParentAId()==ruler.id()||child.adoptiveParentBId()==ruler.id()).min(Comparator.comparingLong(DependentChild::birthDay).thenComparingLong(DependentChild::id)).map(DependentChild::id).orElse(0L);
    }

    public static void simulatePoliticalMarriages(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();
        for(DiplomaticMarriage marriage:state.diplomaticMarriages())if(marriage.active()){
            SocialCitizen a=state.findSocialCitizen(marriage.citizenA()).orElse(null),b=state.findSocialCitizen(marriage.citizenB()).orElse(null);
            if(a==null||b==null||!a.alive()||!b.alive()){
                marriage.end();if(a!=null)a.relationship("citizen:"+marriage.citizenB()).setFamilyBond(FamilyBond.NONE);if(b!=null)b.relationship("citizen:"+marriage.citizenA()).setFamilyBond(FamilyBond.NONE);
                Faction fa=state.findFaction(marriage.factionA()).orElse(null),fb=state.findFaction(marriage.factionB()).orElse(null);if(fa!=null&&fb!=null){fa.relationWith(fb.id()).adjust(-2);fb.relationWith(fa.id()).adjust(-2);}state.history().add(new WorldEvent(day,"political_marriage_ended","marriage="+marriage.id()+", reason=death_or_missing"));
            }
        }
        if(state.diplomaticMarriages().stream().filter(DiplomaticMarriage::active).count()>=SimulationState.MAX_DIPLOMATIC_MARRIAGES)return;
        List<Faction> factions=state.factions();for(int i=0;i<factions.size();i++)for(int j=i+1;j<factions.size();j++){
            Faction a=factions.get(i),b=factions.get(j);DiplomaticRelation rel=a.relations().get(b.id());if(rel==null||rel.status()==RelationStatus.WAR||rel.status()==RelationStatus.HOSTILE||rel.opinion()<30)continue;if(state.diplomaticMarriages().stream().anyMatch(m->m.active()&&((m.factionA()==a.id()&&m.factionB()==b.id())||(m.factionA()==b.id()&&m.factionB()==a.id()))))continue;if(!rng.chance(.04))continue;
            SocialCitizen ca=eligiblePoliticalPartner(state,a),cb=eligiblePoliticalPartner(state,b);if(ca==null||cb==null)continue;
            boolean hostA=isDynasticCourtMember(state,a,ca)||(!isDynasticCourtMember(state,b,cb)&&a.government().legitimacy()>=b.government().legitimacy());SocialCitizen host=hostA?ca:cb,guest=hostA?cb:ca;Faction hostFaction=hostA?a:b;if(!relocateCourtSpouse(state,hostFaction,host,guest))continue;
            ca.relationship("citizen:"+cb.id()).setFamilyBond(FamilyBond.PARTNER);cb.relationship("citizen:"+ca.id()).setFamilyBond(FamilyBond.PARTNER);ca.relationship("citizen:"+cb.id()).adjust(.12,0,.32,0,.18);cb.relationship("citizen:"+ca.id()).adjust(.12,0,.32,0,.18);
            DiplomaticMarriage marriage=new DiplomaticMarriage(state.nextId(),day,ca.id(),cb.id(),a.id(),b.id());state.addDiplomaticMarriage(marriage);a.relationWith(b.id()).adjust(7);b.relationWith(a.id()).adjust(7);state.history().add(new WorldEvent(day,"political_marriage","marriage="+marriage.id()+", factions="+a.id()+"/"+b.id()+", citizens="+ca.id()+"/"+cb.id()+", court="+hostFaction.id()));
        }
    }

    public static boolean relocateCourtSpouse(SimulationState state,Faction hostFaction,SocialCitizen host,SocialCitizen guest){
        HouseholdState hostHouse=state.findHousehold(host.householdId()).filter(HouseholdState::active).orElse(null);if(hostHouse==null||hostHouse.memberIds().size()>=HouseholdState.MAX_NAMED_MEMBERS)return false;
        Settlement destination=state.findSettlement(hostHouse.settlementId()).orElse(null);Settlement source=state.findSettlement(guest.settlementId()).orElse(null);if(destination==null)return false;
        state.findHousehold(guest.householdId()).ifPresent(h->h.removeMember(guest.id()));if(source!=null&&source.id()!=destination.id()&&source.population()>1){source.addPopulation(-1);destination.addPopulation(1);}
        guest.migrateTo(hostFaction.id(),destination.id());SocialPopulationEngine.rebindAfterMigration(state,guest);guest.setHouseholdId(hostHouse.id());hostHouse.addMember(guest.id());return true;
    }

    public static boolean isDynasticCourtMember(SimulationState state,Faction faction,SocialCitizen citizen){DynastyState d=state.dynasties().get(faction.id());return d!=null&&(d.rulerCitizenId()==citizen.id()||d.heirCitizenId()==citizen.id());}

    public static SocialCitizen eligiblePoliticalPartner(SimulationState state,Faction faction){DynastyState dynasty=state.dynasties().get(faction.id());List<SocialCitizen> candidates=state.socialCitizens().stream().filter(SocialCitizen::alive).filter(c->c.factionId()==faction.id()).filter(c->c.ageYears(state.clock().day())>=18&&c.ageYears(state.clock().day())<=55).filter(c->!CivilizationSupport.hasLivingPartner(state,c)).filter(c->c.role()==CitizenRole.OFFICIAL||c.role()==CitizenRole.PRIEST||dynasty!=null&&c.householdId()>0&&state.findHousehold(c.householdId()).map(h->h.memberIds().contains(dynasty.rulerCitizenId())).orElse(false)).sorted(Comparator.comparingLong(SocialCitizen::id)).toList();return candidates.isEmpty()?null:candidates.getFirst();}
}
