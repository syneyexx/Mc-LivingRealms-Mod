package dev.livingrealms.sim.dialogue;

import dev.livingrealms.sim.social.CitizenPersonality;
import dev.livingrealms.sim.social.SocialCitizen;
import java.util.Objects;

/** Layer 4 — personality / profession tone. Never rewrites grounded facts. */
public final class DialogueStyleProfile {
    public DialogueResponsePlan apply(DialogueResponsePlan plan, SocialCitizen citizen, long day) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(citizen, "citizen");
        CitizenPersonality p = citizen.personality();
        String acknowledgement = plan.acknowledgement();
        String tone = tonePrefix(p);
        if (!tone.isBlank()) acknowledgement = tone + stripLeadingTone(acknowledgement);
        String opinion = plan.opinion();
        if (!opinion.isBlank()) opinion = personalityColor(opinion, p);
        else if (p.caution() > 0.72) opinion = "I speak carefully about such things.";
        else if (p.aggression() > 0.72) opinion = "I won't soften it.";
        return new DialogueResponsePlan(acknowledgement, plan.source(), plan.fact(), opinion, plan.consequence());
    }

    private static String tonePrefix(CitizenPersonality p) {
        if (p.caution() > 0.7) return "Carefully, ";
        if (p.aggression() > 0.7) return "Bluntly, ";
        if (p.loyalty() > 0.75) return "As a loyal voice of this place, ";
        if (p.tradeAffinity() > 0.7) return "Practically speaking, ";
        if (p.treachery() > 0.65) return "Between us, ";
        return "";
    }

    private static String personalityColor(String opinion, CitizenPersonality p) {
        if (p.caution() > 0.7 && !opinion.toLowerCase().contains("careful")) {
            return opinion + " I would not swear beyond what I have seen.";
        }
        if (p.aggression() > 0.7 && !opinion.toLowerCase().contains("blunt")) {
            return opinion + " Make of that what you will.";
        }
        return opinion;
    }

    private static String stripLeadingTone(String text) {
        String t = text == null ? "" : text.trim();
        if (t.regionMatches(true, 0, "Carefully,", 0, 10)) return t.substring(10).trim();
        if (t.regionMatches(true, 0, "Bluntly,", 0, 8)) return t.substring(8).trim();
        return t;
    }
}
