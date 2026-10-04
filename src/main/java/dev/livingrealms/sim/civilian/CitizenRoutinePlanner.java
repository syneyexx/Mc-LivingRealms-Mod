package dev.livingrealms.sim.civilian;

import dev.livingrealms.sim.construction.*;
import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.economy.primary.PrimaryEconomyPlanner;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.social.HouseholdHomeBinder;
import dev.livingrealms.sim.social.HouseholdState;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.world.*;
import java.util.*;

/** Maps aggregate citizen projections to deterministic work destinations. */
public final class CitizenRoutinePlanner {
    private CitizenRoutinePlanner(){}
    public static CitizenRoutine plan(SimulationState state,Faction faction,Settlement settlement,CitizenRole role,int slot){return plan(state,faction,settlement,role,slot,6000);}
    public static CitizenRoutine plan(SimulationState state,Faction faction,Settlement settlement,CitizenRole role,int slot,int dayTimeTicks){
        Objects.requireNonNull(state);Objects.requireNonNull(faction);Objects.requireNonNull(settlement);Objects.requireNonNull(role);
        List<ConstructionIntent> all=new ArrayList<>(SettlementPlanCache.plan(faction,settlement));all.addAll(PrimaryEconomyPlanner.plan(state,faction,settlement));
        List<ConstructionIntent> completed=all.stream().filter(i->settlement.isConstructionCompleted(i.key())).toList();
        int time=Math.floorMod(dayTimeTicks,24000);if(time>=12500||time<1000)return nightRoutine(state,faction,settlement,role,slot,completed);
        CitizenRoutine epidemic=epidemicRoutine(state,settlement,role,slot,completed);if(epidemic!=null)return epidemic;
        CitizenRoutine civic=civicRoutine(state,settlement,role,slot,completed);if(civic!=null)return civic;
        CitizenRoutine commute=commuteViaStreet(state,settlement,role,slot,completed);
        if(commute!=null)return commute;
        return switch(role){
            case FARMER -> at(select(completed,slot,StructureRole.FARM),CitizenActivity.WORK_FARM,settlement,.9);
            case MINER -> at(select(completed,slot,StructureRole.MINE),CitizenActivity.WORK_MINE,settlement,.9);
            case LUMBERJACK -> at(select(completed,slot,StructureRole.LUMBER_CAMP),CitizenActivity.WORK_FORESTRY,settlement,.9);
            case HUNTER -> new CitizenRoutine(CitizenActivity.HUNT, huntPoint(state,settlement,slot), 1.02, 7.0);
            case FISHER -> at(select(completed,slot,StructureRole.FISHERY),CitizenActivity.WORK_FISHERY,settlement,.9);
            case ARTISAN -> at(selectAny(completed,slot,List.of(StructureRole.WORKSHOP,StructureRole.FACTORY)),CitizenActivity.CRAFT,settlement,.86);
            case TRADER -> at(selectAny(completed,slot,List.of(StructureRole.MARKET,StructureRole.WAREHOUSE)),CitizenActivity.TRADE,settlement,.9);
            case OFFICIAL -> at(select(completed,slot,StructureRole.KEEP),CitizenActivity.ADMINISTER,settlement,.82);
            case GUARD -> at(selectAny(completed,slot,List.of(StructureRole.ROAD,StructureRole.KEEP,StructureRole.WALL,StructureRole.GATE,StructureRole.PRISON)),CitizenActivity.PATROL,settlement,1.0);
            case HEALER -> at(select(completed,slot,StructureRole.CLINIC),CitizenActivity.HEAL,settlement,.88);
            case PRIEST -> at(select(completed,slot,StructureRole.TEMPLE),CitizenActivity.WORSHIP,settlement,.84);
            case SCHOLAR -> at(selectAny(completed,slot,List.of(StructureRole.SCHOOL,StructureRole.OBSERVATORY)),CitizenActivity.STUDY,settlement,.84);
            case BUTCHER -> at(selectAny(completed,slot,List.of(StructureRole.MARKET,StructureRole.WAREHOUSE,StructureRole.WORKSHOP)),CitizenActivity.BUTCHER,settlement,.86);
            case CARPENTER -> at(selectAny(completed,slot,List.of(StructureRole.WORKSHOP,StructureRole.WAREHOUSE)),CitizenActivity.CARPENTRY,settlement,.86);
            case TEACHER -> at(select(completed,slot,StructureRole.SCHOOL),CitizenActivity.TEACH,settlement,.84);
            case SAILOR -> at(selectAny(completed,slot,List.of(StructureRole.DOCK,StructureRole.FISHERY,StructureRole.WAREHOUSE)),CitizenActivity.SAIL,settlement,.90);
            case DOCKWORKER -> at(selectAny(completed,slot,List.of(StructureRole.DOCK,StructureRole.WAREHOUSE,StructureRole.MARKET)),CitizenActivity.DOCK_WORK,settlement,.90);
            case SPY -> at(selectAny(completed,slot,List.of(StructureRole.TAVERN,StructureRole.MARKET,StructureRole.KEEP)),CitizenActivity.INFILTRATE,settlement,.92);
            case BUILDER -> builder(state,faction,settlement,slot);
        };
    }

