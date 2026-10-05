package dev.livingrealms;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.civilization.*;
import dev.livingrealms.sim.dialogue.*;
import dev.livingrealms.sim.faction.*;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.validation.SimulationValidator;
import dev.livingrealms.sim.world.*;

/** Regression gate for the schema-12 civilization layer and broad no-LLM conversation domains. */
public final class CivilizationLayerTest {
    private CivilizationLayerTest(){}
    public static void main(String[] args){
        SimulationState state=new SimulationState(0xC1A11A710L);
        Faction a=new Faction(state.nextId(),"Aurelian Reach","Queen Mira");
        Settlement capital=new Settlement(state.nextId(),"Sunmere",new SimPosition(0,0),620,760);
        capital.markConstructionCompleted("well:0");capital.markConstructionCompleted("clinic:0");capital.markConstructionCompleted("school:0");capital.markConstructionCompleted("temple:0");capital.markConstructionCompleted("farm:0");capital.markConstructionCompleted("mine:0");capital.markConstructionCompleted("market:0");capital.markConstructionCompleted("monument:0");
        a.addSettlement(capital);Settlement border=new Settlement(state.nextId(),"Oakwatch",new SimPosition(520,40),180,250);border.markConstructionCompleted("well:0");border.markConstructionCompleted("lumber_camp:0");a.addSettlement(border);
        a.stockpile().add(ResourceType.FOOD,900);a.stockpile().add(ResourceType.IRON,120);a.stockpile().add(ResourceType.WOOD,700);a.stockpile().add(ResourceType.TOOLS,120);a.addTreasury(2000);Army army=new Army(state.nextId(),a.id(),new SimPosition(120,0),100);a.addArmy(army);state.addFaction(a);

        Faction b=new Faction(state.nextId(),"Brass March","Lord Corvin");Settlement rival=new Settlement(state.nextId(),"Redford",new SimPosition(900,20),360,430);rival.markConstructionCompleted("well:0");rival.markConstructionCompleted("farm:0");b.addSettlement(rival);b.stockpile().add(ResourceType.FOOD,500);b.stockpile().add(ResourceType.IRON,90);Army ba=new Army(state.nextId(),b.id(),new SimPosition(850,30),80);b.addArmy(ba);state.addFaction(b);
        a.relationWith(b.id()).adjust(-72);b.relationWith(a.id()).adjust(-72);

        SocialCitizen official=state.ensureSocialCitizen(a.id(),capital.id(),0,CitizenRole.OFFICIAL);state.ensureSocialCitizen(a.id(),capital.id(),1,CitizenRole.SCHOLAR);state.ensureSocialCitizen(a.id(),capital.id(),2,CitizenRole.HEALER);
        state.history().add(new WorldEvent(0,"battle","faction="+a.id()+", settlement="+capital.id()+", defenders held the eastern gate"));
        state.ensureSettlementCivilization(border.id(),a.id()).adjustBanditPressure(.9);
        state.advanceDays(1);

        check(state.settlementCivilizations().size()==3,"settlement civilization profiles");
        check(state.factionCivilizations().size()==2,"faction civilization profiles");
        check(state.resourceClaims().stream().anyMatch(c->c.settlementId()==capital.id()&&c.type()==ResourceClaimType.MINE),"mine claim");
        check(state.resourceClaims().stream().anyMatch(c->c.settlementId()==capital.id()&&c.type()==ResourceClaimType.WATER),"water claim");
        check(state.raids().stream().anyMatch(r->r.bandit()&&r.originSettlementId()==border.id()),"state-backed bandit raid");
        check(state.legends().stream().anyMatch(l->l.factionId()==a.id()),"history promoted to legend");
        check(state.legends().stream().filter(l->l.settlementId()==capital.id()).allMatch(LegendRecord::monumented),"existing monument memorializes local legend");

        SettlementCivilizationState civ=state.settlementCivilizations().get(capital.id());
        check(civ.waterSecurity()>.55&&civ.education()>.20,"civic infrastructure affects civilization metrics");
        check(state.factionCivilizations().get(a.id()).cultureName()!=null,"culture identity");

        NaturalLanguageDialogueEngine engine=new NaturalLanguageDialogueEngine();DialogueContext context=new DialogueContext();String player="player:civilization-test";
        check(engine.respond(state,official,player,"Wat doe je voor werk?",context).intent()==DialogueIntent.ASK_JOB,"job dialogue");
        check(engine.respond(state,official,player,"Hoe hoog zijn de belastingen?",context).intent()==DialogueIntent.ASK_TAX,"tax dialogue");
        check(engine.respond(state,official,player,"Wat is jullie cultuur en dialect?",context).intent()==DialogueIntent.ASK_CULTURE,"culture dialogue");
        check(engine.respond(state,official,player,"Zijn er vluchtelingen of migratie?",context).intent()==DialogueIntent.ASK_MIGRATION,"migration dialogue");
        DialogueResult history=engine.respond(state,official,player,"Vertel me over de geschiedenis en legendes",context);check(history.intent()==DialogueIntent.ASK_HISTORY&&history.response().contains("Battle"),"history/legend dialogue");
        check(engine.respond(state,official,player,"Welke wetten gelden hier?",context).intent()==DialogueIntent.ASK_LAW,"law dialogue");
        check(engine.respond(state,official,player,"Wat voor grondstoffen hebben jullie?",context).intent()==DialogueIntent.ASK_RESOURCES,"resource dialogue");

        state.advanceDays(35);SimulationValidator.validate(state).throwIfInvalid();
        byte[] encoded=SimulationStateCodec.encode(state);SimulationState restored=SimulationStateCodec.decode(encoded,state.species());
        check(SimulationStateCodec.SCHEMA_VERSION==18,"schema"+SimulationStateCodec.SCHEMA_VERSION);
        check(restored.settlementCivilizations().size()==state.settlementCivilizations().size(),"civilization persistence");
        check(restored.resourceClaims().size()==state.resourceClaims().size(),"claim persistence");
        check(restored.legends().size()==state.legends().size(),"legend persistence");
        check(restored.factionCivilizations().get(a.id()).cultureName().equals(state.factionCivilizations().get(a.id()).cultureName()),"culture persistence");
        SimulationValidator.validate(restored).throwIfInvalid();
        System.out.println("PASS civilization layer: demography/health/culture/claims/raids/legends + schema18 persistence + broad grounded no-LLM dialogue");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
