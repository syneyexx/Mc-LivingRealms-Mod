package dev.livingrealms.sim.dialogue;

import dev.livingrealms.sim.civilian.CitizenRole;
import dev.livingrealms.sim.social.SocialCitizen;
import java.util.Objects;

/** Layer 3 — semantic response plan from grounded knowledge. Profession changes framing only. */
public final class DialoguePlanner {
    public DialogueResponsePlan plan(DialogueIntent intent, DialogueKnowledge knowledge, SocialCitizen citizen, long day) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(citizen, "citizen");
        if (!knowledge.known() || knowledge.depth() == DialogueKnowledge.KnowledgeDepth.UNKNOWN) {
            String limit = knowledge.limitReason().isBlank() ? "I don't know enough about that" : knowledge.limitReason();
            return new DialogueResponsePlan(acknowledgementFor(citizen), "", limit, "", "");
        }
        String fact = knowledge.fact();
        if (knowledge.depth() == DialogueKnowledge.KnowledgeDepth.LIMITED && fact.isBlank()) fact = knowledge.limitReason();
        String opinion = knowledge.opinion();
        if (opinion.isBlank()) opinion = professionOpinion(intent, citizen.role(), knowledge.depth());
        String consequence = knowledge.consequence();
        if (consequence.isBlank() && knowledge.depth() == DialogueKnowledge.KnowledgeDepth.FULL
                && (citizen.role() == CitizenRole.OFFICIAL || citizen.role() == CitizenRole.GUARD
                || citizen.role() == CitizenRole.SCHOLAR || citizen.role() == CitizenRole.TRADER)) {
            consequence = professionConsequence(intent, citizen.role());
        }
        return new DialogueResponsePlan(acknowledgementFor(citizen), knowledge.source(), fact, opinion, consequence);
    }

    private static String acknowledgementFor(SocialCitizen citizen) {
        return switch (citizen.role()) {
            case OFFICIAL, GUARD -> "From where I stand,";
            case SCHOLAR, TEACHER, PRIEST -> "As I understand it,";
            case TRADER, SAILOR, DOCKWORKER -> "As people on the roads tell it,";
            case FARMER, MINER, LUMBERJACK, FISHER, HUNTER, BUTCHER -> "From what reaches the fields,";
            default -> "From what I know,";
        };
    }

    private static String professionOpinion(DialogueIntent intent, CitizenRole role, DialogueKnowledge.KnowledgeDepth depth) {
        if (depth == DialogueKnowledge.KnowledgeDepth.LIMITED) {
            return switch (role) {
                case FARMER, MINER, LUMBERJACK, FISHER, HUNTER, BUTCHER, BUILDER, CARPENTER, ARTISAN ->
                        "Ask an official if you want the precise figures.";
                case TRADER -> "A clerk would have the ledger detail.";
                default -> "";
            };
        }
        return switch (intent) {
            case ASK_ARMY -> role == CitizenRole.OFFICIAL || role == CitizenRole.GUARD
                    ? "That strength is what the realm can currently field." : "";
            case ASK_POLITICS -> role == CitizenRole.OFFICIAL || role == CitizenRole.SCHOLAR
                    ? "Court bargains matter as much as proclamations." : "";
            case ASK_FOOD -> role == CitizenRole.FARMER ? "The fields decide more than speeches."
                    : role == CitizenRole.TRADER ? "Prices follow the granary." : "";
            case ASK_TECHNOLOGY -> role == CitizenRole.SCHOLAR || role == CitizenRole.ARTISAN
                    ? "Schools and workshops carry the methods." : "";
            default -> "";
        };
    }

    private static String professionConsequence(DialogueIntent intent, CitizenRole role) {
        return switch (intent) {
            case ASK_ARMY -> role == CitizenRole.OFFICIAL || role == CitizenRole.GUARD
                    ? "Poor morale will show on the roads first." : "";
            case ASK_FOOD -> role == CitizenRole.TRADER || role == CitizenRole.OFFICIAL
                    ? "Short stores push market prices up." : "";
            case ASK_WAR -> "War exhaustion drains farms and trade alike.";
            default -> "";
        };
    }
}