    /** Under plague stress, non-essential roles stay home or seek the clinic instead of commuting freely. */
    private static CitizenRoutine epidemicRoutine(SimulationState state,Settlement settlement,CitizenRole role,int slot,List<ConstructionIntent> completed){
        if(role==CitizenRole.HEALER||role==CitizenRole.GUARD)return null;
        boolean outbreak=state.epidemics().stream().anyMatch(e->e.active()&&e.settlementId()==settlement.id());
        SettlementCivilizationState civ=state.findSettlementCivilization(settlement.id()).orElse(null);
        double pressure=civ==null?0:civ.diseasePressure();
        if(!outbreak&&pressure<.55)return null;
        long mix=state.seed()^settlement.id()*0x9E3779B97F4A7C15L^slot*31L^state.clock().day();
        if(((mix>>>11)&3L)==0L){
            ConstructionIntent clinic=select(completed,slot,StructureRole.CLINIC);
            if(clinic!=null)return at(clinic,CitizenActivity.HEAL,settlement,.72);
        }
        return at(select(completed,slot,StructureRole.HOUSE),CitizenActivity.REST,settlement,.70);
    }

    private static CitizenRoutine civicRoutine(SimulationState state,Settlement settlement,CitizenRole role,int slot,List<ConstructionIntent> completed){
        if(role==CitizenRole.GUARD||role==CitizenRole.HEALER||role==CitizenRole.SPY)return null;
        long day=state.clock().day();
        CivicEvent event=state.civicEvents().stream().filter(CivicEvent::active).filter(e->e.settlementId()==settlement.id()&&e.startDay()<=day&&e.endDay()>=day).max(Comparator.comparingDouble(CivicEvent::intensity).thenComparingLong(CivicEvent::id)).orElse(null);
        if(event==null)return null;
        double participation=Math.max(.18,Math.min(.92,event.attendance()*.65+event.intensity()*.35));
        long mix=state.seed()^settlement.id()*0x9E3779B97F4A7C15L^event.id()*0xD1B54A32D192ED03L^slot*0x94D049BB133111EBL;
        double draw=((mix>>>11)&0xFFFFL)/65535.0;if(draw>participation)return null;
        return switch(event.type()){
            case RELIGIOUS_RITUAL -> at(select(completed,slot,StructureRole.TEMPLE),CitizenActivity.WORSHIP,settlement,.82);
            case MARKET_FAIR -> at(select(completed,slot,StructureRole.MARKET),CitizenActivity.TRADE,settlement,.86);
            case CORONATION -> at(select(completed,slot,StructureRole.KEEP),CitizenActivity.SOCIALIZE,settlement,.82);
            case MOURNING -> at(selectAny(completed,slot,List.of(StructureRole.TEMPLE,StructureRole.KEEP)),CitizenActivity.SOCIALIZE,settlement,.78);
            case HARVEST_FESTIVAL,VICTORY_FEAST,WEDDING_FEAST -> at(selectAny(completed,slot,List.of(StructureRole.TAVERN,StructureRole.MARKET,StructureRole.KEEP)),CitizenActivity.SOCIALIZE,settlement,.84);
        };
    }

    private static CitizenRoutine nightRoutine(SimulationState state,Faction faction,Settlement settlement,CitizenRole role,int slot,List<ConstructionIntent> completed){
        if(role==CitizenRole.GUARD)return at(selectAny(completed,slot,List.of(StructureRole.GATE,StructureRole.WALL,StructureRole.ROAD,StructureRole.PRISON,StructureRole.KEEP)),CitizenActivity.PATROL,settlement,.92);
        long phase=state.clock().day()*31L+slot*17L+settlement.id();
        double law=faction.government().lawEnforcement();
        double order=settlement.publicOrder();
        boolean curfew=law>=.62&&order>=.55;
        // Strict curfew: non-guards/spies/priests stay home when law and order are strong.
        if(curfew&&role!=CitizenRole.SPY&&role!=CitizenRole.PRIEST&&role!=CitizenRole.OFFICIAL){
            ConstructionIntent home=resolveHouseholdHome(state,faction,settlement,slot,completed);
            return at(home,CitizenActivity.REST,settlement,.78);
        }
        // Low-order nights: spies and a few non-guards move for secret meetings / tavern nightlife.
        if(!curfew&&role==CitizenRole.SPY){
            ConstructionIntent meet=selectAny(completed,slot,List.of(StructureRole.TAVERN,StructureRole.KEEP,StructureRole.WAREHOUSE));
            if(meet!=null)return at(meet,CitizenActivity.INFILTRATE,settlement,.90);
        }
        if(!curfew&&(role==CitizenRole.TRADER||role==CitizenRole.ARTISAN||role==CitizenRole.BUILDER||role==CitizenRole.CARPENTER||role==CitizenRole.DOCKWORKER||role==CitizenRole.SAILOR)&&Math.floorMod(phase,4L)==0L){
            ConstructionIntent tavern=select(completed,slot,StructureRole.TAVERN);if(tavern!=null)return at(tavern,CitizenActivity.SOCIALIZE,settlement,.82);
        }
        if(role==CitizenRole.PRIEST&&Math.floorMod(phase,5L)==0L){ConstructionIntent temple=select(completed,slot,StructureRole.TEMPLE);if(temple!=null)return at(temple,CitizenActivity.WORSHIP,settlement,.8);}
        if((role==CitizenRole.OFFICIAL||role==CitizenRole.SPY)&&Math.floorMod(phase,7L)==0L){ConstructionIntent keep=select(completed,slot,StructureRole.KEEP);if(keep!=null)return at(keep,role==CitizenRole.SPY?CitizenActivity.INFILTRATE:CitizenActivity.ADMINISTER,settlement,.8);}
        ConstructionIntent home=resolveHouseholdHome(state,faction,settlement,slot,completed);
        return at(home,CitizenActivity.REST,settlement,.78);
    }

