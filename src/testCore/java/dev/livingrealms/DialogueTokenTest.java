package dev.livingrealms;

import dev.livingrealms.sim.dialogue.*;

/** Token/phrase dialogue parsing: longest phrase wins and disambiguation regressions. */
public final class DialogueTokenTest {
    private DialogueTokenTest() {}

    public static void main(String[] args) {
        NaturalLanguageDialogueEngine engine = new NaturalLanguageDialogueEngine();
        DialogueContext context = new DialogueContext();

        check(engine.parse("who is the king?", context).intent() == DialogueIntent.ASK_RULER, "who is the king");
        check(engine.parse("welk koninkrijk is dit", context).intent() == DialogueIntent.ASK_FACTION, "welk koninkrijk before ruler");
        check(engine.parse("who is our trade partner", context).intent() == DialogueIntent.ASK_TRADE, "trade partner not family");
        check(engine.parse("tell me about the royal court", context).intent() == DialogueIntent.ASK_DYNASTY, "royal court not law");
        check(engine.parse("what is the crime rate here", context).intent() == DialogueIntent.ASK_CRIME, "crime rate");
        check(engine.parse("i heard that bandits took the north road", context).intent() == DialogueIntent.PROVIDE_INFORMATION,
                "heard that bandits is provide information");

        context.update(DialogueIntent.PROVIDE_INFORMATION, DialogueTopic.RUMOR, "bandits", 1);
        check(engine.parse("where did they go", context).intent() == DialogueIntent.ASK_DIRECTION, "follow-up direction");

        System.out.println("PASS dialogue token parsing: longest phrase + disambiguation");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
