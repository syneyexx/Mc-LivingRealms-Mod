package dev.livingrealms.sim.dialogue;

import dev.livingrealms.sim.content.CultureDefinition;
import dev.livingrealms.sim.content.SettlementIdentityProfile;
import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.social.CitizenPersonality;
import dev.livingrealms.sim.social.SocialCitizen;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Layer 4 — personality / profession / culture tone. Never rewrites grounded facts. */
public final class DialogueStyleProfile {
    public DialogueResponsePlan apply(DialogueResponsePlan plan, SocialCitizen citizen, long day) {
        return apply(plan, citizen, day, null, null);
    }

    /** Wave 30 — culture dialect colors acknowledgement when an authored pack is present. */
    public DialogueResponsePlan apply(
            DialogueResponsePlan plan,
            SocialCitizen citizen,
            long day,
            Faction faction,
            CultureDefinition culture
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(citizen, "citizen");
        CitizenPersonality p = citizen.personality();
        String acknowledgement = plan.acknowledgement();
        String tone = tonePrefix(p, culture);
        if (!tone.isBlank()) acknowledgement = tone + stripLeadingTone(acknowledgement);
        String opinion = plan.opinion();
        if (!opinion.isBlank()) opinion = personalityColor(opinion, p, culture);
        else if (p.caution() > 0.72) opinion = "I speak carefully about such things.";
        else if (p.aggression() > 0.72) opinion = "I won't soften it.";
        else if (culture != null && culture.artisticTendency() > 0.7) {
            opinion = "There is a particular grace to how we say these things in " + culture.displayName() + ".";
        }
        return new DialogueResponsePlan(acknowledgement, plan.source(), plan.fact(), opinion, plan.consequence());
    }

    public static Optional<CultureDefinition> cultureOf(Faction faction) {
        if (faction == null) return Optional.empty();
        return SettlementIdentityProfile.cultureOf(faction);
    }

    private static String tonePrefix(CitizenPersonality p, CultureDefinition culture) {
        if (culture != null) {
            String dialect = culture.dialectStyle() == null ? "" : culture.dialectStyle().trim().toLowerCase(Locale.ROOT);
            if (!dialect.isBlank() && p.loyalty() > 0.55) {
                return "In the " + dialect + ", ";
            }
            if (culture.martialTendency() > 0.72 && p.aggression() > 0.45) return "Plainly, as our people do, ";
            if (culture.economicTendency() > 0.72 && p.tradeAffinity() > 0.45) return "As any market-minded voice would say, ";
        }
        if (p.caution() > 0.7) return "Carefully, ";
        if (p.aggression() > 0.7) return "Bluntly, ";
        if (p.loyalty() > 0.75) return "As a loyal voice of this place, ";
        if (p.tradeAffinity() > 0.7) return "Practically speaking, ";
        if (p.treachery() > 0.65) return "Between us, ";
        return "";
    }

    private static String personalityColor(String opinion, CitizenPersonality p, CultureDefinition culture) {
        if (culture != null && culture.artisticTendency() > 0.65
                && !opinion.toLowerCase(Locale.ROOT).contains("grace")) {
            opinion = opinion + " We phrase it with a touch of " + culture.displayName() + " colour.";
        }
        if (p.caution() > 0.7 && !opinion.toLowerCase(Locale.ROOT).contains("careful")) {
            return opinion + " I would not swear beyond what I have seen.";
        }
        if (p.aggression() > 0.7 && !opinion.toLowerCase(Locale.ROOT).contains("blunt")) {
            return opinion + " Make of that what you will.";
        }
        return opinion;
    }

    private static String stripLeadingTone(String text) {
        String t = text == null ? "" : text.trim();
        if (t.regionMatches(true, 0, "Carefully,", 0, 10)) return t.substring(10).trim();
        if (t.regionMatches(true, 0, "Bluntly,", 0, 8)) return t.substring(8).trim();
        if (t.regionMatches(true, 0, "In the ", 0, 7)) {
            int comma = t.indexOf(',');
            if (comma > 0 && comma < 48) return t.substring(comma + 1).trim();
        }
        return t;
    }
}
