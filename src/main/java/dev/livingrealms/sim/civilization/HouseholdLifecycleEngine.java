package dev.livingrealms.sim.civilization;

import dev.livingrealms.sim.civilian.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.util.*;
import dev.livingrealms.sim.world.*;
import java.util.*;

/**
 * Household graph: ensure/merge/repair, births, coming-of-age, orphans, wages.
 */
public final class HouseholdLifecycleEngine {
    private HouseholdLifecycleEngine() {}

    public static void ensureHouseholds(SimulationState state){
        Map<Long,HouseholdState> byId=new HashMap<>();for(HouseholdState h:state.households())if(h.active())byId.put(h.id(),h);
        for(SocialCitizen citizen:state.socialCitizens())if(citizen.alive()){
            if(citizen.householdId()>0&&byId.containsKey(citizen.householdId()))continue;
            HouseholdState partnerHouse=null;
            for(var e:citizen.relationships().entrySet())if(e.getValue().familyBond()==FamilyBond.PARTNER){long otherId=CivilizationSupport.parseCitizenKey(e.getKey());SocialCitizen other=state.findSocialCitizen(otherId).filter(SocialCitizen::alive).orElse(null);if(other!=null&&other.householdId()>0){partnerHouse=byId.get(other.householdId());if(partnerHouse!=null)break;}}
            // Never attach a citizen to a household that lives in another settlement/faction.
            if(partnerHouse!=null&&(partnerHouse.factionId()!=citizen.factionId()||partnerHouse.settlementId()!=citizen.settlementId()))partnerHouse=null;
            if(partnerHouse==null){
                partnerHouse=new HouseholdState(state.nextId(),citizen.factionId(),citizen.settlementId(),state.clock().day());
                Faction owner=state.findFaction(citizen.factionId()).orElse(null);
                Settlement homeSettlement=state.findSettlement(citizen.settlementId()).orElse(null);
                String homeKey=owner!=null&&homeSettlement!=null
                        ?dev.livingrealms.sim.social.HouseholdHomeBinder.assignHomeKey(owner,homeSettlement,partnerHouse.id())
                        :"house:"+Math.floorMod(citizen.projectionSlot(),72);
                partnerHouse.setHomeKey(homeKey);
                partnerHouse.adjustWealth(citizen.money()*.25);
                state.addHousehold(partnerHouse);
                byId.put(partnerHouse.id(),partnerHouse);
            }
            partnerHouse.addMember(citizen.id());citizen.setHouseholdId(partnerHouse.id());
        }
        // Drop dead members so transfers that leave corpses behind do not desync household location checks.
        for(HouseholdState household:state.households())if(household.active())for(long memberId:List.copyOf(household.memberIds())){
            SocialCitizen member=state.findSocialCitizen(memberId).orElse(null);
            if(member==null||!member.alive()){household.removeMember(memberId);if(member!=null&&member.householdId()==household.id())member.setHouseholdId(0);}
        }
        // Merge households after partnership formation; oldest household survives to preserve a stable identity.
        for(SocialCitizen citizen:state.socialCitizens())if(citizen.alive()&&citizen.householdId()>0)for(var e:citizen.relationships().entrySet())if(e.getValue().familyBond()==FamilyBond.PARTNER){
            SocialCitizen partner=state.findSocialCitizen(CivilizationSupport.parseCitizenKey(e.getKey())).filter(SocialCitizen::alive).orElse(null);
            if(partner==null||partner.householdId()<=0||partner.householdId()==citizen.householdId())continue;
            HouseholdState a=byId.get(citizen.householdId()),b=byId.get(partner.householdId());
            if(a==null||b==null||a.factionId()!=b.factionId()||a.settlementId()!=b.settlementId())continue;
            HouseholdState keep=a.id()<b.id()?a:b,drop=keep==a?b:a;
            for(long member:new ArrayList<>(drop.memberIds()))if(keep.addMember(member)){state.findSocialCitizen(member).ifPresent(c->c.setHouseholdId(keep.id()));drop.removeMember(member);}
            // Move children once — leaving them on the deactivated household would duplicate canonical ids.
            double transferredWealth=drop.sharedWealth();
            List<DependentChild> moving=new ArrayList<>(drop.children());
            for(DependentChild child:moving)keep.addChild(child);
            drop.restore(drop.memberIds(),List.of(),0,drop.homeKey(),false);
            keep.adjustWealth(transferredWealth);
        }
        for(HouseholdState household:state.households())if(household.active()&&household.memberIds().stream().noneMatch(id->state.findSocialCitizen(id).map(SocialCitizen::alive).orElse(false))&&household.children().isEmpty())household.deactivate();
        // Repair residual location drift (e.g. transfer/migration race): sync household to unanimous member location or clear links.
        for(HouseholdState household:state.households()){
            if(!household.active())continue;
            Long unanimousFaction=null,unanimousSettlement=null;boolean mixed=false;
            for(long memberId:household.memberIds()){
                SocialCitizen member=state.findSocialCitizen(memberId).filter(SocialCitizen::alive).orElse(null);
                if(member==null)continue;
                if(unanimousFaction==null){unanimousFaction=member.factionId();unanimousSettlement=member.settlementId();}
                else if(unanimousFaction!=member.factionId()||unanimousSettlement!=member.settlementId()){mixed=true;break;}
            }
            if(mixed){
                for(long memberId:List.copyOf(household.memberIds())){
                    SocialCitizen member=state.findSocialCitizen(memberId).orElse(null);
                    if(member!=null&&(member.factionId()!=household.factionId()||member.settlementId()!=household.settlementId())){
                        household.removeMember(memberId);member.setHouseholdId(0);
                    }
                }
            }else if(unanimousFaction!=null&&(household.factionId()!=unanimousFaction||household.settlementId()!=unanimousSettlement)){
                household.migrate(unanimousFaction,unanimousSettlement);
            }
        }
    }

