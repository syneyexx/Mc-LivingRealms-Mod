package dev.livingrealms;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.compat.ModCompatibilityPolicy;
import dev.livingrealms.sim.dialogue.*;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.social.*;
import dev.livingrealms.sim.world.*;

/** Regression gate for persistent named people and the no-LLM natural-language dialogue core. */
public final class SocietyDialogueTest {
    private SocietyDialogueTest() {}
    public static void main(String[] args){
        SimulationState state=new SimulationState(0x51C1E7L);DemoSeeder.seed(state);
        var faction=state.factions().getFirst();var settlement=faction.settlements().getFirst();
        SocialCitizen citizen=state.ensureSocialCitizen(faction.id(),settlement.id(),0,CitizenRole.GUARD);
        check(citizen==state.ensureSocialCitizen(faction.id(),settlement.id(),0,CitizenRole.GUARD),"projection citizen must be stable");
        check(citizen.name()!=null&&!citizen.name().isBlank()&&citizen.skinVariant()>=0&&citizen.skinVariant()<12,"identity");
        check(citizen.personality().loyalty()>=0&&citizen.personality().loyalty()<=1,"personality");
        citizen.remember(new CitizenMemory(state.clock().day(),MemoryType.RUMOR,"bandits","merchant:Tess","Bandits were seen near the old north road.",new SimPosition(settlement.position().x()+50,settlement.position().z()-80),.8,.72));

        NaturalLanguageDialogueEngine engine=new NaturalLanguageDialogueEngine();DialogueContext context=new DialogueContext();String player="player:test";
        DialogueResult danger=engine.respond(state,citizen,player,"Heb je hier bandieten gezien?",context);
        check(danger.intent()==DialogueIntent.ASK_DANGER&&danger.topic()==DialogueTopic.BANDITS,"bandit intent");
        check(danger.response().toLowerCase().contains("bandit"),"knowledge-grounded bandit response");
        check(danger.actions().stream().anyMatch(a->a.type()==DialogueActionType.MARK_LOCATION),"known danger should mark location");

        DialogueResult follow=engine.respond(state,citizen,player,"Waar gingen ze heen?",context);
        check(follow.intent()==DialogueIntent.ASK_DIRECTION&&follow.response().toLowerCase().contains("north"),"pronoun/context direction");
        DialogueResult source=engine.respond(state,citizen,player,"Wie vertelde je dat?",context);
        check(source.intent()==DialogueIntent.ASK_SOURCE&&source.response().contains("merchant:Tess"),"source attribution");

        double before=state.playerStanding(player).reputationWith(faction.id());
        DialogueResult gift=engine.respond(state,citizen,player,"Ik heb een cadeau voor jou",context);
        check(gift.actions().stream().anyMatch(a->a.type()==DialogueActionType.ACCEPT_GIFT),"gift must request a real runtime transfer");
        double afterGift=state.playerStanding(player).reputationWith(faction.id());check(afterGift==before,"typing gift must not fabricate a gift");
        engine.respond(state,citizen,player,"Ik vermoord je",context);
        double afterThreat=state.playerStanding(player).reputationWith(faction.id());check(afterThreat<afterGift,"threat reputation");
        check(citizen.relationship(player).hostility()>0,"threat relationship memory");

        DialogueResult age=engine.respond(state,citizen,player,"Hoe oud ben je?",context);
        check(age.intent()==DialogueIntent.ASK_AGE&&age.response().contains(Integer.toString(citizen.ageYears(state.clock().day()))),"age intent must be grounded in citizen birth day");
        DialogueResult water=engine.respond(state,citizen,player,"Hoe is het drinkwater hier?",context);
        check(water.intent()==DialogueIntent.ASK_WATER&&water.response().toLowerCase().contains("water"),"water intent");
        DialogueResult calendar=engine.respond(state,citizen,player,"Welk seizoen is het?",context);
        check(calendar.intent()==DialogueIntent.ASK_CALENDAR&&calendar.response().toLowerCase().contains("year"),"calendar intent");
        DialogueResult price=engine.respond(state,citizen,player,"Wat kost ijzer?",context);
        check(price.intent()==DialogueIntent.ASK_PRICE&&price.response().toLowerCase().contains("iron"),"resource-specific price intent");
        state.ensureDynasty(faction.id(),state.clock().day(),"River").setRulerCitizenId(citizen.id());
        DialogueResult dynasty=engine.respond(state,citizen,player,"Welke dynastie regeert hier?",context);
        check(dynasty.intent()==DialogueIntent.ASK_DYNASTY&&dynasty.response().contains("River"),"dynasty must answer from canonical dynasty state");
        DialogueResult guards=engine.respond(state,citizen,player,"Hoe zijn de wachters hier?",context);
        check(guards.intent()==DialogueIntent.ASK_GUARDS,"guard intent");
        DialogueResult wanted=engine.respond(state,citizen,player,"Ben ik gezocht?",context);
        check(wanted.intent()==DialogueIntent.ASK_CRIME&&wanted.response().toLowerCase().contains("not currently looking"),"wanted query must use crime ledger");
        DialogueResult report=engine.respond(state,citizen,player,"Ik zag bandieten",context);
        check(report.intent()==DialogueIntent.REPORT_DANGER&&report.actions().stream().anyMatch(a->a.type()==DialogueActionType.NOTIFY_GUARDS),"danger report must notify guards without marking the reporter hostile");
        check(citizen.latestMemory(m->m.sourceKey().equals(player)&&m.subjectKey().equals("reported-danger")).isPresent(),"reported danger must preserve player as source");

        for(int i=0;i<80;i++)citizen.remember(new CitizenMemory(state.clock().day(),MemoryType.CONVERSATION,"topic:"+i,player,"memory "+i,settlement.position(),.1,.9));
        check(citizen.memories().size()==SocialCitizen.MAX_MEMORIES,"memory bound");

        byte[] encoded=SimulationStateCodec.encode(state);SimulationState restored=SimulationStateCodec.decode(encoded,state.species());
        SocialCitizen rc=restored.findSocialCitizen(citizen.id()).orElseThrow();
        check(rc.name().equals(citizen.name())&&rc.memories().size()==SocialCitizen.MAX_MEMORIES,"social persistence");
        check(rc.relationships().containsKey(player),"relationship persistence");
        check(SimulationStateCodec.SCHEMA_VERSION==16,"schema16");

        ModCompatibilityPolicy.validate();
        check(ModCompatibilityPolicy.isPlayerOnly("mr_guns")&&!ModCompatibilityPolicy.mayUseForLivingWorld("mr_guns"),"Guns++ NPC deny-list");
        check(ModCompatibilityPolicy.isPlayerOnly("gamingbarns_guns")&&!ModCompatibilityPolicy.mayUseForLivingWorld("gamingbarns_guns"),"GamingBarn NPC deny-list");
        System.out.println("PASS living society + no-LLM dialogue foundation: persistent identity/needs/personality/memory/relations + context + grounded knowledge + dual gun deny-list");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