    private static ConstructionIntent resolveHouseholdHome(SimulationState state,Faction faction,Settlement settlement,int slot,List<ConstructionIntent> completed){
        SocialCitizen citizen=state.socialCitizens().stream()
                .filter(c->c.alive()&&c.factionId()==faction.id()&&c.settlementId()==settlement.id()&&c.projectionSlot()==slot)
                .findFirst().orElse(null);
        if(citizen!=null&&citizen.householdId()>0){
            HouseholdState house=state.findHousehold(citizen.householdId()).orElse(null);
            if(house!=null&&house.active()){
                HouseholdHomeBinder.rebindIfInvalid(house,faction,settlement);
                ConstructionIntent bound=HouseholdHomeBinder.resolveHomeIntent(faction,settlement,house.homeKey(),slot);
                if(bound!=null)return bound;
            }
        }
        return select(completed,slot,StructureRole.HOUSE);
    }

    private static CitizenRoutine commuteViaStreet(SimulationState state,Settlement settlement,CitizenRole role,int slot,List<ConstructionIntent> completed){
        if(role==CitizenRole.BUILDER||role==CitizenRole.GUARD)return null;
        List<ConstructionIntent> roads=completed.stream().filter(i->i.role()==StructureRole.ROAD).toList();
        if(roads.isEmpty())return null;
        // Citizens periodically move through the authored street network before returning to work.
        // The stable phase prevents crowds from switching destinations on the same tick/day.
        long phase=state.clock().day()+slot*3L+settlement.id();
        if(Math.floorMod(phase,4L)!=0L)return null;
        ConstructionIntent road=roads.get(Math.floorMod(slot+(int)state.clock().day(),roads.size()));
        CitizenActivity activity=role==CitizenRole.TRADER?CitizenActivity.TRADE:CitizenActivity.PATROL;
        return at(road,activity,settlement,.92);
    }
    private static SimPosition huntPoint(SimulationState state,Settlement settlement,int slot){
        long h=state.seed()^settlement.id()*0x9E3779B97F4A7C15L^slot*0xD1B54A32D192ED03L^state.clock().day()*0x94D049BB133111EBL;
        double angle=((h>>>11)&0xFFFFL)/65535.0*Math.PI*2.0;
        double radius=70.0+Math.floorMod(h,70L);
        return new SimPosition(settlement.position().x()+Math.cos(angle)*radius,settlement.position().z()+Math.sin(angle)*radius);
    }
    private static CitizenRoutine builder(SimulationState state,Faction faction,Settlement settlement,int slot){
        List<ConstructionIntent> pending=new ArrayList<>(SettlementPlanner.pending(faction,settlement));pending.addAll(PrimaryEconomyPlanner.pending(state,faction,settlement));pending.sort(Comparator.comparingInt(ConstructionIntent::priority).reversed().thenComparing(ConstructionIntent::key));
        ConstructionIntent target=pending.isEmpty()?null:pending.get(Math.floorMod(slot,pending.size()));return at(target,CitizenActivity.BUILD,settlement,.92);
    }
    private static ConstructionIntent select(List<ConstructionIntent> intents,int slot,StructureRole role){List<ConstructionIntent> matches=intents.stream().filter(i->i.role()==role).toList();return matches.isEmpty()?null:matches.get(Math.floorMod(slot,matches.size()));}
    private static ConstructionIntent selectAny(List<ConstructionIntent> intents,int slot,List<StructureRole> roles){List<ConstructionIntent> matches=intents.stream().filter(i->roles.contains(i.role())).toList();return matches.isEmpty()?null:matches.get(Math.floorMod(slot,matches.size()));}
    private static CitizenRoutine at(ConstructionIntent intent,CitizenActivity activity,Settlement fallback,double speed){return new CitizenRoutine(intent==null?CitizenActivity.IDLE:activity,intent==null?fallback.position():intent.center(),speed,intent==null?8:3.5);}
}