    public static void simulateHouseholdLifecycle(SimulationState state,DeterministicRng rng){
        long day=state.clock().day();
        for(HouseholdState household:state.households())if(household.active()){
            Settlement settlement=state.findSettlement(household.settlementId()).orElse(null);Faction faction=state.findFaction(household.factionId()).orElse(null);if(settlement==null||faction==null){household.deactivate();continue;}
            List<SocialCitizen> adults=household.memberIds().stream().map(state::findSocialCitizen).flatMap(Optional::stream).filter(SocialCitizen::alive).filter(c->c.ageYears(day)>=18).toList();
            boolean partnered=adults.stream().anyMatch(a->CivilizationSupport.hasHouseholdPartner(a,adults));
            SettlementCivilizationState civ=state.ensureSettlementCivilization(settlement.id(),faction.id());
            double freeHousing=Mathx.clamp((settlement.housing()-settlement.population())/(double)Math.max(20,settlement.population()),0,1);
            double fertility=Mathx.clamp(.012+.028*settlement.foodSecurity()+.012*freeHousing+.008*civ.culturalCohesion()-.025*civ.diseasePressure(),0,.08);
            if(partnered&&household.children().size()<HouseholdState.MAX_CHILDREN&&settlement.housing()>settlement.population()&&rng.chance(fertility)){
                long[] parents=parentPair(adults);DependentChild child=new DependentChild(state.nextId(),day,parents[0],parents[1]);household.addChild(child);
                state.history().add(new WorldEvent(day,"household_birth","child="+child.id()+", household="+household.id()+", settlement="+settlement.id()+", parents="+parents[0]+"/"+parents[1]));
            }
            for(DependentChild child:household.takeAdultChildren(day))if(state.socialCitizens().size()<CivilizationSupport.MAX_NAMED_CITIZENS&&household.memberIds().size()<HouseholdState.MAX_NAMED_MEMBERS)materializeAdultChild(state,household,faction,settlement,child,rng);
            if(adults.isEmpty()&&!household.children().isEmpty())adoptOrphans(state,household,settlement);
            // Living household budget: wages already top up sharedWealth; spend on food/comfort, then needs follow.
            double mouths=Math.max(1,household.representedPeople());
            double scarcity=1.0-settlement.foodSecurity();
            double foodBill=mouths*(.08+.22*scarcity);
            double spent=Math.min(household.sharedWealth(),foodBill);
            if(spent>0){
                household.adjustWealth(-spent);
                settlement.stockpile().take(ResourceType.FOOD,spent*.55); // market purchase from local granary
            }
            double fed=spent/Math.max(.01,foodBill);
            for(SocialCitizen adult:adults){
                adult.needs().approach(.35+.55*fed,settlement.publicOrder(),.55+.2*settlement.prosperity(),
                        Mathx.clamp(.3+household.sharedWealth()/120.0,0,1),.4+.35*fed+(household.homeKey().isBlank()?0:.1),.18);
                if(fed<.45)adult.addMoney(-Math.min(adult.money(),.4)); // personal purse covers shortfall
            }
            household.adjustWealth((settlement.prosperity()-.45)*.35-mouths*scarcity*.05);
        }
    }

    public static void materializeAdultChild(SimulationState state,HouseholdState household,Faction faction,Settlement settlement,DependentChild dependent,DeterministicRng rng){
        long id=dependent.id();CitizenRole role=CivilizationSupport.chooseProfession(settlement,id);SocialCitizen child=FamilyMaterializer.materialize(state,household,dependent,role);
        state.history().add(new WorldEvent(state.clock().day(),"coming_of_age","citizen="+child.id()+", household="+household.id()+", settlement="+settlement.id()+", profession="+role));
    }

