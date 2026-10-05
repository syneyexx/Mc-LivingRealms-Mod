package dev.livingrealms.sim.dialogue;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Layer 1 — normalize free text, extract intent / topic / subject entities. */
public final class DialogueInterpreter {
    public record Interpreted(DialogueIntent intent, DialogueTopic topic, String subject, String normalized, Set<String> tokens) {
        public Interpreted {
            intent = Objects.requireNonNull(intent, "intent");
            topic = Objects.requireNonNull(topic, "topic");
            subject = Objects.requireNonNullElse(subject, "");
            normalized = Objects.requireNonNullElse(normalized, "");
            tokens = tokens == null ? Set.of() : Set.copyOf(tokens);
        }
    }

    public Interpreted interpret(String raw, DialogueContext context) {
        Objects.requireNonNull(context, "context");
        String s = normalize(raw);
        Set<String> tokens = new LinkedHashSet<>(s.isBlank() ? Set.of() : Arrays.asList(s.split(" ")));
        if (DialogueLexicalGroups.containsAny(s, DialogueLexicalGroups.SOURCE_ASK) || s.equals("source")) {
            return new Interpreted(DialogueIntent.ASK_SOURCE, context.lastTopic(), context.lastSubject(), s, tokens);
        }
        if (DialogueLexicalGroups.containsAny(s, DialogueLexicalGroups.DIRECTION_FOLLOWUP)) {
            DialogueTopic topic = context.lastTopic() == DialogueTopic.NONE ? DialogueTopic.LOCATION : context.lastTopic();
            return new Interpreted(DialogueIntent.ASK_DIRECTION, topic, context.lastSubject(), s, tokens);
        }
        String where = afterAny(s, DialogueLexicalGroups.WHERE_PREFIXES);
        if (!where.isBlank()) return new Interpreted(DialogueIntent.ASK_DIRECTION, DialogueTopic.LOCATION, where, s, tokens);
        DialoguePhraseTable.Match match = DialoguePhraseTable.longest(s);
        if (match != null) {
            String subject = match.dynamicResource() ? subjectOr(DialogueLexicalGroups.resourceSubject(s), "resource") : match.subject();
            return new Interpreted(match.intent(), match.topic(), subject, s, tokens);
        }
        if (tokens.size() <= 2 && DialogueLexicalGroups.tokenMatch(tokens, DialogueLexicalGroups.GREETINGS)) {
            return new Interpreted(DialogueIntent.GREET, DialogueTopic.NONE, "", s, tokens);
        }
        if (DialogueLexicalGroups.tokenMatch(tokens, DialogueLexicalGroups.GOODBYES)) {
            return new Interpreted(DialogueIntent.GOODBYE, DialogueTopic.NONE, "", s, tokens);
        }
        return new Interpreted(DialogueIntent.UNKNOWN, DialogueTopic.NONE, "", s, tokens);
    }

    public static String normalize(String input) {
        return Normalizer.normalize(Objects.requireNonNullElse(input, "").toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String afterAny(String s, java.util.List<String> prefixes) {
        for (String p : prefixes) {
            int i = s.indexOf(p);
            if (i >= 0) {
                String x = s.substring(i + p.length()).trim();
                if (!x.isBlank()) return x;
            }
        }
        return "";
    }

    private static String subjectOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
