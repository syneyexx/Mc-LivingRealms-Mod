package dev.livingrealms;

import dev.livingrealms.sim.civilian.*;
import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.construction.*;
import dev.livingrealms.sim.dialogue.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.validation.SimulationValidator;
import dev.livingrealms.sim.world.*;

/** Regression gate for persistent humanity lifecycle features that must be state-backed rather than scripted. */
public final class HumanityLifecycleTest {
    private HumanityLifecycleTest(){}
    public static void main(String[] args){
        SimulationState state=new SimulationState(0x484D414E495459L);
        Faction realm=new Faction(state.nextId(),"River Crown","Queen Alys");
        Settlement town=new Settlement(state.nextId(),"Lowwater",new SimPosition(0,0),260,230);
        town.setFoodSecurity(.72);town.setPublicOrder(.01);town.markConstructionCompleted("market:0");town.markConstructionCompleted("well:0");town.markConstructionCompleted("tavern:0");realm.addSettlement(town);town.markConstructionCompleted(SettlementPlanner.plan(realm,town).stream().filter(i->i.role()==StructureRole.ROAD).findFirst().orElseThrow().key());realm.stockpile().add(ResourceType.FOOD,60);realm.stockpile().add(ResourceType.WOOD,180);realm.addTreasury(900);realm.government().setLawEnforcement(0);state.addFaction(realm);
        SocialCitizen citizen=state.ensureSocialCitizen(realm.id(),town.id(),0,CitizenRole.TRADER);

        SettlementCivilizationState civ=state.ensureSettlementCivilization(town.id(),realm.id());
        civ.adjustBanditPressure(.95);
        AssistanceTask security=null;
        for(int i=0;i<8&&security==null;i++){
            state.advanceDays(1);
            security=state.assistanceTasks().stream().filter(AssistanceTask::active).filter(t->t.settlementId()==town.id()&&t.type()==AssistanceTaskType.SECURITY_SUPPORT).findFirst().orElse(null);
        }
        if(security==null)throw new AssertionError("security pressure must create a state-backed assistance task");
        long taskId=security.id();
        DialogueResult help=new NaturalLanguageDialogueEngine().respond(state,citizen,"player:helper","Kan ik helpen?",new DialogueContext());
        check(help.actions().stream().anyMatch(a->a.type()==DialogueActionType.OFFER_TASK&&a.subject().equals("task:"+taskId)),"dialogue must offer canonical task id");

        realm.government().setLawEnforcement(1);town.setPublicOrder(.98);civ.adjustBanditPressure(-1.0);state.advanceDays(1);
        check(security.status()==AssistanceTaskStatus.RESOLVED,"task must resolve when canonical security pressure resolves");

        check(CivilizationCalendar.season(0)==CivilizationCalendar.Season.SPRING,"calendar spring");
        check(CivilizationCalendar.season(95)==CivilizationCalendar.Season.SUMMER,"calendar summer");
        check(CivilizationCalendar.year(360)==2,"calendar year rollover");
        CitizenRoutine night=CitizenRoutinePlanner.plan(state,realm,town,CitizenRole.GUARD,3,18000);
        check(night.activity()==CitizenActivity.PATROL,"guards must patrol at night");
        CitizenRoutine sleeper=CitizenRoutinePlanner.plan(state,realm,town,CitizenRole.FARMER,4,18000);
        check(sleeper.activity()==CitizenActivity.REST||sleeper.activity()==CitizenActivity.IDLE,"ordinary workers must rest at night");

        CivicEvent fair=new CivicEvent(state.nextId(),realm.id(),town.id(),state.clock().day(),state.clock().day()+1,CivicEventType.MARKET_FAIR,"Lowwater Market Fair",1);fair.setAttendance(1);state.addCivicEvent(fair);
        CitizenRoutine attendee=CitizenRoutinePlanner.plan(state,realm,town,CitizenRole.TRADER,0,6000);
        check(attendee.activity()==CitizenActivity.TRADE,"active market fair must pull eligible citizens into the real market routine");
        DialogueResult today=new NaturalLanguageDialogueEngine().respond(state,citizen,"player:helper","Wat gebeurt er vandaag?",new DialogueContext());
        check(today.response().contains("Lowwater Market Fair"),"active civic event must be visible through grounded NPC dialogue");

        SimulationValidator.validate(state).throwIfInvalid();
        byte[] encoded=SimulationStateCodec.encode(state);SimulationState restored=SimulationStateCodec.decode(encoded,state.species());
        AssistanceTask restoredTask=restored.assistanceTasks().stream().filter(t->t.id()==taskId).findFirst().orElseThrow();
        check(restoredTask.status()==AssistanceTaskStatus.RESOLVED,"assistance task lifecycle must persist");
        SimulationValidator.validate(restored).throwIfInvalid();
        System.out.println("PASS humanity lifecycle: state-backed assistance + calendar/night/civic routines + grounded event dialogue + schema15 persistence");
    }
    private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
