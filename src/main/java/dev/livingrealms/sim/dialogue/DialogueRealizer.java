package dev.livingrealms.sim.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Layer 5 — surface strings from plan slots. */
public final class DialogueRealizer {
    public String realize(DialogueResponsePlan plan) {
        Objects.requireNonNull(plan, "plan");
        List<String> parts = new ArrayList<>();
        if (!plan.acknowledgement().isBlank()) parts.add(trimPunct(plan.acknowledgement()));
        if (!plan.source().isBlank()) parts.add(trimPunct(plan.source()));
        if (!plan.fact().isBlank()) parts.add(trimPunct(plan.fact()));
        if (!plan.opinion().isBlank()) parts.add(trimPunct(plan.opinion()));
        if (!plan.consequence().isBlank()) parts.add(trimPunct(plan.consequence()));
        if (parts.isEmpty()) return "I don't know enough about that.";
        String joined = String.join(" ", parts);
        if (!joined.endsWith(".") && !joined.endsWith("?") && !joined.endsWith("!")) joined = joined + ".";
        return joined.replaceAll("\s+", " ").replace(" ,", ",").trim();
    }

    private static String trimPunct(String s) {
        String t = s.trim();
        while (t.endsWith(".") || t.endsWith(",")) t = t.substring(0, t.length() - 1).trim();
        return t;
    }
}
