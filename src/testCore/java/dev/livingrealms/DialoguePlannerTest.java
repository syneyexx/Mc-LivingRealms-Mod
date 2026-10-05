package dev.livingrealms;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.dialogue.*;
import dev.livingrealms.sim.faction.Army;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.social.CitizenPersonality;
import dev.livingrealms.sim.social.SocialCitizen;
import dev.livingrealms.sim.world.SimPosition;
import dev.livingrealms.sim.world.SimulationState;

public final class DialoguePlannerTest {
    private DialoguePlannerTest() {}
    public static void main(String[] args) {
        sameFactDifferentProfessionFraming();
        personalityToneChange();
        unknownCitizenLimits();
        System.out.println("PASS dialogue planner: profession framing, personality tone, unknown limits");
    }
    private static void sameFactDifferentProfessionFraming() {
        SimulationState state = new SimulationState(0xD1A106L);
        Faction faction = new Faction(1, "Test Realm", "Ruler");
        Settlement settlement = new Settlement(10, "Testville", new SimPosition(0, 0), 400, 400);
        faction.addSettlement(settlement);
        faction.addArmy(new Army(1, faction.id(), settlement.position(), 120));
        state.addFaction(faction);
        SocialCitizen farmer = citizen(2, faction.id(), settlement.id(), "Ada", CitizenRole.FARMER, personality(.2, .3, .4, .3, .5, .1));
        SocialCitizen official = citizen(3, faction.id(), settlement.id(), "Boris", CitizenRole.OFFICIAL, personality(.2, .3, .4, .3, .5, .1));
        state.addSocialCitizen(farmer); state.addSocialCitizen(official);
        DialogueKnowledgeService knowledge = new DialogueKnowledgeService();
        DialoguePlanner planner = new DialoguePlanner();
        DialogueRealizer realizer = new DialogueRealizer();
        long day = state.clock().day();
        DialogueKnowledge farmerK = knowledge.army(state, farmer, faction, day);
        DialogueKnowledge officialK = knowledge.army(state, official, faction, day);
        check(farmerK.depth() == DialogueKnowledge.KnowledgeDepth.LIMITED, "farmer limited");
        check(officialK.depth() == DialogueKnowledge.KnowledgeDepth.FULL, "official full");
        check(!farmerK.fact().equals(officialK.fact()), "facts differ");
        String farmerText = realizer.realize(planner.plan(DialogueIntent.ASK_ARMY, farmerK, farmer, day));
        String officialText = realizer.realize(planner.plan(DialogueIntent.ASK_ARMY, officialK, official, day));
        check(!farmerText.equals(officialText), "framing differs");
        check(officialText.toLowerCase().contains("personnel") || officialText.contains("120"), "official fact");
        check(!farmerText.contains("120"), "farmer no invent");
    }
    private static void personalityToneChange() {
        DialoguePlanner planner = new DialoguePlanner();
        DialogueStyleProfile style = new DialogueStyleProfile();
        DialogueRealizer realizer = new DialogueRealizer();
        DialogueKnowledge fact = DialogueKnowledge.grounded("Public order is moderate.");
        SocialCitizen cautious = citizen(11, 1, 10, "Cara", CitizenRole.ARTISAN, personality(.1, .4, .9, .2, .6, .1));
        SocialCitizen blunt = citizen(12, 1, 10, "Dirk", CitizenRole.ARTISAN, personality(.9, .4, .1, .2, .6, .1));
        String cautiousText = realizer.realize(style.apply(planner.plan(DialogueIntent.ASK_CRIME, fact, cautious, 1), cautious, 1));
        String bluntText = realizer.realize(style.apply(planner.plan(DialogueIntent.ASK_CRIME, fact, blunt, 1), blunt, 1));
        check(cautiousText.toLowerCase().contains("careful"), "cautious");
        check(bluntText.toLowerCase().contains("blunt"), "blunt");
        check(cautiousText.contains("Public order is moderate") && bluntText.contains("Public order is moderate"), "fact preserved");
        check(!cautiousText.equals(bluntText), "tone differs");
    }
    private static void unknownCitizenLimits() {
        DialoguePlanner planner = new DialoguePlanner();
        DialogueRealizer realizer = new DialogueRealizer();
        SocialCitizen farmer = citizen(21, 1, 10, "Eve", CitizenRole.FARMER, personality(.3, .3, .3, .3, .3, .1));
        String text = realizer.realize(planner.plan(DialogueIntent.ASK_ARMY, DialogueKnowledge.unknown("I don't know of a standing field army here"), farmer, 1));
        check(text.toLowerCase().contains("don't know"), "unknown limit");
        check(!text.toLowerCase().contains("personnel"), "no invent");
    }
    private static SocialCitizen citizen(long id, long factionId, long settlementId, String name, CitizenRole role, CitizenPersonality personality) {
        return new SocialCitizen(id, factionId, settlementId, 0, name, 0, role, 0, personality);
    }
    private static CitizenPersonality personality(double a, double t, double c, double g, double l, double tr) {
        return new CitizenPersonality(a, t, c, g, l, tr);
    }
    private static void check(boolean cond, String message) { if (!cond) throw new AssertionError(message); }
}
