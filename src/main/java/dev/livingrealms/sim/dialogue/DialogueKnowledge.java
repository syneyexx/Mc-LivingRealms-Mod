package dev.livingrealms.sim.dialogue;

import java.util.Objects;

/** Canonical knowledge payload. Never invents facts. */
public record DialogueKnowledge(
        boolean known, KnowledgeDepth depth, String acknowledgement, String source,
        String fact, String opinion, String consequence, String limitReason) {
    public enum KnowledgeDepth { FULL, LIMITED, UNKNOWN }
    public DialogueKnowledge {
        depth = Objects.requireNonNullElse(depth, KnowledgeDepth.UNKNOWN);
        acknowledgement = Objects.requireNonNullElse(acknowledgement, "");
        source = Objects.requireNonNullElse(source, "");
        fact = Objects.requireNonNullElse(fact, "");
        opinion = Objects.requireNonNullElse(opinion, "");
        consequence = Objects.requireNonNullElse(consequence, "");
        limitReason = Objects.requireNonNullElse(limitReason, "");
    }
    public static DialogueKnowledge unknown(String limitReason) {
        return new DialogueKnowledge(false, KnowledgeDepth.UNKNOWN, "", "", "", "", "",
                limitReason == null || limitReason.isBlank() ? "I don't know enough about that" : limitReason);
    }
    public static DialogueKnowledge grounded(String fact) {
        return new DialogueKnowledge(true, KnowledgeDepth.FULL, "", "", fact, "", "", "");
    }
    public static DialogueKnowledge limited(String fact, String limitReason) {
        return new DialogueKnowledge(true, KnowledgeDepth.LIMITED, "", "", fact, "", "", limitReason);
    }
    public DialogueKnowledge withOpinion(String value) {
        return new DialogueKnowledge(known, depth, acknowledgement, source, fact, value, consequence, limitReason);
    }
    public DialogueKnowledge withConsequence(String value) {
        return new DialogueKnowledge(known, depth, acknowledgement, source, fact, opinion, value, limitReason);
    }
}