    public static void adoptOrphans(SimulationState state,HouseholdState orphaned,Settlement settlement){
        HouseholdState target=state.households().stream().filter(HouseholdState::active).filter(h->h.id()!=orphaned.id()&&h.settlementId()==settlement.id()&&h.memberIds().stream().anyMatch(id->state.findSocialCitizen(id).filter(SocialCitizen::alive).map(c->c.ageYears(state.clock().day())>=21).orElse(false))).filter(h->h.children().size()<6).max(Comparator.comparingDouble(HouseholdState::resilience)).orElse(null);
        if(target==null){
            if(CivilizationSupport.has(settlement,"orphanage:")){if(!orphaned.homeKey().startsWith("orphanage:")){orphaned.setHomeKey("orphanage:0");state.history().add(new WorldEvent(state.clock().day(),"orphans_sheltered","household="+orphaned.id()+", settlement="+settlement.id()+", children="+orphaned.children().size()));}return;}
            orphaned.setHomeKey("community_care:"+settlement.id());return;
        }
        List<DependentChild> children=new ArrayList<>(orphaned.children());List<Long> guardians=target.memberIds().stream().filter(id->state.findSocialCitizen(id).filter(SocialCitizen::alive).map(c->c.ageYears(state.clock().day())>=21).orElse(false)).limit(2).toList();for(DependentChild child:children){child.adoptBy(guardians);target.addChild(child);}double transfer=orphaned.sharedWealth()*.5;target.adjustWealth(transfer);orphaned.restore(orphaned.memberIds(),List.of(),Math.max(0,orphaned.sharedWealth()-transfer),orphaned.homeKey(),false);state.history().add(new WorldEvent(state.clock().day(),"orphans_adopted","fromHousehold="+orphaned.id()+", toHousehold="+target.id()+", settlement="+settlement.id()+", children="+children.size()));
    }

    public static long[] parentPair(List<SocialCitizen> adults){
        for(SocialCitizen a:adults)for(var e:a.relationships().entrySet())if(e.getValue().familyBond()==FamilyBond.PARTNER){long bid=CivilizationSupport.parseCitizenKey(e.getKey());if(bid>0&&adults.stream().anyMatch(c->c.id()==bid))return new long[]{a.id(),bid};}
        return adults.size()>=2?new long[]{adults.get(0).id(),adults.get(1).id()}:adults.isEmpty()?new long[]{0,0}:new long[]{adults.get(0).id(),0};
    }

    public static void payWages(SimulationState state){
        for(Faction faction:state.factions()){
            for(Settlement settlement:faction.settlements()){
                double employment=settlement.employment();
                if(employment<.12)continue;
                double purse=settlement.prosperity()*.8+faction.treasury()*.0004;
                List<SocialCitizen> workers=state.socialCitizens().stream().filter(SocialCitizen::alive)
                        .filter(c->c.settlementId()==settlement.id())
                        .sorted(Comparator.comparingLong(SocialCitizen::id)).toList();
                if(workers.isEmpty())continue;
                double wagePool=Math.min(purse,settlement.population()*.002*employment);
                if(wagePool<=0)continue;
                double paid=0;
                for(SocialCitizen c:workers){
                    double roleMul=switch(c.role()){
                        case OFFICIAL,GUARD -> 1.35;
                        case TRADER,ARTISAN,SCHOLAR,TEACHER,HEALER -> 1.20;
                        case FARMER,MINER,LUMBERJACK,FISHER,BUTCHER,CARPENTER,BUILDER,DOCKWORKER,SAILOR -> 1.0;
                        case PRIEST -> .85;
                        default -> .75;
                    };
                    double guildMul=GuildRank.guildedRole(c.role())?GuildRank.of(c).wageMultiplier():1.0;
                    double wage=Math.max(.05,wagePool/workers.size()*roleMul*guildMul*(.55+.9*c.professionSkill())*employment);
                    if(faction.treasury()<wage*.15&&settlement.stockpile().get(ResourceType.GOLD)<wage)continue;
                    double fromLocal=settlement.stockpile().take(ResourceType.GOLD,wage*.35);
                    double need=wage-fromLocal;
                    if(need>0)faction.addTreasury(-Math.min(faction.treasury(),need));
                    c.addMoney(wage);c.practiceProfession(.01);paid+=wage;
                    // Household purse receives a share so family consumption is not only prosperity drift.
                    if(c.householdId()>0)state.findHousehold(c.householdId()).ifPresent(h->h.adjustWealth(wage*.45));
                }
                if(paid>0)state.history().add(new WorldEvent(state.clock().day(),"wages_paid","settlement="+settlement.id()+", workers="+workers.size()+", paid="+Math.round(paid)));
            }
        }
    }
}
