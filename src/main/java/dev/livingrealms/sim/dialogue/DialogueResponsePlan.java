package dev.livingrealms.sim.dialogue;

import java.util.Objects;

public record DialogueResponsePlan(String acknowledgement, String source, String fact, String opinion, String consequence) {
    public DialogueResponsePlan {
        acknowledgement = Objects.requireNonNullElse(acknowledgement, "");
        source = Objects.requireNonNullElse(source, "");
        fact = Objects.requireNonNullElse(fact, "");
        opinion = Objects.requireNonNullElse(opinion, "");
        consequence = Objects.requireNonNullElse(consequence, "");
    }
}
